package cn.luzhou;

import org.junit.jupiter.api.Test;

class RoomServiceTest {
  @Test
  void concurrentAndRecoverableRooms() throws Exception {
    RoomChecks.run();
  }
}
