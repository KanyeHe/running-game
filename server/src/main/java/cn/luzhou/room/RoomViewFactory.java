package cn.luzhou.room;

import cn.luzhou.game.Game;
import cn.luzhou.rules.CardCombinationFinder;
import java.util.*;

/** 只投影公开状态和请求者自己的手牌，禁止直接序列化 Room 返回客户端。 */
public final class RoomViewFactory {
  private final CardCombinationFinder finder;

  public RoomViewFactory(CardCombinationFinder finder) {
    this.finder = finder;
  }

  private void requireMember(Room r, String player) {
    if (!r.players.contains(player)) throw new IllegalArgumentException("NOT_IN_ROOM");
  }

  public Map<String, Object> view(Room r, String player, String event, String request) {
    requireMember(r, player);
    Map<String, Object> v = new LinkedHashMap<>();
    v.put("type", event);
    v.put("requestId", request);
    v.put("roomId", r.id);
    v.put("version", r.version);
    v.put("playerId", player);
    v.put("players", r.players);
    v.put("ready", r.ready);
    v.put("online", r.online);
    v.put("policy", "demo-v1");
    v.put("gameSerial", r.gameSerial);
    if (r.game != null) {
      addGame(v, r, player);
    }
    return v;
  }

  private void addGame(Map<String, Object> v, Room r, String player) {
    Game g = r.game;
    int seat = r.players.indexOf(player);
    v.put("hand", g.hands.get(seat));
    v.put("counts", g.hands.stream().map(List::size).toList());
    v.put("turn", g.turn);
    v.put("table", g.table);
    v.put("trick", g.trick);
    v.put("winner", g.winner);
    v.put("scores", g.scores);
    v.put("deadline", g.deadline);
    v.put("serverTime", System.currentTimeMillis());
    v.put("legal", g.legal(seat, finder));
    v.put("events", g.events.stream().skip(Math.max(0, g.events.size() - 12)).toList());
  }
}
