package edu.depaul.se331.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.depaul.se331.chatbot.model.ChatMessage;
import edu.depaul.se331.chatbot.model.ChatRequest;
import edu.depaul.se331.chatbot.service.ChatService;
import edu.depaul.se331.chatbot.service.RateLimiterService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(ChatController.class)
class ChatControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    @MockBean
    private RateLimiterService rateLimiterService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void chat_validMessage_returnsReply() throws Exception {
        when(rateLimiterService.tryAcquire(anyString())).thenReturn(true);
        when(chatService.chat(anyString(), anyString())).thenReturn("Hello!");

        ChatRequest request = new ChatRequest();
        request.setMessage("Hi");
        request.setSessionId("s1");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("Hello!"));
    }

    @Test
    void chat_emptyMessage_returnsBadRequest() throws Exception {
        ChatRequest request = new ChatRequest();
        request.setMessage("");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reply").value("Please provide a non-empty message."));
    }

    @Test
    void chat_nullMessage_returnsBadRequest() throws Exception {
        ChatRequest request = new ChatRequest();

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void chat_nullSessionId_usesDefault() throws Exception {
        when(rateLimiterService.tryAcquire("default")).thenReturn(true);
        when(chatService.chat(eq("default"), anyString())).thenReturn("reply");

        ChatRequest request = new ChatRequest();
        request.setMessage("Hi");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(chatService).chat(eq("default"), anyString());
    }

    @Test
    void chat_rateLimitExceeded_returns429() throws Exception {
        when(rateLimiterService.tryAcquire(anyString())).thenReturn(false);

        ChatRequest request = new ChatRequest();
        request.setMessage("Hi");
        request.setSessionId("s1");

        mockMvc.perform(post("/api/chat")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.reply").value("Rate limit exceeded. Please wait before sending more messages."));

        verify(chatService, never()).chat(anyString(), anyString());
    }

    @Test
    void clearSessionHistory_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/chat/history/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Conversation history cleared for session: s1"));

        verify(chatService).resetHistory("s1");
    }

    @Test
    void clearHistory_defaultSession_returnsOk() throws Exception {
        mockMvc.perform(delete("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Conversation history cleared."));

        verify(chatService).resetHistory("default");
    }

    @Test
    void getSessionHistory_returnsMessages() throws Exception {
        List<ChatMessage> messages = List.of(
                new ChatMessage("s1", "user", "hello"),
                new ChatMessage("s1", "assistant", "hi"));
        when(chatService.getHistory("s1")).thenReturn(messages);

        mockMvc.perform(get("/api/chat/history/s1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("user"))
                .andExpect(jsonPath("$[0].content").value("hello"))
                .andExpect(jsonPath("$[1].role").value("assistant"));
    }

    @Test
    void getHistory_defaultSession_returnsMessages() throws Exception {
        when(chatService.getHistory("default")).thenReturn(List.of());

        mockMvc.perform(get("/api/chat/history"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
