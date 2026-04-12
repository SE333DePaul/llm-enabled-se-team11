package edu.depaul.se331.chatbot.model;

/**
 * A single turn in the conversation.
 *
 * role    — "system", "user", or "assistant"
 * content — the text of the message
 *
 * This maps directly to the OpenRouter / OpenAI message format,
 * so Jackson will serialize it as {"role":"...", "content":"..."}.
 */
public class ChatMessage {

    private String role;
    private String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role    = role;
        this.content = content;
    }

    public String getRole()    { return role; }
    public String getContent() { return content; }

    public void setRole(String role)       { this.role    = role; }
    public void setContent(String content) { this.content = content; }
}
