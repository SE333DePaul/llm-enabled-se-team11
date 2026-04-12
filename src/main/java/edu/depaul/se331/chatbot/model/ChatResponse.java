package edu.depaul.se331.chatbot.model;

/**
 * The JSON body returned to the browser by our REST API.
 *
 * { "reply": "Recursion is a function that calls itself..." }
 *
 * EXTENSION IDEA: add fields like "model", "tokens", or
 * "sources" to surface more information to the client.
 */
public class ChatResponse {

    private String reply;

    public ChatResponse() {}

    public ChatResponse(String reply) {
        this.reply = reply;
    }

    public String getReply() { return reply; }
    public void setReply(String reply) { this.reply = reply; }
}
