package cn.luzhou.game;

import static cn.luzhou.rules.CardCombinationFinder.Lead.*;

import cn.luzhou.rules.*;
import java.util.*;

/** Pure Java state machine. Its owning room must serialize all access. */
public final class Game {
  public List<List<Card>> hands = new ArrayList<>();
  public List<Card> discarded = new ArrayList<>();
  public List<Integer> played = new ArrayList<>();
  public Combination table;
  public int turn, last = -1, consecutiveUnable, winner = -1;
  public long trick = 1, version = 1, deadline;
  public boolean firstAction = true, firstGame = true;
  public int[] scores = new int[4];
  public ScoreRuleConfig scoreConfig = ScoreRuleConfig.low();
  public BombScoreLedger ledger = new BombScoreLedger();
  public List<Event> events = new ArrayList<>();

  public static Game deal(Random random, Integer previousWinner, ScoreRuleConfig scores) {
    Game g = new Game();
    g.firstGame = previousWinner == null;
    g.scoreConfig = scores;
    List<Card> deck = new ArrayList<>(Card.deck());
    // Unspecified case: spade 3 is in discarded cards. Explicit demo policy: reshuffle.
    do {
      Collections.shuffle(deck, random);
    } while (previousWinner == null && deck.subList(52, 54).contains(new Card(Card.SPADE_THREE)));
    for (int p = 0; p < 4; p++) g.hands.add(new ArrayList<>(deck.subList(p * 13, p * 13 + 13)));
    g.discarded = new ArrayList<>(deck.subList(52, 54));
    g.turn =
        previousWinner == null
            ? java.util.stream.IntStream.range(0, 4)
                .filter(p -> g.hands.get(p).contains(new Card(Card.SPADE_THREE)))
                .findFirst()
                .orElseThrow()
            : previousWinner;
    g.deadline = System.currentTimeMillis() + 30000;
    g.events.add(new Event(g.version, "GAME_STARTED", g.turn, g.trick, "server-deal"));
    return g;
  }

  public CardCombinationFinder.Lead lead() {
    return !firstAction ? FREE : firstGame ? FIRST_SPADE_THREE : WINNER_FIRST;
  }

  public List<Combination> legal(int player, CardCombinationFinder finder) {
    return winner >= 0 || turn != player
        ? List.of()
        : finder.find(hands.get(player), table, lead());
  }

  public void play(int player, String key, long expectedVersion, CardCombinationFinder finder) {
    if (version != expectedVersion) throw new IllegalArgumentException("STALE_VERSION");
    if (winner >= 0) throw new IllegalArgumentException("GAME_FINISHED");
    if (turn != player) throw new IllegalArgumentException("NOT_YOUR_TURN");
    Combination c =
        legal(player, finder).stream()
            .filter(x -> x.key().equals(key))
            .findFirst()
            .orElseThrow(() -> new IllegalArgumentException("ILLEGAL_COMBINATION"));
    hands.get(player).removeIf(card -> c.cardIds().contains(card.id()));
    played.addAll(c.cardIds());
    version++;
    table = c;
    last = player;
    firstAction = false;
    // 每次成功压制都重置连续无解计数，允许同一轮反复绕桌多圈。
    consecutiveUnable = 0;
    events.add(new Event(version, "PLAYED", player, trick, c.key(), c));
    recordBomb(player, c, finder);
    if (hands.get(player).isEmpty()) {
      settle(player);
      return;
    }
    turn = (turn + 1) % 4;
    advance(finder);
    deadline = System.currentTimeMillis() + 30000;
  }

  private void settle(int player) {
    // 赢家最后一手的炸弹已先记账，再按剩余手牌档位结算。
    winner = player;
    for (int p = 0; p < 4; p++)
      if (p != winner) {
        int loss = scoreConfig.forRemaining(hands.get(p).size());
        scores[p] -= loss;
        scores[winner] += loss;
      }
    events.add(new Event(version, "SETTLED", winner, trick, Arrays.toString(scores)));
  }

  private void recordBomb(int player, Combination c, CardCombinationFinder finder) {
    if (c.type() == Combination.Type.BOMB) {
      ledger.record(trick, player, c, finder.bombPolicy(), scoreConfig, scores);
      events.add(new Event(version, "BOMB_SCORE_CHANGED", player, trick, Arrays.toString(scores)));
    }
  }

  private void advance(CardCombinationFinder finder) {
    while (table != null && finder.find(hands.get(turn), table, FREE).isEmpty()) {
      consecutiveUnable++;
      events.add(new Event(version, "AUTO_SKIPPED", turn, trick, "no-legal-response"));
      if (consecutiveUnable == 3) {
        events.add(new Event(version, "TRICK_ENDED", last, trick, "three-unable"));
        turn = last;
        table = null;
        consecutiveUnable = 0;
        trick++;
        return;
      }
      turn = (turn + 1) % 4;
    }
  }

  public void timeout(CardCombinationFinder finder, TimeoutPolicy policy, long now) {
    if (winner >= 0 || now < deadline) return;
    policy.choose(legal(turn, finder)).ifPresent(c -> play(turn, c.key(), version, finder));
  }

  public void assertConservation() {
    List<Integer> ids = new ArrayList<>(played);
    discarded.forEach(c -> ids.add(c.id()));
    hands.forEach(h -> h.forEach(c -> ids.add(c.id())));
    if (ids.size() != 54 || new HashSet<>(ids).size() != 54)
      throw new IllegalStateException("CARD_CONSERVATION");
    if (Arrays.stream(scores).sum() != 0) throw new IllegalStateException("SCORE_CONSERVATION");
  }

  public record Event(
      long version, String type, int player, long trick, String detail, Combination combination) {
    public Event(long version, String type, int player, long trick, String detail) {
      this(version, type, player, trick, detail, null);
    }
  }
}
