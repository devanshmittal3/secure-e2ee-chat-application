
package com.secureconnect.backend.service;

import com.secureconnect.backend.dto.ConversationSummaryResponse;
import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.model.ConversationMember;
import com.secureconnect.backend.model.Message;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationMemberRepository;
import com.secureconnect.backend.repository.ConversationRepository;
import com.secureconnect.backend.repository.MessageRepository;
import com.secureconnect.backend.repository.UserRepository;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    public ConversationService(
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            MessageRepository messageRepository,
            UserRepository userRepository
    ) {
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.messageRepository = messageRepository;
        this.userRepository = userRepository;
    }

    // Create or reuse an existing one-to-one conversation.
    @Transactional
    public Conversation createConversation(
            String authenticatedEmail,
            Long otherUserId
    ) {
        User currentUser = findUser(authenticatedEmail);

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

        List<ConversationMember> memberships =
                conversationMemberRepository.findByUserId(currentUser.getId());

        for (ConversationMember membership : memberships) {

            Long conversationId = membership.getConversation().getId();

            boolean otherUserIsMember =
                    conversationMemberRepository
                            .existsByConversationIdAndUserId(
                                    conversationId,
                                    otherUser.getId()
                            );

            if (otherUserIsMember) {

                // Reopen the chat for this user without restoring old messages.
                if (membership.getHiddenAt() != null) {
                    membership.setHiddenAt(null);
                    conversationMemberRepository.save(membership);
                }

                return membership.getConversation();
            }
        }

        Conversation conversation = conversationRepository.save(
                new Conversation(LocalDateTime.now())
        );

        conversationMemberRepository.save(
                new ConversationMember(conversation, currentUser)
        );

        conversationMemberRepository.save(
                new ConversationMember(conversation, otherUser)
        );

        return conversation;
    }

    // Return conversations visible to the authenticated user.
    @Transactional(readOnly = true)
    public List<ConversationSummaryResponse> getMyConversations(
            String authenticatedEmail
    ) {
        User currentUser = findUser(authenticatedEmail);

        List<ConversationSummaryResponse> summaries = new ArrayList<>();

        List<ConversationMember> memberships =
                conversationMemberRepository.findByUserId(currentUser.getId());

        for (ConversationMember membership : memberships) {

            Long conversationId = membership.getConversation().getId();

            List<Message> messages =
                    messageRepository.findByConversationIdOrderByCreatedAtAsc(
                            conversationId
                    );

            Message latestMessage =
                    messages.isEmpty()
                            ? null
                            : messages.get(messages.size() - 1);

            // A deleted chat stays hidden until a new message arrives.
            if (membership.getHiddenAt() != null
                    && (latestMessage == null
                    || !latestMessage.getCreatedAt().isAfter(
                            membership.getHiddenAt()
                    ))) {
                continue;
            }

            ConversationMember otherMember =
                    conversationMemberRepository.findByConversationId(conversationId)
                            .stream()
                            .filter(member ->
                                    !member.getUser().getId().equals(currentUser.getId())
                            )
                            .findFirst()
                            .orElse(null);

            if (otherMember == null) {
                continue;
            }

            // Do not expose messages from before the user's clear-history time.
            Message visibleLatest = latestMessage;

            if (visibleLatest != null
                    && membership.getHistoryClearedAt() != null
                    && !visibleLatest.getCreatedAt().isAfter(
                            membership.getHistoryClearedAt()
                    )) {
                visibleLatest = null;
            }

            summaries.add(new ConversationSummaryResponse(
                    conversationId,
                    otherMember.getUser().getId(),
                    otherMember.getUser().getUsername(),
                    null, // Never expose message content in conversation-list previews.

                    visibleLatest == null
                            ? null
                            : visibleLatest.getCreatedAt(),
                    membership.getConversation().getCreatedAt()
            ));
        }

        return summaries.stream()
                .sorted(Comparator.comparing(
                        (ConversationSummaryResponse summary) ->
                                summary.lastMessageAt() == null
                                        ? summary.createdAt()
                                        : summary.lastMessageAt(),
                        Comparator.nullsLast(Comparator.reverseOrder())
                ))
                .toList();
    }

    // Delete a conversation only for the authenticated user.
    @Transactional
    public void deleteForMe(
            String authenticatedEmail,
            Long conversationId
    ) {
        User currentUser = findUser(authenticatedEmail);

        ConversationMember membership =
                findMembership(currentUser.getId(), conversationId);

        LocalDateTime now = LocalDateTime.now();

        membership.setHiddenAt(now);
        membership.setHistoryClearedAt(now);

        conversationMemberRepository.save(membership);
    }

    // Permanently delete a conversation and all its messages for everyone.
    @Transactional
    public void deleteForEveryone(
            String authenticatedEmail,
            Long conversationId
    ) {
        User currentUser = findUser(authenticatedEmail);

        findMembership(currentUser.getId(), conversationId);

        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conversation not found"
                ));

        // Delete messages first to respect database foreign keys.
        messageRepository.deleteAll(
                messageRepository.findByConversationIdOrderByCreatedAtAsc(
                        conversationId
                )
        );

        messageRepository.flush();

        // Remove all conversation memberships.
        conversationMemberRepository.deleteAll(
                conversationMemberRepository.findByConversationId(
                        conversationId
                )
        );

        conversationMemberRepository.flush();

        // Finally delete the conversation itself.
        conversationRepository.delete(conversation);
        conversationRepository.flush();
    }

    // Clear all conversations only for the authenticated user.
    @Transactional
public int clearAllForMe(String email) {
    User user = findUser(email);

    List<ConversationMember> memberships =
            conversationMemberRepository.findByUserId(user.getId());

    LocalDateTime now = LocalDateTime.now();

    for (ConversationMember membership : memberships) {
        membership.setHiddenAt(now);
        membership.setHistoryClearedAt(now);
    }

    conversationMemberRepository.saveAll(memberships);

    return memberships.size();
}

    // Find authenticated user.
    private User findUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));
    }

    // Verify that the user belongs to the requested conversation.
    private ConversationMember findMembership(
            Long userId,
            Long conversationId
    ) {
        return conversationMemberRepository
                .findByUserIdAndConversationId(userId, conversationId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.FORBIDDEN,
                        "You are not a member of this conversation"
                ));
    }
}
