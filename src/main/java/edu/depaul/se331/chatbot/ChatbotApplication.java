package edu.depaul.se331.chatbot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestTemplate;

@SpringBootApplication
public class ChatbotApplication {

    public static void main(String[] args) {
        SpringApplication.run(ChatbotApplication.class, args);
    }

    /**
     * RestTemplate is the HTTP client used to call the OpenRouter API.
     * Timeouts are configured so the app does not hang on slow responses.
     */
    @Bean
    public RestTemplate restTemplate() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(5_000);   // 5 s to establish connection
        factory.setReadTimeout(60_000);     // 60 s to receive the full reply
        return new RestTemplate(factory);
    }
}
