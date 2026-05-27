package edu.depaul.se331.chatbot.controller;

import edu.depaul.se331.chatbot.model.ChatMessage;
import edu.depaul.se331.chatbot.model.ChatRequest;
import edu.depaul.se331.chatbot.model.ChatResponse;
import edu.depaul.se331.chatbot.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * ChatController — HTTP layer for the chatbot.
 *
 * Endpoints:
 *   POST   /api/chat          — send a message, get a reply
 *   DELETE /api/chat/history  — clear the conversation history
 *   GET    /api/chat/history  — (optional) inspect conversation history
 *
 * EXTEND: Add new endpoints here to expose additional features,
 * e.g. POST /api/summarize, POST /api/translate, etc.
 */
@RestController
@RequestMapping("/api/chat")
@CrossOrigin(origins = "*")   // allows the HTML page to call this API
public class ChatController {

    private final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }

    // ── Endpoints ─────────────────────────────────────────────

    /**
     * Send a message to the chatbot.
     *
     * Request body : { "message": "your text here", "sessionId": "abc123" }
     * Response body: { "reply": "assistant reply here" }
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Please provide a non-empty message."));
        }

        // Use the provided sessionId, or fall back to "default" if none was sent
        String sessionId = request.getSessionId();
        if (sessionId == null || sessionId.isBlank()) {
            sessionId = "default";
        }

        String reply = chatService.chat(sessionId, request.getMessage().trim());
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    /**
     * Clear the conversation history for a specific session.
     *
     * DELETE /api/chat/history/{sessionId}
     */
    @DeleteMapping("/history/{sessionId}")
    public ResponseEntity<Map<String, String>> clearSessionHistory(
            @PathVariable String sessionId) {
        chatService.resetHistory(sessionId);
        return ResponseEntity.ok(Map.of("status",
                "Conversation history cleared for session: " + sessionId));
    }

    /**
     * Clear the conversation history for the "default" session.
     * Kept for backwards compatibility with the old endpoint.
     *
     * DELETE /api/chat/history
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        chatService.resetHistory("default");
        return ResponseEntity.ok(Map.of("status", "Conversation history cleared."));
    }

    /**
     * Inspect the conversation history for a specific session (for debugging).
     *
     * GET /api/chat/history/{sessionId}
     */
    @GetMapping("/history/{sessionId}")
    public ResponseEntity<List<ChatMessage>> getSessionHistory(
            @PathVariable String sessionId) {
        return ResponseEntity.ok(chatService.getHistory(sessionId));
    }

    /**
     * Inspect the "default" session history (for debugging).
     * Kept for backwards compatibility.
     *
     * GET /api/chat/history
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory() {
        return ResponseEntity.ok(chatService.getHistory("default"));
    }
}
