package edu.depaul.se331.chatbot.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.*;

class RateLimiterServiceTest {

    private RateLimiterService rateLimiter;

    @BeforeEach
    void setUp() {
        rateLimiter = new RateLimiterService();
        ReflectionTestUtils.setField(rateLimiter, "maxRequests", 3);
        ReflectionTestUtils.setField(rateLimiter, "windowSeconds", 2);
    }

    @Test
    void isAllowed_firstRequest_returnsTrue() {
        assertTrue(rateLimiter.isAllowed("session1"));
    }

    @Test
    void isAllowed_underLimit_returnsTrue() {
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        assertTrue(rateLimiter.isAllowed("session1"));
    }

    @Test
    void isAllowed_atLimit_returnsFalse() {
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        assertFalse(rateLimiter.isAllowed("session1"));
    }

    @Test
    void isAllowed_afterWindowExpires_returnsTrue() throws InterruptedException {
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        assertFalse(rateLimiter.isAllowed("session1"));

        Thread.sleep(2100);

        assertTrue(rateLimiter.isAllowed("session1"));
    }

    @Test
    void isAllowed_differentSessions_areIndependent() {
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        rateLimiter.recordRequest("session1");
        assertFalse(rateLimiter.isAllowed("session1"));
        assertTrue(rateLimiter.isAllowed("session2"));
    }

    @Test
    void tryAcquire_allowsUpToLimit() {
        assertTrue(rateLimiter.tryAcquire("s1"));
        assertTrue(rateLimiter.tryAcquire("s1"));
        assertTrue(rateLimiter.tryAcquire("s1"));
        assertFalse(rateLimiter.tryAcquire("s1"));
    }

    @Test
    void tryAcquire_afterWindowExpires_allowsAgain() throws InterruptedException {
        rateLimiter.tryAcquire("s1");
        rateLimiter.tryAcquire("s1");
        rateLimiter.tryAcquire("s1");
        assertFalse(rateLimiter.tryAcquire("s1"));

        Thread.sleep(2100);

        assertTrue(rateLimiter.tryAcquire("s1"));
    }
}
