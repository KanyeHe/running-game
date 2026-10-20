package cn.luzhou.room;

import cn.luzhou.game.*;
import cn.luzhou.rules.*;
import java.util.*;

/** 房间内的业务变更；调用方必须先取得房间锁并在副本上操作。 */
final class RoomCommandHandler {
  private final CardCombinationFinder finder;
  private final Random random;

  RoomCommandHandler(CardCombinationFinder finder, Random random) {
    this.finder = finder;
    this.random = random;
  }

  void apply(
      Room r,
      boolean exists,
      String player,
      String type,
      String requestId,
      long seq,
      long expected,
      Map<String, String> data) {
    switch (type) {
      case "CREATE_ROOM" -> create(r, exists, player, data);
      case "JOIN_ROOM" -> join(r, player);
      case "READY" -> ready(r, player);
      case "PLAY" -> play(r, player, requestId, seq, expected, data);
      case "RECONNECT" -> requireMember(r, player);
      default -> throw new IllegalArgumentException("UNKNOWN_TYPE");
    }
  }

  private void create(Room r, boolean exists, String player, Map<String, String> data) {
    if (exists) throw new IllegalArgumentException("ROOM_EXISTS");
    r.players.add(player);
    r.scorePlan = data.getOrDefault("scorePlan", "low");
    if (!Set.of("low", "high").contains(r.scorePlan))
      throw new IllegalArgumentException("INVALID_SCORE_PLAN");
  }

  private void join(Room r, String player) {
    if (r.players.contains(player)) return;
    if (r.players.size() >= 4 || r.game != null) throw new IllegalArgumentException("ROOM_FULL");
    r.players.add(player);
  }

  private void ready(Room r, String player) {
    requireMember(r, player);
    if (r.game != null && r.game.winner < 0) throw new IllegalArgumentException("GAME_RUNNING");
    r.ready.add(player);
    if (r.players.size() != 4 || r.ready.size() != 4) return;
    Integer previous = r.game == null ? null : r.game.winner;
    r.game =
        Game.deal(
            random,
            previous,
            r.scorePlan.equals("high") ? ScoreRuleConfig.high() : ScoreRuleConfig.low());
    r.gameSerial++;
    r.ready.clear();
  }

  private void play(
      Room r, String player, String requestId, long seq, long expected, Map<String, String> data) {
    requireMember(r, player);
    if (r.game == null) throw new IllegalArgumentException("GAME_NOT_STARTED");
    r.game.play(r.players.indexOf(player), data.get("combinationKey"), r.game.version, finder);
    r.game.assertConservation();
    Combination played =
        r.game.events.stream()
            .filter(e -> e.combination() != null)
            .reduce((a, b) -> b)
            .orElseThrow()
            .combination();
    r.operations.add(
        new Room.Operation(r.gameSerial, requestId, seq, expected, r.version + 1, player, played));
  }

  private void requireMember(Room r, String player) {
    if (!r.players.contains(player)) throw new IllegalArgumentException("NOT_IN_ROOM");
  }
}
