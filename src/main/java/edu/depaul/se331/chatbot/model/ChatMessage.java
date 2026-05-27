package edu.depaul.se331.chatbot.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A single turn in the conversation.
 *
 * role    — "system", "user", or "assistant"
 * content — the text of the message
 *
 * This class is BOTH:
 *   1. A JPA entity stored in the H2 database
 *   2. The JSON shape sent to the OpenRouter API
 *
 * The "id" and "sessionId" fields are database-only — they are
 * hidden from JSON with @JsonIgnore so the OpenRouter API only
 * sees {"role":"...", "content":"..."}.
 */
@Entity
@Table(name = "chat_messages")
public class ChatMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @JsonIgnore
    private Long id;

    @JsonIgnore
    private String sessionId;

    private String role;

    @Column(length = 10000)
    private String content;

    public ChatMessage() {}

    public ChatMessage(String role, String content) {
        this.role    = role;
        this.content = content;
    }

    public ChatMessage(String sessionId, String role, String content) {
        this.sessionId = sessionId;
        this.role      = role;
        this.content   = content;
    }

    public Long getId()        { return id; }
    public String getSessionId() { return sessionId; }
    public String getRole()    { return role; }
    public String getContent() { return content; }

    public void setId(Long id)             { this.id = id; }
    public void setSessionId(String sessionId) { this.sessionId = sessionId; }
    public void setRole(String role)       { this.role    = role; }
    public void setContent(String content) { this.content = content; }
}
