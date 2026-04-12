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
     * Request body : { "message": "your text here" }
     * Response body: { "reply": "assistant reply here" }
     */
    @PostMapping
    public ResponseEntity<ChatResponse> chat(@RequestBody ChatRequest request) {
        if (request.getMessage() == null || request.getMessage().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Please provide a non-empty message."));
        }
        String reply = chatService.chat(request.getMessage().trim());
        return ResponseEntity.ok(new ChatResponse(reply));
    }

    /**
     * Clear the in-memory conversation history.
     *
     * Useful when you want to start a completely new topic
     * without the model seeing previous turns.
     */
    @DeleteMapping("/history")
    public ResponseEntity<Map<String, String>> clearHistory() {
        chatService.resetHistory();
        return ResponseEntity.ok(Map.of("status", "Conversation history cleared."));
    }

    /**
     * Inspect the current conversation history (for debugging).
     *
     * EXTEND: Remove or restrict this endpoint before deploying
     * to a production environment.
     */
    @GetMapping("/history")
    public ResponseEntity<List<ChatMessage>> getHistory() {
        return ResponseEntity.ok(chatService.getHistory());
    }
}
