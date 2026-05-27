package edu.depaul.se331.chatbot.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import edu.depaul.se331.chatbot.model.SummarizeRequest;
import edu.depaul.se331.chatbot.service.ChatService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(SummarizeController.class)
class SummarizeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void summarize_validText_returnsSummary() throws Exception {
        when(chatService.summarize(anyString())).thenReturn("TL;DR: short summary");

        SummarizeRequest request = new SummarizeRequest();
        request.setText("A very long block of text that needs summarizing.");

        mockMvc.perform(post("/api/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reply").value("TL;DR: short summary"));
    }

    @Test
    void summarize_emptyText_returnsBadRequest() throws Exception {
        SummarizeRequest request = new SummarizeRequest();
        request.setText("");

        mockMvc.perform(post("/api/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.reply").value("Please provide non-empty text to summarize."));
    }

    @Test
    void summarize_nullText_returnsBadRequest() throws Exception {
        SummarizeRequest request = new SummarizeRequest();

        mockMvc.perform(post("/api/summarize")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
