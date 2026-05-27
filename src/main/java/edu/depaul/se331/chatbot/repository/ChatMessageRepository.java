package edu.depaul.se331.chatbot.repository;

import edu.depaul.se331.chatbot.model.ChatMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for ChatMessage entities.
 *
 * Spring automatically generates the SQL queries from the method names.
 * For example, "findBySessionId" becomes:
 *   SELECT * FROM chat_messages WHERE session_id = ?
 */
@Repository
public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /**
     * Find all messages for a given session, ordered by their ID
     * (which is auto-incremented, so older messages have smaller IDs).
     */
    List<ChatMessage> findBySessionIdOrderByIdAsc(String sessionId);

    /**
     * Delete all messages for a given session.
     */
    void deleteBySessionId(String sessionId);
}
