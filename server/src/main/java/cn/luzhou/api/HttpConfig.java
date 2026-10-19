package cn.luzhou.api;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.*;
@Configuration
public class HttpConfig implements WebMvcConfigurer {
    @Value("${game.allowed-origins:http://localhost:7456,http://localhost:8080,http://localhost:5173,http://127.0.0.1:5173}")private String[] origins;
    @Override public void addCorsMappings(CorsRegistry registry){registry.addMapping("/api/**").allowedOrigins(origins).allowedMethods("GET","POST").allowedHeaders("Content-Type");}
}
