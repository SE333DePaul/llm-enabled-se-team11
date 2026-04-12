package edu.depaul.se331.chatbot.model;

/**
 * The JSON body sent by the browser to our REST API.
 *
 * POST /api/chat
 * { "message": "What is recursion?" }
 *
 * EXTENSION IDEA: add a "sessionId" field to support
 * multiple independent chat sessions.
 */
public class ChatRequest {

    private String message;

    public ChatRequest() {}

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }
}
