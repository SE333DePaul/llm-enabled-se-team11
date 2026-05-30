package edu.depaul.se331.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import edu.depaul.se331.chatbot.model.ChatMessage;
import edu.depaul.se331.chatbot.repository.ChatMessageRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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

    /**
     * Sampling temperature for the LLM. 0 = (near-)deterministic, 1 = creative.
     *
     * We default to 0 to reduce output variance, which makes the Track 2 LLM
     * behavior tests (see LlmBehaviorTest) far more reproducible. Note: temperature
     * 0 lowers variance but does NOT guarantee identical output across runs or
     * model versions — that limitation is discussed in RESEARCH.md.
     */
    @Value("${openrouter.temperature:0.0}")
    private double temperature;

    // ── State ─────────────────────────────────────────────────
    private final RestTemplate restTemplate;
    private final ChatMessageRepository chatMessageRepository;

    public ChatService(RestTemplate restTemplate,
                       ChatMessageRepository chatMessageRepository) {
        this.restTemplate = restTemplate;
        this.chatMessageRepository = chatMessageRepository;
    }

    // ── Public API ────────────────────────────────────────────

    /**
     * Send a user message to the LLM and return the assistant reply.
     *
     * High-level flow:
     *   save the user message to the database
     *   → load this session's full history from the database
     *   → build payload (system + history) → POST to OpenRouter
     *   → save the assistant reply to the database → return reply
     */
    public String chat(String sessionId, String userMessage) {
        // 1. Save the user's message to the database
        chatMessageRepository.save(new ChatMessage(sessionId, "user", userMessage));

        // 2. Load the full conversation history for this session from the database
        List<ChatMessage> history = chatMessageRepository.findBySessionIdOrderByIdAsc(sessionId);

        // 3. Call the LLM with the full history
        String reply = callOpenRouter(history);

        // 4. Save the assistant's reply to the database
        chatMessageRepository.save(new ChatMessage(sessionId, "assistant", reply));

        return reply;
    }

    /** Clear history for a specific session by deleting all its messages from the database. */
    @Transactional
    public void resetHistory(String sessionId) {
        chatMessageRepository.deleteBySessionId(sessionId);
    }

    /** Load a specific session's history from the database. */
    public List<ChatMessage> getHistory(String sessionId) {
        return chatMessageRepository.findBySessionIdOrderByIdAsc(sessionId);
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
        body.put("temperature", temperature);

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
        body.put("temperature", temperature);   // 0 = deterministic, 1 = creative

        // EXTEND: Add optional parameters here, for example:
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
