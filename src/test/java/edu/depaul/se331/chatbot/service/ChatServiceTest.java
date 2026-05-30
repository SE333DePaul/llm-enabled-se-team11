package edu.depaul.se331.chatbot.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import edu.depaul.se331.chatbot.model.ChatMessage;
import edu.depaul.se331.chatbot.repository.ChatMessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private ChatMessageRepository chatMessageRepository;

    @InjectMocks
    private ChatService chatService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(chatService, "apiKey", "test-key");
        ReflectionTestUtils.setField(chatService, "apiUrl", "https://api.test.com/chat");
        ReflectionTestUtils.setField(chatService, "model", "test-model");
        ReflectionTestUtils.setField(chatService, "systemPrompt", "You are a test bot.");
    }

    private JsonNode buildLlmResponse(String content) throws Exception {
        String json = """
                {
                  "choices": [
                    { "message": { "role": "assistant", "content": "%s" } }
                  ]
                }
                """.formatted(content);
        return objectMapper.readTree(json);
    }

    @Test
    void chat_savesUserMessageToDatabase() throws Exception {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of(new ChatMessage("s1", "user", "hello")));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(buildLlmResponse("hi"), HttpStatus.OK));

        chatService.chat("s1", "hello");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage userMsg = captor.getAllValues().get(0);
        assertEquals("user", userMsg.getRole());
        assertEquals("hello", userMsg.getContent());
        assertEquals("s1", userMsg.getSessionId());
    }

    @Test
    void chat_savesAssistantReplyToDatabase() throws Exception {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of(new ChatMessage("s1", "user", "hello")));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(buildLlmResponse("hi there"), HttpStatus.OK));

        chatService.chat("s1", "hello");

        ArgumentCaptor<ChatMessage> captor = ArgumentCaptor.forClass(ChatMessage.class);
        verify(chatMessageRepository, times(2)).save(captor.capture());
        ChatMessage assistantMsg = captor.getAllValues().get(1);
        assertEquals("assistant", assistantMsg.getRole());
        assertEquals("hi there", assistantMsg.getContent());
    }

    @Test
    void chat_callsOpenRouterWithCorrectPayload() throws Exception {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of(new ChatMessage("s1", "user", "What is Java?")));
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(buildLlmResponse("A language"), HttpStatus.OK));

        chatService.chat("s1", "What is Java?");

        verify(restTemplate).postForEntity(
                eq("https://api.test.com/chat"),
                any(HttpEntity.class),
                eq(JsonNode.class));
    }

    @Test
    void chat_returnsErrorOnNetworkFailure() {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("Connection refused"));

        String reply = chatService.chat("s1", "hello");

        assertTrue(reply.contains("API Error"));
    }

    @Test
    void resetHistory_deletesSessionMessages() {
        chatService.resetHistory("s1");
        verify(chatMessageRepository).deleteBySessionId("s1");
    }

    @Test
    void getHistory_returnsMessagesInOrder() {
        List<ChatMessage> messages = List.of(
                new ChatMessage("s1", "user", "first"),
                new ChatMessage("s1", "assistant", "second"));
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1")).thenReturn(messages);

        List<ChatMessage> result = chatService.getHistory("s1");

        assertEquals(2, result.size());
        assertEquals("first", result.get(0).getContent());
        assertEquals("second", result.get(1).getContent());
    }

    @Test
    void getHistory_emptyForUnknownSession() {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("unknown")).thenReturn(List.of());

        List<ChatMessage> result = chatService.getHistory("unknown");

        assertTrue(result.isEmpty());
    }

    @Test
    void summarize_sendsOneShotRequest() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(buildLlmResponse("TL;DR summary"), HttpStatus.OK));

        String result = chatService.summarize("Long text here...");

        assertEquals("TL;DR summary", result);
        verify(restTemplate).postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class));
    }

    @Test
    void summarize_doesNotModifyHistory() throws Exception {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(buildLlmResponse("summary"), HttpStatus.OK));

        chatService.summarize("Some text");

        verify(chatMessageRepository, never()).save(any());
        verify(chatMessageRepository, never()).findBySessionIdOrderByIdAsc(anyString());
    }

    @Test
    void chat_handlesNullResponseBody() {
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>((JsonNode) null, HttpStatus.OK));

        String reply = chatService.chat("s1", "hello");

        assertTrue(reply.contains("Error"));
    }

    @Test
    void chat_handlesMalformedApiResponse() throws Exception {
        JsonNode malformed = objectMapper.readTree("{\"unexpected\": true}");
        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(malformed, HttpStatus.OK));

        String reply = chatService.chat("s1", "hello");

        assertTrue(reply.contains("unexpected API response"));
    }

    @Test
    void chat_retriesOnTooManyRequestsAndReturns429Error() {
        HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "rate limit".getBytes(),
                null);

        when(chatMessageRepository.findBySessionIdOrderByIdAsc("s1"))
                .thenReturn(List.of());
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(tooManyRequests);

        String reply = chatService.chat("s1", "hello");

        assertEquals("API Error: 429 Too Many Requests - rate limit", reply);
        verify(chatMessageRepository, times(2)).save(any());
    }

    @Test
    void summarize_handlesHttpStatusError() {
        HttpClientErrorException badRequest = HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST,
                "Bad Request",
                HttpHeaders.EMPTY,
                "bad input".getBytes(),
                null);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(badRequest);

        String reply = chatService.summarize("Long text here...");

        assertEquals("API Error: 400 BAD_REQUEST - bad input", reply);
    }

    @Test
    void summarize_handlesRestClientException() {
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(new ResourceAccessException("Connection timed out"));

        String reply = chatService.summarize("Long text here...");

        assertEquals("API Error: Connection timed out", reply);
    }

    @Test
    void summarize_interruptedDuringBackoff_returnsInterruptedMessage() {
        HttpClientErrorException tooManyRequests = HttpClientErrorException.create(
                HttpStatus.TOO_MANY_REQUESTS,
                "Too Many Requests",
                HttpHeaders.EMPTY,
                "rate limit".getBytes(),
                null);

        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenThrow(tooManyRequests);

        boolean originalInterrupted = Thread.interrupted();
        try {
            Thread.currentThread().interrupt();
            String reply = chatService.summarize("Long text here...");
            assertEquals("API Error: interrupted while waiting to retry.", reply);
        } finally {
            if (!originalInterrupted) {
                Thread.interrupted();
            }
        }
    }

    @Test
    void summarize_handlesMalformedApiResponse() throws Exception {
        JsonNode malformed = objectMapper.readTree("{\"unexpected\": true}");
        when(restTemplate.postForEntity(anyString(), any(HttpEntity.class), eq(JsonNode.class)))
                .thenReturn(new ResponseEntity<>(malformed, HttpStatus.OK));

        String reply = chatService.summarize("Some text");

        assertEquals("Error: unexpected API response — " + malformed, reply);
    }
}
