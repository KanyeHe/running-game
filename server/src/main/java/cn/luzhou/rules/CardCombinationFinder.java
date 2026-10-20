package cn.luzhou.rules;

import static cn.luzhou.rules.Combination.Type.*;

import java.util.*;
import java.util.function.Consumer;

/**
 * Generates rank-count templates, prunes unavailable ranks, then allocates physical cards. No hand
 * power set.
 */
public final class CardCombinationFinder {
  private final WildcardPolicy wild;
  private final BombPolicy bombs;
  private final SequencePolicy sequences;
  private final HintComparator hints;
  private final PlanePolicy planes;

  public CardCombinationFinder(WildcardPolicy w, BombPolicy b, SequencePolicy s, HintComparator h) {
    this(w, b, s, h, PlanePolicy.demo());
  }

  public CardCombinationFinder(
      WildcardPolicy w, BombPolicy b, SequencePolicy s, HintComparator h, PlanePolicy p) {
    wild = w;
    bombs = b;
    sequences = s;
    hints = h;
    planes = p;
  }

  public static CardCombinationFinder demo() {
    return new CardCombinationFinder(
        WildcardPolicy.demo(), BombPolicy.demo(), SequencePolicy.demo(), HintComparator.demo());
  }

  private static boolean isPlane(Combination.Type type) {
    return type == PLANE || type == PLANE_SINGLES || type == PLANE_PAIRS;
  }

  private static void choose(List<Card> list, int count, Consumer<List<Card>> consumer) {
    choose(list, 0, count, new ArrayList<>(), consumer);
  }

  private static void choose(
      List<Card> list, int start, int count, List<Card> chosen, Consumer<List<Card>> consumer) {
    if (count == 0) {
      consumer.accept(chosen);
      return;
    }
    for (int i = start; i <= list.size() - count; i++) {
      chosen.add(list.get(i));
      choose(list, i + 1, count - 1, chosen, consumer);
      chosen.removeLast();
    }
  }

  public BombPolicy bombPolicy() {
    return bombs;
  }

  public boolean beats(Combination a, Combination b) {
    if (b == null) return true;
    if (a.type() == BOMB) return b.type() != BOMB || bombs.beats(a, b);
    if (isPlane(a.type())) return a.type() == b.type() && planes.beats(a, b);
    return b.type() != BOMB
        && a.type() == b.type()
        && a.cardIds().size() == b.cardIds().size()
        && a.groups() == b.groups()
        && a.value() > b.value();
  }

  public List<Combination> find(List<Card> hand, Combination table, Lead lead) {
    if (hand.size() > 13 || hand.stream().map(Card::id).distinct().count() != hand.size())
      throw new IllegalArgumentException("INVALID_HAND");
    Search search = new Search(hand, table, lead);
    for (int r = 1; r <= 13; r++) {
      search.emit(SINGLE, Map.of(r, 1), Card.strength(r), 1, r);
      search.emit(PAIR, Map.of(r, 2), Card.strength(r), 1, r);
      search.emit(TRIPLE, Map.of(r, 3), Card.strength(r), 1, r);
      search.emit(BOMB, Map.of(r, 4), Card.strength(r), 1, r);
      if (search.possible(Map.of(r, 3))) {
        int body = r;
        search.attach(TRIPLE_SINGLES, Map.of(r, 3), 2, 1, Card.strength(r), 1, body);
        search.attach(TRIPLE_PAIR, Map.of(r, 3), 1, 2, Card.strength(r), 1, body);
      }
    }
    List<Integer> order = sequences.order();
    for (int start = 0; start < order.size(); start++)
      for (int n = 2; start + n <= order.size(); n++) {
        Map<Integer, Integer> template = new TreeMap<>();
        for (int i = start; i < start + n; i++) template.put(order.get(i), 1);
        if (n >= 5) search.emit(STRAIGHT, template, start, n, order.get(start));
        template.replaceAll((r, c) -> 2);
        search.emit(PAIR_RUN, template, start, n, order.get(start));
        template.replaceAll((r, c) -> 3);
        if (search.possible(template)) {
          search.emit(PLANE, template, start, n, order.get(start));
          search.attach(PLANE_SINGLES, template, 2 * n, 1, start, n, order.get(start));
          search.attach(PLANE_PAIRS, template, n, 2, start, n, order.get(start));
        }
      }
    return search.results.values().stream().sorted(hints).toList();
  }

  public boolean leadAllows(Combination c, Lead lead) {
    if (lead == Lead.FIRST_SPADE_THREE) return c.cardIds().contains(Card.SPADE_THREE);
    if (lead == Lead.WINNER_FIRST)
      return !(c.type() == SINGLE && (c.bodyRank() == 2 || c.cardIds().getFirst() >= 52))
          && !(c.type() == PAIR && c.bodyRank() == 2);
    return true;
  }

  public enum Lead {
    FREE,
    FIRST_SPADE_THREE,
    WINNER_FIRST
  }

  private record Template(
      Combination.Type type,
      List<Map.Entry<Integer, Integer>> counts,
      int value,
      int groups,
      int bodyRank) {}

