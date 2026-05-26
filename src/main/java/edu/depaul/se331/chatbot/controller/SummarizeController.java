package edu.depaul.se331.chatbot.controller;

import edu.depaul.se331.chatbot.model.ChatResponse;
import edu.depaul.se331.chatbot.model.SummarizeRequest;
import edu.depaul.se331.chatbot.service.ChatService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * SummarizeController — handles the summarize endpoint.
 *
 * POST /api/summarize — send a block of text, get a TL;DR summary back.
 *
 * This endpoint does NOT use or modify the chat conversation history.
 * It makes a one-shot call to the LLM to summarize the given text.
 */
@RestController
@RequestMapping("/api/summarize")
@CrossOrigin(origins = "*")
public class SummarizeController {

    private final ChatService chatService;

    public SummarizeController(ChatService chatService) {
        this.chatService = chatService;
    }

    /**
     * Summarize a block of text.
     *
     * Request body : { "text": "A long block of text..." }
     * Response body: { "reply": "TL;DR summary here..." }
     */
    @PostMapping
    public ResponseEntity<ChatResponse> summarize(@RequestBody SummarizeRequest request) {
        if (request.getText() == null || request.getText().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new ChatResponse("Please provide non-empty text to summarize."));
        }

        String summary = chatService.summarize(request.getText().trim());
        return ResponseEntity.ok(new ChatResponse(summary));
    }
}
