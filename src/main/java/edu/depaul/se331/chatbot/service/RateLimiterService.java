package edu.depaul.se331.chatbot.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class RateLimiterService {

    private final Map<String, List<Long>> requestTimestamps = new ConcurrentHashMap<>();

    @Value("${chatbot.rate.limit}")
    private int maxRequests;

    @Value("${chatbot.rate.window.seconds}")
    private int windowSeconds;

    public boolean isAllowed(String sessionId) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        List<Long> timestamps = requestTimestamps.computeIfAbsent(sessionId, k -> new ArrayList<>());

        synchronized (timestamps) {
            timestamps.removeIf(t -> (now - t) > windowMillis);
            return timestamps.size() < maxRequests;
        }
    }

    public void recordRequest(String sessionId) {
        List<Long> timestamps = requestTimestamps.computeIfAbsent(sessionId, k -> new ArrayList<>());
        synchronized (timestamps) {
            timestamps.add(System.currentTimeMillis());
        }
    }

    /**
     * Atomically checks the rate limit and records the request if allowed.
     * Use this instead of separate isAllowed + recordRequest to avoid race conditions.
     */
    public boolean tryAcquire(String sessionId) {
        long now = System.currentTimeMillis();
        long windowMillis = windowSeconds * 1000L;

        List<Long> timestamps = requestTimestamps.computeIfAbsent(sessionId, k -> new ArrayList<>());

        synchronized (timestamps) {
            timestamps.removeIf(t -> (now - t) > windowMillis);
            if (timestamps.size() < maxRequests) {
                timestamps.add(now);
                return true;
            }
            return false;
        }
    }
}
