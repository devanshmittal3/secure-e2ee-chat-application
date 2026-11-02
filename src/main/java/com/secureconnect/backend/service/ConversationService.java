
package com.secureconnect.backend.service;

import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.model.ConversationMember;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationMemberRepository;
import com.secureconnect.backend.repository.ConversationRepository;
import com.secureconnect.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

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

    @Transactional
    public Conversation createConversation(
            String authenticatedEmail,
            Long otherUserId
    ) {
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        User otherUser = userRepository.findById(otherUserId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Other user not found"
                ));

        if (currentUser.getId().equals(otherUser.getId())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "You cannot create a conversation with yourself"
            );
        }

        // Find existing conversations belonging to current user.
        List<ConversationMember> memberships =
                conversationMemberRepository.findByUserId(
                        currentUser.getId()
                );

        // Reuse a conversation if both users are members.
        for (ConversationMember membership : memberships) {
            Long conversationId =
                    membership.getConversation().getId();

            boolean otherUserIsMember =
                    conversationMemberRepository
                            .existsByConversationIdAndUserId(
                                    conversationId,
                                    otherUser.getId()
                            );

            if (otherUserIsMember) {
                return membership.getConversation();
            }
        }

        // Otherwise create a new conversation.
        Conversation conversation =
                new Conversation(LocalDateTime.now());

        Conversation savedConversation =
                conversationRepository.save(conversation);

        conversationMemberRepository.save(
                new ConversationMember(savedConversation, currentUser)
        );

        conversationMemberRepository.save(
                new ConversationMember(savedConversation, otherUser)
        );

        return savedConversation;
    }

    @Transactional(readOnly = true)
    public List<Conversation> getMyConversations(
            String authenticatedEmail
    ) {
        User currentUser = userRepository.findByEmail(authenticatedEmail)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        return conversationMemberRepository
                .findByUserId(currentUser.getId())
                .stream()
                .map(ConversationMember::getConversation)
                .toList();
    }
}
