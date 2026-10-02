package com.secureconnect.backend.service;

import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.model.ConversationMember;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationMemberRepository;
import com.secureconnect.backend.repository.ConversationRepository;
import com.secureconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
    }

    public Conversation createConversation(Long userId1, Long userId2) {

        User user1 = userRepository.findById(userId1)
                .orElseThrow(() -> new RuntimeException("User 1 not found"));

        User user2 = userRepository.findById(userId2)
                .orElseThrow(() -> new RuntimeException("User 2 not found"));

        Conversation conversation =
                new Conversation(LocalDateTime.now());

        Conversation savedConversation =
                conversationRepository.save(conversation);

        ConversationMember member1 =
                new ConversationMember(savedConversation, user1);

        ConversationMember member2 =
                new ConversationMember(savedConversation, user2);

        conversationMemberRepository.save(member1);
        conversationMemberRepository.save(member2);

        return savedConversation;
    }
}