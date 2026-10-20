package cn.luzhou.store;

import cn.luzhou.room.Room;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

@Component
@Profile("memory")
public class MemoryStore implements StateStore {
  private final Map<String, Room> rooms = new ConcurrentHashMap<>();
  private final Map<String, Identity> identities = new ConcurrentHashMap<>();

  public Room load(String id) {
    return rooms.get(id);
  }

  public void save(Room room) {
    rooms.put(room.id, room);
  }

  public void saveIdentity(Identity identity) {
    identities.put(identity.token(), identity);
  }

  public Optional<Identity> authenticate(String token) {
    return Optional.ofNullable(identities.get(token));
  }
}
