package cn.luzhou.store;

import cn.luzhou.room.Room;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Duration;
import java.util.*;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * SQL room snapshot and audit append commit together. Redis is a disposable cache. Single service
 * node.
 */
@Component
@Profile("persistent")
public class JdbcRedisStore implements StateStore {
  private final JdbcTemplate db;
  private final StringRedisTemplate redis;
  private final ObjectMapper json;
  private final TransactionTemplate tx;

  public JdbcRedisStore(
      JdbcTemplate db,
      StringRedisTemplate redis,
      ObjectMapper json,
      PlatformTransactionManager manager) {
    this.db = db;
    this.redis = redis;
    this.json = json;
    tx = new TransactionTemplate(manager);
  }

  private String encode(Object value) {
    try {
      return json.writeValueAsString(value);
    } catch (Exception e) {
      throw new IllegalStateException(e);
    }
  }

  public Room load(String id) {
    // SQL is always authoritative; never restore a stale cache over a committed snapshot.
    var rows = db.queryForList("SELECT snapshot FROM rooms WHERE id=?", String.class, id);
    if (rows.isEmpty()) return null;
    try {
      return json.readValue(rows.getFirst(), Room.class);
    } catch (Exception e) {
      throw new IllegalStateException("SNAPSHOT_INVALID", e);
    }
  }

  public void save(Room room) {
    String snapshot = encode(room);
    tx.executeWithoutResult(status -> saveSnapshotAndAudit(room, snapshot));
    refreshCache(room, snapshot);
  }

  private void saveSnapshotAndAudit(Room room, String snapshot) {
    // 快照、审计和积分流水必须共用一个 SQL 事务，Redis 不参与权威提交。
    db.update(
        "INSERT INTO rooms(id,version,snapshot) VALUES(?,?,?) ON DUPLICATE KEY UPDATE "
            + "version=VALUES(version),snapshot=VALUES(snapshot)",
        room.id,
        room.version,
        snapshot);
    if (room.game == null) return;
    String gameId = room.id + ":" + room.gameSerial;
    saveMatch(room, gameId);
    savePlayers(room, gameId);
    appendEvents(room.game, gameId);
    appendBombs(room.game, gameId);
    appendOperations(room);
    appendSettlement(room, gameId);
  }

  private void saveMatch(Room room, String gameId) {
    var g = room.game;
    db.update(
        "INSERT INTO matches(id,room_id,winner) VALUES(?,?,?) ON DUPLICATE KEY UPDATE"
            + " winner=VALUES(winner)",
        gameId,
        room.id,
        g.winner);
  }

  private void savePlayers(Room room, String gameId) {
    var g = room.game;
    for (int p = 0; p < 4; p++)
      db.update(
          "INSERT INTO player_results(match_id,player_id,seat,remaining,score)"
              + " VALUES(?,?,?,?,?) ON DUPLICATE KEY UPDATE"
              + " remaining=VALUES(remaining),score=VALUES(score)",
          gameId,
          room.players.get(p),
          p,
          g.hands.get(p).size(),
          g.scores[p]);
  }

  private void appendEvents(cn.luzhou.game.Game g, String gameId) {
    for (int i = 0; i < g.events.size(); i++) {
      var event = g.events.get(i);
      db.update(
          "INSERT IGNORE INTO"
              + " play_records(match_id,event_no,player_seat,state_version,event_type,payload)"
              + " VALUES(?,?,?,?,?,?)",
          gameId,
          i,
          event.player(),
          event.version(),
          event.type(),
          encode(event));
    }
  }

  private void appendBombs(cn.luzhou.game.Game g, String gameId) {
    for (var e : g.ledger.entries)
      db.update(
          "INSERT IGNORE INTO"
              + " bomb_ledger(match_id,entry_no,trick,player_seat,amount,reverses,payload)"
              + " VALUES(?,?,?,?,?,?,?)",
          gameId,
          e.id(),
          e.trick(),
          e.player(),
          e.perOpponent(),
          e.reverses(),
          encode(e));
  }

  private void appendOperations(Room room) {
    for (var op : room.operations)
      db.update(
          "INSERT IGNORE INTO"
              + " operations(room_id,player_id,command_seq,request_id,expected_version,accepted_version,payload)"
              + " VALUES(?,?,?,?,?,?,?)",
          room.id,
          op.player(),
          op.seq(),
          op.requestId(),
          op.expectedRoomVersion(),
          op.acceptedRoomVersion(),
          encode(op));
  }

  private void appendSettlement(Room room, String gameId) {
    var g = room.game;
    if (g.winner >= 0)
      for (int p = 0; p < 4; p++)
        db.update(
            "INSERT IGNORE INTO score_ledger(match_id,player_id,amount) VALUES(?,?,?)",
            gameId,
            room.players.get(p),
            g.scores[p]);
  }

  private void refreshCache(Room room, String snapshot) {
    try {
      redis.opsForValue().set("room:" + room.id, snapshot, Duration.ofDays(1));
      redis.opsForValue().set("room-node:" + room.id, "single-node", Duration.ofDays(1));
      room.online.forEach(
          (p, online) ->
              redis.opsForValue().set("online:" + p, online.toString(), Duration.ofMinutes(5)));
    } catch (org.springframework.dao.DataAccessException ignored) {
      /* Committed SQL is the recovery source. */
    }
  }

  public void saveIdentity(Identity id) {
    db.update(
        "INSERT INTO users(id,token,name) VALUES(?,?,?)", id.playerId(), id.token(), id.name());
    try {
      redis.opsForValue().set("session:" + id.token(), id.playerId(), Duration.ofDays(1));
    } catch (org.springframework.dao.DataAccessException ignored) {
    }
  }

  public Optional<Identity> authenticate(String token) {
    var users =
        db.query(
            "SELECT id,token,name FROM users WHERE token=?",
            (rs, n) -> new Identity(rs.getString(1), rs.getString(2), rs.getString(3)),
            token);
    return users.stream().findFirst();
  }
}
