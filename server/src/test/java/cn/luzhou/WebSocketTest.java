package cn.luzhou;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.*;
import java.net.http.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class WebSocketTest {
  @LocalServerPort int port;

  @Test
  void fourClientsDealPlayAndReconnect() throws Exception {
    HttpClient http = HttpClient.newHttpClient();
    ObjectMapper json = new ObjectMapper();
    List<Client> clients = new ArrayList<>();
    String room = "ws-" + UUID.randomUUID();
    long version = 1;
    try {
      version = joinPlayers(http, json, clients, room);
      Map<String, Object> snapshot = null;
      for (Client c : clients) {
        snapshot = c.send("READY", room, version, Map.of(), null);
        assertEquals("ACK", snapshot.get("type"));
        version = ((Number) snapshot.get("version")).longValue();
      }
      int turn = ((Number) snapshot.get("turn")).intValue();
      Client c = clients.get(turn);
      snapshot = c.send("SNAPSHOT", room, version, Map.of(), null);
      assertEquals(13, ((List<?>) snapshot.get("hand")).size());
      assertFalse(snapshot.containsKey("hands"));
      var combo =
          json.convertValue(
              ((List<?>) snapshot.get("legal")).getFirst(), cn.luzhou.rules.Combination.class);
      var played = c.send("PLAY", room, version, Map.of("combinationKey", combo.key()), null);
      assertEquals("ACK", played.get("type"));
      assertTrue(((Number) played.get("version")).longValue() > version);
      var reconnected = c.send("RECONNECT", room, 0, Map.of(), null);
      assertEquals("ACK", reconnected.get("type"));
    } finally {
      for (Client c : clients) c.close();
    }
  }

  @Test
  void invalidRequestsReturnProtocolErrors() throws Exception {
    try (Client client = login(HttpClient.newHttpClient(), new ObjectMapper())) {
      client.socket.sendText("null", true).join();
      var error = client.inbox.poll(5, TimeUnit.SECONDS);
      assertNotNull(error);
      assertEquals("INVALID_REQUEST", error.get("code"));
      assertEquals("ERROR", error.get("type"));
      var invalid =
          client.send("JOIN_ROOM", "invalid-room", 0, Collections.singletonMap("bad", null), null);
      assertEquals("INVALID_REQUEST", invalid.get("code"));
      client.socket.sendText("[", true).join();
      error = client.inbox.poll(5, TimeUnit.SECONDS);
      assertNotNull(error);
      assertEquals("INVALID_REQUEST", error.get("code"));
    }
  }

  private Client login(HttpClient http, ObjectMapper json) throws Exception {
    var res =
        http.send(
            HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/login"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString("{\"name\":\"guest\"}"))
                .build(),
            HttpResponse.BodyHandlers.ofString());
    assertEquals(200, res.statusCode());
    var identity = json.readTree(res.body());
    Client c = new Client();
    http.newWebSocketBuilder().buildAsync(URI.create("ws://localhost:" + port + "/ws"), c).join();
    assertEquals(
        "LOGIN_SUCCESS",
        c.send("AUTH", null, 0, Map.of(), identity.get("token").asText()).get("type"));
    c.player = identity.get("playerId").asText();
    return c;
  }

  private long joinPlayers(HttpClient http, ObjectMapper json, List<Client> clients, String room)
      throws Exception {
    long version = 1;
    for (int i = 0; i < 4; i++) {
      Client c = login(http, json);
      clients.add(c);
      var ack = c.send(i == 0 ? "CREATE_ROOM" : "JOIN_ROOM", room, version, Map.of(), null);
      assertEquals("ACK", ack.get("type"));
      version = ((Number) ack.get("version")).longValue();
    }
    return version;
  }

  static class Client implements WebSocket.Listener, AutoCloseable {
    final BlockingQueue<Map<String, Object>> inbox = new LinkedBlockingQueue<>();
    final StringBuilder buffer = new StringBuilder();
    final ObjectMapper json = new ObjectMapper();
    WebSocket socket;
    long seq;
    String player;

    @Override
    public void onOpen(WebSocket socket) {
      this.socket = socket;
      socket.request(1);
    }

    @Override
    public CompletionStage<?> onText(WebSocket socket, CharSequence data, boolean last) {
      buffer.append(data);
      if (last) {
        try {
          inbox.add(
              json.readValue(
                  buffer.toString(), new com.fasterxml.jackson.core.type.TypeReference<>() {}));
        } catch (Exception e) {
          throw new RuntimeException(e);
        }
        buffer.setLength(0);
      }
      socket.request(1);
      return null;
    }

    Map<String, Object> send(
        String type, String room, long version, Map<String, String> data, String token)
        throws Exception {
      String id = "request-" + (++seq);
      Map<String, Object> q = new HashMap<>();
      q.put("type", type);
      q.put("requestId", id);
      q.put("seq", seq);
      q.put("roomId", room);
      q.put("expectedVersion", version);
      q.put("data", data);
      q.put("token", token);
      socket.sendText(json.writeValueAsString(q), true).join();
      long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
      while (System.nanoTime() < end) {
        var message = inbox.poll(1, TimeUnit.SECONDS);
        if (message != null && id.equals(message.get("requestId"))) return message;
      }
      throw new AssertionError("No ACK: " + type);
    }

    public void close() {
      socket.sendClose(WebSocket.NORMAL_CLOSURE, "done");
    }
  }
}