  private final class Search {
    final Map<Integer, List<Card>> ranks = new TreeMap<>();
    final List<Card> jokers;
    final List<Card> hand;
    final Combination table;
    final Lead lead;
    final Map<String, Combination> results = new TreeMap<>();

    Search(List<Card> hand, Combination table, Lead lead) {
      this.hand = hand;
      this.table = table;
      this.lead = lead;
      jokers = hand.stream().filter(Card::wild).sorted(Comparator.comparingInt(Card::id)).toList();
      hand.stream()
          .filter(c -> !c.wild())
          .sorted(Comparator.comparingInt(Card::id))
          .forEach(c -> ranks.computeIfAbsent(c.rank(), k -> new ArrayList<>()).add(c));
    }

    boolean possible(Map<Integer, Integer> counts) {
      return counts.values().stream().mapToInt(i -> i).sum() <= hand.size()
          && counts.entrySet().stream()
                  .mapToInt(
                      e ->
                          Math.max(
                              0, e.getValue() - ranks.getOrDefault(e.getKey(), List.of()).size()))
                  .sum()
              <= jokers.size();
    }

    void attach(
        Combination.Type type,
        Map<Integer, Integer> body,
        int count,
        int size,
        int value,
        int groups,
        int bodyRank) {
      if (body.values().stream().mapToInt(i -> i).sum() + count * size > hand.size()) return;
      attachments(
          type,
          new TreeMap<>(body),
          new HashSet<>(body.keySet()),
          1,
          count,
          size,
          value,
          groups,
          bodyRank);
    }

    void attachments(
        Combination.Type type,
        TreeMap<Integer, Integer> counts,
        Set<Integer> body,
        int from,
        int remaining,
        int size,
        int value,
        int groups,
        int bodyRank) {
      if (remaining == 0) {
        emit(type, counts, value, groups, bodyRank);
        return;
      }
      for (int r = from; r <= 13; r++)
        if (!body.contains(r)) {
          counts.put(r, size);
          if (possible(counts))
            attachments(type, counts, body, r + 1, remaining - 1, size, value, groups, bodyRank);
          counts.remove(r);
        }
    }

    void emit(
        Combination.Type type, Map<Integer, Integer> counts, int value, int groups, int bodyRank) {
      if (!possible(counts)) return;
      if (table != null && type != BOMB) {
        int count = counts.values().stream().mapToInt(i -> i).sum();
        if (type != table.type()) return;
        if (isPlane(type)) {
          if (!planes.compatible(count, groups, table)) return;
        } else if (count != table.cardIds().size() || value <= table.value()) return;
      }
      Template template =
          new Template(
              type, new ArrayList<>(new TreeMap<>(counts).entrySet()), value, groups, bodyRank);
      allocate(template, 0, new ArrayList<>(), new TreeMap<>());
    }

    void allocate(
        Template template, int index, List<Integer> ids, TreeMap<Integer, Integer> substitutes) {
      if (index == template.counts().size()) {
        accept(
            template.type(),
            ids,
            substitutes,
            template.value(),
            template.groups(),
            template.bodyRank());
        return;
      }
      var e = template.counts().get(index);
      List<Card> natural = ranks.getOrDefault(e.getKey(), List.of());
      List<Card> available = jokers.stream().filter(c -> !substitutes.containsKey(c.id())).toList();
      for (int use = Math.max(0, e.getValue() - available.size());
          use <= Math.min(e.getValue(), natural.size());
          use++) {
        int need = e.getValue() - use;
        choose(
            natural,
            use,
            chosen ->
                choose(
                    available,
                    need,
                    wc ->
                        allocateChoice(template, index, ids, substitutes, e.getKey(), chosen, wc)));
      }
    }

    private void accept(
        Combination.Type type,
        List<Integer> ids,
        TreeMap<Integer, Integer> substitutes,
        int value,
        int groups,
        int bodyRank) {
      // 叶节点才构造候选，实体 ID 与癞子替代点数始终分别保存。
      if (!wild.allows(type, substitutes)
          || type == BOMB && !bombs.accepts(bodyRank, ids.size(), substitutes.size())) return;
      Combination c =
          new Combination(
              type, ids, substitutes, value, groups, bodyRank, "rank-template/v1; demo-policy");
      if (leadAllows(c, lead) && beats(c, table)) results.putIfAbsent(c.key(), c);
    }

    private void allocateChoice(
        Template template,
        int index,
        List<Integer> ids,
        TreeMap<Integer, Integer> substitutes,
        int rank,
        List<Card> chosen,
        List<Card> wc) {
      int before = ids.size();
      chosen.forEach(c -> ids.add(c.id()));
      wc.forEach(
          c -> {
            ids.add(c.id());
            substitutes.put(c.id(), rank);
          });
      allocate(template, index + 1, ids, substitutes);
      // 回溯必须同时恢复实体牌和替代映射，避免污染下一个分支。
      wc.forEach(c -> substitutes.remove(c.id()));
      ids.subList(before, ids.size()).clear();
    }
  }
}
