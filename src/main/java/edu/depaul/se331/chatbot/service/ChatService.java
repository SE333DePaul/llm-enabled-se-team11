package edu.depaul.se331.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.depaul.se331.chatbot.model.ChatMessage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestClientException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * ChatService — the core of the chatbot.
 *
 * Responsibilities:
 *   1. Maintain the in-memory conversation history.
 *   2. Build a request payload in OpenAI-compatible format.
 *   3. Call the OpenRouter API and return the assistant's reply.
 *
 * ──────────────────────────────────────────────────────────────
 * EXTENSION POINTS (clearly marked with "// EXTEND:" comments)
 * ──────────────────────────────────────────────────────────────
 */
@Service
public class ChatService {

    // ── Configuration ─────────────────────────────────────────
    // Values are read from src/main/resources/application.properties

    @Value("${openrouter.api.key}")
    private String apiKey;

    @Value("${openrouter.api.url}")
    private String apiUrl;

    /** Model to use.  Change in application.properties. */
    @Value("${openrouter.model}")
    private String model;

    /**
     * The system prompt defines your chatbot's persona and rules.
     *
     * EXTEND: Change this in application.properties to create a
     * specialized assistant (tutor, code reviewer, chef, etc.).
     */
    @Value("${chatbot.system.prompt}")
    private String systemPrompt;

    // ── State ─────────────────────────────────────────────────
    private final RestTemplate restTemplate;

    /**
     * In-memory conversation histories, one per session.
     *
     * Each key is a session ID string (e.g. "abc123"), and the
     * value is that session's conversation history.
     *
     * When a new session ID arrives for the first time, we
     * automatically create an empty history list for it.
     */
    private final Map<String, List<ChatMessage>> sessionHistories = new HashMap<>();

    public ChatService(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Send a user message to the LLM and return the assistant reply.
     *
     * High-level flow:
     *   look up (or create) the history for this sessionId
     *   → append user message to that session's history
     *   → build payload (system + history) → POST to OpenRouter
     *   → parse reply → append to history → return reply
     */
    public String chat(String sessionId, String userMessage) {
        List<ChatMessage> history = getOrCreateHistory(sessionId);

        history.add(new ChatMessage("user", userMessage));

        String reply = callOpenRouter(history);

        history.add(new ChatMessage("assistant", reply));
        return reply;
    }

    /** Clear history for a specific session. */
    public void resetHistory(String sessionId) {
        sessionHistories.remove(sessionId);
    }

    /** Read-only view of a specific session's history. */
    public List<ChatMessage> getHistory(String sessionId) {
        List<ChatMessage> history = sessionHistories.get(sessionId);
        if (history == null) {
            return List.of();
        }
        return List.copyOf(history);
    }

    /**
     * Look up the history list for a session ID.
     * If this is the first message for that session, create a new empty list.
     */
    private List<ChatMessage> getOrCreateHistory(String sessionId) {
        if (!sessionHistories.containsKey(sessionId)) {
            sessionHistories.put(sessionId, new ArrayList<>());
        }
        return sessionHistories.get(sessionId);
    }

    /**
     * Summarize a block of text using a one-shot LLM call.
     *
     * This does NOT use or modify the conversation history.
     * It sends a fresh request to the LLM with just a system instruction
     * to summarize and the user's text, then returns the summary.
     */
    public String summarize(String text) {
        // Build a one-shot messages list (no history involved)
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system",
                "You are a helpful summarizer. Provide a clear and concise TL;DR summary of the text the user gives you. Keep it to 2-3 sentences."));
        messages.add(new ChatMessage("user", text));

        // Build the request body
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Make the API call (with the same retry logic as chat)
        final int maxAttempts = 4;
        long delayMs = 1000L;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<JsonNode> response =
                        restTemplate.postForEntity(apiUrl, request, JsonNode.class);

                return parseReply(response.getBody());

            } catch (HttpStatusCodeException e) {
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt == maxAttempts) {
                        return "API Error: 429 Too Many Requests - " + e.getResponseBodyAsString();
                    }
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "API Error: interrupted while waiting to retry.";
                    }
                    delayMs *= 2;
                    continue;
                }
                return "API Error: " + e.getStatusCode() + " - " + e.getResponseBodyAsString();

            } catch (RestClientException e) {
                return "API Error: " + e.getMessage();
            }
        }

        return "API Error: unknown failure";
    }

    // ── Private helpers ───────────────────────────────────────

    /**
     * Build the request body and call OpenRouter.
     *
     * OpenRouter uses the same JSON format as the OpenAI Chat API:
     * {
     *   "model": "...",
     *   "messages": [
     *     { "role": "system",    "content": "..." },
     *     { "role": "user",      "content": "..." },
     *     { "role": "assistant", "content": "..." },
     *     ...
     *   ]
     * }
     *
     * @param history the conversation history for the current session
     */
    private String callOpenRouter(List<ChatMessage> history) {
        // Build the messages list: system prompt first, then this session's history
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", systemPrompt));
        messages.addAll(history);

        // Build request body as a plain Map — Jackson serializes it to JSON
        Map<String, Object> body = new HashMap<>();
        body.put("model", model);
        body.put("messages", messages);

        // EXTEND: Add optional parameters here, for example:
        //   body.put("temperature", 0.7);   // 0 = deterministic, 1 = creative
        //   body.put("max_tokens", 512);

        HttpHeaders headers = buildHeaders();
        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        // Retry loop for transient rate-limit (429) errors.
        final int maxAttempts = 4;
        long delayMs = 1000L; // initial backoff

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {
            try {
                ResponseEntity<JsonNode> response =
                        restTemplate.postForEntity(apiUrl, request, JsonNode.class);

                return parseReply(response.getBody());

            } catch (HttpStatusCodeException e) {
                // If rate-limited, retry with exponential backoff
                if (e.getStatusCode() == HttpStatus.TOO_MANY_REQUESTS) {
                    if (attempt == maxAttempts) {
                        return "API Error: 429 Too Many Requests - " + e.getResponseBodyAsString();
                    }
                    try {
                        Thread.sleep(delayMs);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        return "API Error: interrupted while waiting to retry.";
                    }
                    delayMs *= 2;
                    continue;
                }

                // Non-retriable HTTP error — return body for debugging
                String respBody = e.getResponseBodyAsString();
                return "API Error: " + e.getStatusCode() + " - " + respBody;

            } catch (RestClientException e) {
                // Network / client error — do not retry by default
                return "API Error: " + e.getMessage();
            }
        }

        return "API Error: unknown failure";
    }

    private HttpHeaders buildHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(apiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        // OpenRouter asks for these headers to identify your app
        headers.set("HTTP-Referer", "http://localhost:8080");
        headers.set("X-Title", "SE331 Chatbot");
        return headers;
    }

    /**
     * Extract the assistant's text from the OpenRouter response.
     *
     * Response shape (same as OpenAI):
     * {
     *   "choices": [
     *     { "message": { "role": "assistant", "content": "Hello!" } }
     *   ]
     * }
     */
    private String parseReply(JsonNode responseBody) {
        if (responseBody == null) {
            return "Error: empty response from API.";
        }
        JsonNode content = responseBody
                .path("choices")
                .path(0)
                .path("message")
                .path("content");

        if (content.isMissingNode()) {
            // Surface the raw response so students can debug
            return "Error: unexpected API response — " + responseBody;
        }
        return content.asText();
    }
}
