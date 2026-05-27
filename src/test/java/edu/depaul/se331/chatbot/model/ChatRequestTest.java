package edu.depaul.se331.chatbot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatRequestTest {

    @Test
    void noArgConstructor_createsEmptyInstance() {
        ChatRequest req = new ChatRequest();
        assertNull(req.getMessage());
        assertNull(req.getSessionId());
    }

    @Test
    void setters_updateFields() {
        ChatRequest req = new ChatRequest();
        req.setMessage("What is Java?");
        req.setSessionId("abc123");

        assertEquals("What is Java?", req.getMessage());
        assertEquals("abc123", req.getSessionId());
    }
}
