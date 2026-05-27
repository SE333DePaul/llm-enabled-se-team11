package edu.depaul.se331.chatbot.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageTest {

    @Test
    void noArgConstructor_createsEmptyInstance() {
        ChatMessage msg = new ChatMessage();
        assertNull(msg.getId());
        assertNull(msg.getRole());
        assertNull(msg.getContent());
        assertNull(msg.getSessionId());
    }

    @Test
    void twoArgConstructor_setsRoleAndContent() {
        ChatMessage msg = new ChatMessage("user", "hello");
        assertEquals("user", msg.getRole());
        assertEquals("hello", msg.getContent());
        assertNull(msg.getSessionId());
    }

    @Test
    void threeArgConstructor_setsAllFields() {
        ChatMessage msg = new ChatMessage("s1", "assistant", "reply");
        assertEquals("s1", msg.getSessionId());
        assertEquals("assistant", msg.getRole());
        assertEquals("reply", msg.getContent());
    }

    @Test
    void setters_updateFields() {
        ChatMessage msg = new ChatMessage();
        msg.setId(42L);
        msg.setSessionId("s2");
        msg.setRole("system");
        msg.setContent("prompt");

        assertEquals(42L, msg.getId());
        assertEquals("s2", msg.getSessionId());
        assertEquals("system", msg.getRole());
        assertEquals("prompt", msg.getContent());
    }
}
