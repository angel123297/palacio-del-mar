package co.palaciodelmar.backend.controller;

import co.palaciodelmar.backend.dto.ApiResponse;
import co.palaciodelmar.backend.service.ChatService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/chat")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;

    @PostMapping
    public ResponseEntity<Map<String, Object>> chat(@RequestBody Map<String, Object> body) {
        String message = body != null && body.get("message") != null ? body.get("message").toString() : "";
        @SuppressWarnings("unchecked")
        List<Map<String, String>> history = body != null && body.get("history") instanceof List
                ? (List<Map<String, String>>) body.get("history")
                : List.of();

        Map<String, Object> result = chatService.processChat(message, history);
        result.put("success", true);
        return ResponseEntity.ok(result);
    }

    @GetMapping("/status")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getStatus() {
        return ResponseEntity.ok(ApiResponse.ok(chatService.getStatus()));
    }

    @PostMapping("/clear-cache")
    public ResponseEntity<ApiResponse<String>> clearCache() {
        return ResponseEntity.ok(ApiResponse.ok("Cache de rate limiting limpiado", null));
    }
}
