package cn.luzhou.store;

import cn.luzhou.room.Room;
import java.util.*;

public interface StateStore {
  Room load(String roomId);

  void save(Room room);

  void saveIdentity(Identity identity);

  Optional<Identity> authenticate(String token);

  record Identity(String playerId, String token, String name) {}
}
