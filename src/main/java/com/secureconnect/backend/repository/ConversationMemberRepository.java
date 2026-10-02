package com.secureconnect.backend.repository;

import com.secureconnect.backend.model.ConversationMember;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ConversationMemberRepository
        extends JpaRepository<ConversationMember, Long> {
}