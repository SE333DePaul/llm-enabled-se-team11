package edu.depaul.se331.chatbot.model;

/**
 * The JSON body sent by the browser to our REST API.
 *
 * POST /api/chat
 * { "message": "What is recursion?", "sessionId": "abc123" }
 */
public class ChatRequest {

    private String message;
    private String sessionId;

    public ChatRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getSessionId() { return sessionId; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
}
