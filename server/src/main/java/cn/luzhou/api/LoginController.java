package cn.luzhou.api;

import cn.luzhou.room.RoomService;
import cn.luzhou.store.StateStore;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;

@RestController
public class LoginController {
  private final RoomService rooms;

  public LoginController(RoomService r) {
    rooms = r;
  }

  @PostMapping("/api/login")
  public StateStore.Identity login(@Valid @RequestBody Login request) {
    return rooms.login(request.name());
  }

  @GetMapping("/api/health")
  public java.util.Map<String, String> health() {
    return java.util.Map.of("status", "UP", "policy", "demo-v1", "auth", "guest-MVP");
  }

  public record Login(@NotBlank @Size(max = 24) String name) {}
}
