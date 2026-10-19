
package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ConversationMemberRepository
        extends JpaRepository<ConversationMember, Long> {

    boolean existsByConversationIdAndUserId(
            Long conversationId,
            Long userId
    );

    List<ConversationMember> findByUserId(Long userId);
}
