package cn.luzhou.room;

import cn.luzhou.game.Game;
import java.util.*;

public final class Room {
  public String id;
  public long version = 1;
  public long gameSerial = 0;
  public List<String> players = new ArrayList<>();
  public Set<String> ready = new HashSet<>();
  public Map<String, Long> lastSequences = new HashMap<>();
  public Map<String, Receipt> receipts = new LinkedHashMap<>();
  public Map<String, Boolean> online = new HashMap<>();
  public Game game;
  public String scorePlan = "low";
  public List<Operation> operations = new ArrayList<>();

  public record Operation(
      long gameSerial,
      String requestId,
      long seq,
      long expectedRoomVersion,
      long acceptedRoomVersion,
      String player,
      cn.luzhou.rules.Combination combination) {}

  public record Receipt(String requestId, String fingerprint, long acceptedVersion) {}
}
