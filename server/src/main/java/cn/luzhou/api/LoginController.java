package cn.luzhou.api;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import org.springframework.web.bind.annotation.*;
import cn.luzhou.room.RoomService;
import cn.luzhou.store.StateStore;
@RestController
public class LoginController {
    private final RoomService rooms;
    public LoginController(RoomService r){rooms=r;}
    public record Login(@NotBlank @Size(max=24) String name){}
    @PostMapping("/api/login") public StateStore.Identity login(@Valid @RequestBody Login request){return rooms.login(request.name());}
    @GetMapping("/api/health") public java.util.Map<String,String> health(){return java.util.Map.of("status","UP","policy","demo-v1","auth","guest-MVP");}
}
