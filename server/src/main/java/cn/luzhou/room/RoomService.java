package cn.luzhou.room;

import cn.luzhou.game.*;
import cn.luzhou.rules.*;
import cn.luzhou.store.StateStore;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.security.SecureRandom;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.springframework.stereotype.Service;

@Service
public class RoomService {
  private final StateStore store;
  private final ObjectMapper mapper;
  private final Map<String, Object> locks = new ConcurrentHashMap<>();
  private final RoomCommandHandler commands;
  private final RoomViewFactory views;
  private final SecureRandom random = new SecureRandom();
  private final Map<String, Consumer<Map<String, Object>>> listeners = new ConcurrentHashMap<>();
  private final Map<String, String> connectionPlayers = new ConcurrentHashMap<>();
  private final Map<String, String> connectionRooms = new ConcurrentHashMap<>();

  public RoomService(StateStore store, ObjectMapper mapper) {
    this(store, mapper, CardCombinationFinder.demo());
  }

  @org.springframework.beans.factory.annotation.Autowired
  public RoomService(StateStore store, ObjectMapper mapper, CardCombinationFinder finder) {
    this.store = store;
    this.mapper = mapper;
    this.commands = new RoomCommandHandler(finder, random);
    this.views = new RoomViewFactory(finder);
  }

  public StateStore.Identity login(String name) {
    byte[] bytes = new byte[32];
    random.nextBytes(bytes);
    var id =
        new StateStore.Identity(
            UUID.randomUUID().toString(),
            Base64.getUrlEncoder().withoutPadding().encodeToString(bytes),
            name);
    store.saveIdentity(id);
    return id;
  }

  public StateStore.Identity authenticate(String token) {
    return store
        .authenticate(token)
        .orElseThrow(() -> new IllegalArgumentException("UNAUTHORIZED"));
  }

  public void listen(String connectionId, Consumer<Map<String, Object>> consumer) {
    listeners.put(connectionId, consumer);
  }

  public void unlisten(String id) {
    listeners.remove(id);
  }

  public void connected(String connection, String player) {
    connectionPlayers.put(connection, player);
  }

  public void disconnected(String connection) {
    String player = connectionPlayers.remove(connection), id = connectionRooms.remove(connection);
    unlisten(connection);
    if (player == null || id == null) return;
    synchronized (locks.computeIfAbsent(id, k -> new Object())) {
      Room r = copy(require(id));
      boolean online =
          connectionRooms.entrySet().stream()
              .anyMatch(
                  e -> e.getValue().equals(id) && player.equals(connectionPlayers.get(e.getKey())));
      r.online.put(player, online);
      r.version++;
      store.save(r);
      broadcast(r, "PLAYER_DISCONNECTED");
    }
  }

  public Map<String, Object> command(
      String connection,
      String player,
      String type,
      String requestId,
      long seq,
      String roomId,
      long expected,
      Map<String, String> data) {
    validateRoomId(type, roomId);
    synchronized (locks.computeIfAbsent(roomId, k -> new Object())) {
      Room current = store.load(roomId);
      if (current == null && !type.equals("CREATE_ROOM"))
        throw new IllegalArgumentException("ROOM_NOT_FOUND");
      Room r = current == null ? new Room() : copy(current);
      r.id = roomId;
      if (type.equals("SNAPSHOT") || type.equals("HINT")) {
        requireMember(r, player);
        connectionRooms.put(connection, roomId);
        return view(r, player, type, requestId);
      }
      Map<String, Object> receipt = replay(r, player, type, requestId, seq, expected, data);
      if (receipt != null) {
        connectionRooms.put(connection, roomId);
        return receipt;
      }
      commands.apply(r, current != null, player, type, requestId, seq, expected, data);
      commit(r, player, type, requestId, seq, expected, data);
      connectionRooms.put(connection, roomId);
      broadcast(r, type);
      return view(r, player, "ACK", requestId);
    }
  }

  private void validateRoomId(String type, String roomId) {
    // 创建请求使用稳定房间号，使重试仍然落在同一把房间锁上。
    if (type.equals("CREATE_ROOM") && (roomId == null || !roomId.matches("[a-zA-Z0-9-]{6,64}")))
      throw new IllegalArgumentException("INVALID_ROOM_ID");
    if (roomId == null) throw new IllegalArgumentException("ROOM_REQUIRED");
  }

  private Map<String, Object> replay(
      Room r,
      String player,
      String type,
      String requestId,
      long seq,
      long expected,
      Map<String, String> data) {
    Room.Receipt old = r.receipts.get(player + ":" + seq);
    // 必须先查回执再校验当前版本；重放已提交请求不能再次扣牌或结算。
    if (old != null) {
      if (!old.requestId().equals(requestId)
          || !old.fingerprint().equals(fingerprint(type, expected, data)))
        throw new IllegalArgumentException("SEQUENCE_CONFLICT");
      Map<String, Object> response = view(r, player, "ACK", requestId);
      response.put("acceptedVersion", old.acceptedVersion());
      return response;
    }
    if (seq <= r.lastSequences.getOrDefault(player, 0L))
      throw new IllegalArgumentException("OLD_SEQUENCE");
    if (!type.equals("JOIN_ROOM") && !type.equals("RECONNECT") && expected != r.version)
      throw new IllegalArgumentException("STALE_VERSION");
    return null;
  }

  private String fingerprint(String type, long expected, Map<String, String> data) {
    return type + ":" + expected + ":" + new TreeMap<>(data);
  }

  private void commit(
      Room r,
      String player,
      String type,
      String requestId,
      long seq,
      long expected,
      Map<String, String> data) {
    for (String member : r.players)
      r.online.put(member, member.equals(player) || isOnline(r.id, member));
    r.version++;
    r.lastSequences.put(player, seq);
    r.receipts.put(
        player + ":" + seq,
        new Room.Receipt(requestId, fingerprint(type, expected, data), r.version));
    while (r.receipts.size() > 512) r.receipts.remove(r.receipts.keySet().iterator().next());
    // 写入成功后才能发布事件，失败时丢弃工作副本，已提交房间不受影响。
    store.save(r);
  }

  private boolean isOnline(String room, String player) {
    return connectionRooms.entrySet().stream()
        .anyMatch(
            e -> e.getValue().equals(room) && player.equals(connectionPlayers.get(e.getKey())));
  }

  private Room copy(Room room) {
    return mapper.convertValue(room, Room.class);
  }

  private Room require(String id) {
    Room r = store.load(id);
    if (r == null) throw new IllegalArgumentException("ROOM_NOT_FOUND");
    return r;
  }

  private void requireMember(Room r, String player) {
    if (!r.players.contains(player)) throw new IllegalArgumentException("NOT_IN_ROOM");
  }

  private void broadcast(Room r, String type) {
    connectionRooms.forEach(
        (id, room) -> {
          if (room.equals(r.id)) {
            var consumer = listeners.get(id);
            if (consumer != null)
              try {
                consumer.accept(view(r, connectionPlayers.get(id), type, null));
              } catch (RuntimeException ignored) {
                /* Transport closes independently; committed state remains authoritative. */
              }
          }
        });
  }

  public Map<String, Object> view(Room r, String player, String event, String request) {
    return views.view(r, player, event, request);
  }
}
