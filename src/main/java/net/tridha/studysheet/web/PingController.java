package net.tridha.studysheet.web;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;

@RestController
public class PingController {

    private final Instant startTime = Instant.now();

    @GetMapping("/api/ping")
    public ResponseEntity<Map<String, Object>> ping() {
        long uptimeSeconds = Duration.between(startTime, Instant.now()).getSeconds();
        return ResponseEntity.ok(Map.of(
            "status", "UP",
            "message", "pong",
            "service", "StudySheet",
            "timestamp", Instant.now().toString(),
            "uptimeSeconds", uptimeSeconds
        ));
    }

    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of("status", "UP"));
    }
}
