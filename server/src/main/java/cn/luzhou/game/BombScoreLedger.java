package cn.luzhou.game;

import cn.luzhou.rules.*;
import java.util.*;

public final class BombScoreLedger {
  public final List<Entry> entries = new ArrayList<>();

  private static void transfer(int[] scores, int winner, int amount) {
    for (int p = 0; p < 4; p++) scores[p] += p == winner ? 3 * amount : -amount;
  }

  public void record(
      long trick,
      int player,
      Combination bomb,
      BombPolicy policy,
      ScoreRuleConfig config,
      int[] scores) {
    Entry active =
        entries.stream()
            .filter(e -> e.trick() == trick && e.reverses() == null)
            .filter(e -> entries.stream().noneMatch(r -> Objects.equals(r.reverses(), e.id())))
            .findFirst()
            .orElse(null);
    // 同轮新炸弹替换旧炸弹时追加反向流水，历史记录不能覆盖或删除。
    if (active != null) {
      transfer(scores, active.player(), -active.perOpponent());
      entries.add(
          new Entry(
              entries.size() + 1L,
              trick,
              active.player(),
              -active.perOpponent(),
              active.id(),
              active.combination()));
    }
    int amount = config.tiers().get(policy.isTwoBomb(bomb) ? 3 : 2);
    transfer(scores, player, amount);
    entries.add(new Entry(entries.size() + 1L, trick, player, amount, null, bomb.key()));
  }

  public record Entry(
      long id, long trick, int player, int perOpponent, Long reverses, String combination) {}
}
