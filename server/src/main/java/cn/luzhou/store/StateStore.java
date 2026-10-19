package cn.luzhou.store;

import java.util.*;
import cn.luzhou.room.Room;
public interface StateStore {
    record Identity(String playerId,String token,String name){}
    Room load(String roomId);
    void save(Room room);
    void saveIdentity(Identity identity);
    Optional<Identity> authenticate(String token);
}
