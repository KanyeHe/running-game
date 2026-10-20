package cn.luzhou.api;

import cn.luzhou.room.RoomService;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.validation.*;
import jakarta.validation.constraints.*;
import java.io.IOException;
import java.util.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.*;
import org.springframework.web.socket.config.annotation.*;
import org.springframework.web.socket.handler.TextWebSocketHandler;

@Configuration
@EnableWebSocket
public class SocketConfig implements WebSocketConfigurer {
  private final RoomService rooms;
  private final ObjectMapper mapper;
  private final Validator validator;

  @Value(
      "${game.allowed-origins:http://localhost:7456,http://localhost:8080,http://localhost:5173,http://127.0.0.1:5173}")
  private String[] origins;

  public SocketConfig(RoomService r, ObjectMapper m, Validator v) {
    rooms = r;
    mapper = m;
    validator = v;
  }

  public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
    registry.addHandler(new Handler(), "/ws").setAllowedOrigins(origins);
  }

  public record Request(
      @NotBlank @Size(max = 32) String type,
      @NotBlank @Size(max = 64) String requestId,
      @Positive long seq,
      @Size(max = 64) String roomId,
      @PositiveOrZero long expectedVersion,
      @Size(max = 128) String token,
      @NotNull @Size(max = 8)
          Map<@NotNull @Size(max = 32) String, @NotNull @Size(max = 4096) String> data) {}

  private final class Handler extends TextWebSocketHandler {
    private final Map<String, String> players = new java.util.concurrent.ConcurrentHashMap<>();

    @Override
    public void afterConnectionEstablished(WebSocketSession session) {
      session.setTextMessageSizeLimit(16384);
    }

    @Override
    protected void handleTextMessage(WebSocketSession session, TextMessage message)
        throws IOException {
      String requestId = "";
      try {
        Request q = mapper.readValue(message.getPayload(), Request.class);
        requestId = q == null ? "" : q.requestId();
        if (q == null || !validator.validate(q).isEmpty())
          throw new IllegalArgumentException("INVALID_REQUEST");
        dispatch(session, q);
      } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
        error(session, "INVALID_REQUEST", requestId);
      } catch (IllegalArgumentException e) {
        error(session, safeCode(e.getMessage()), requestId);
      } catch (Exception e) {
        org.slf4j.LoggerFactory.getLogger(SocketConfig.class).error("WebSocket request failed", e);
        error(session, "SERVER_ERROR", requestId);
      }
    }

    private void dispatch(WebSocketSession session, Request q) throws IOException {
      if (q.type().equals("AUTH")) {
        authenticate(session, q);
        return;
      }
      String player = players.get(session.getId());
      if (player == null) throw new IllegalArgumentException("UNAUTHORIZED");
      send(
          session,
          rooms.command(
              session.getId(),
              player,
              q.type(),
              q.requestId(),
              q.seq(),
              q.roomId(),
              q.expectedVersion(),
              q.data()));
    }

    private void authenticate(WebSocketSession session, Request q) throws IOException {
      String player = rooms.authenticate(q.token()).playerId();
      if (players.containsKey(session.getId()) && !player.equals(players.get(session.getId())))
        throw new IllegalArgumentException("UNAUTHORIZED");
      players.put(session.getId(), player);
      rooms.connected(session.getId(), player);
      rooms.listen(session.getId(), value -> publish(session, value));
      send(
          session, Map.of("type", "LOGIN_SUCCESS", "playerId", player, "requestId", q.requestId()));
    }

    private void publish(WebSocketSession session, Object value) {
      try {
        send(session, value);
      } catch (IOException e) {
        try {
          session.close();
        } catch (IOException ignored) {
          // 断连事件负责清理订阅；广播失败不得回滚已提交的牌局。
        }
      }
    }

    private void error(WebSocketSession session, String code, String requestId) throws IOException {
      send(
          session,
          Map.of("type", "ERROR", "code", code, "requestId", requestId == null ? "" : requestId));
    }

    private String safeCode(String message) {
      return message != null && message.matches("[A-Z_]{3,64}") ? message : "INVALID_REQUEST";
    }

    private void send(WebSocketSession s, Object value) throws IOException {
      synchronized (s) {
        if (s.isOpen()) s.sendMessage(new TextMessage(mapper.writeValueAsString(value)));
      }
    }

    @Override
    public void afterConnectionClosed(WebSocketSession s, CloseStatus status) {
      players.remove(s.getId());
      rooms.disconnected(s.getId());
    }
  }
}
