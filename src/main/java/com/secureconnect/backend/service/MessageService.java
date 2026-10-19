
package com.secureconnect.backend.service;

import com.secureconnect.backend.dto.MessageResponse;
import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.model.Message;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationMemberRepository;
import com.secureconnect.backend.repository.ConversationRepository;
import com.secureconnect.backend.repository.MessageRepository;
import com.secureconnect.backend.repository.UserRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final ConversationMemberRepository conversationMemberRepository;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            ConversationMemberRepository conversationMemberRepository,
            UserRepository userRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.conversationMemberRepository = conversationMemberRepository;
        this.userRepository = userRepository;
    }

    public MessageResponse sendMessage(
            Long conversationId,
            String email,
            String content
    ) {
        if (content == null || content.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Message content cannot be empty"
            );
        }

        User sender = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        Conversation conversation = conversationRepository
                .findById(conversationId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Conversation not found"
                ));

        boolean isMember =
                conversationMemberRepository
                        .existsByConversationIdAndUserId(
                                conversationId,
                                sender.getId()
                        );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this conversation"
            );
        }

        Message message = new Message(
                conversation,
                sender,
                content.trim(),
                LocalDateTime.now()
        );

        Message savedMessage = messageRepository.save(message);

        return toResponse(savedMessage);
    }

    public List<MessageResponse> getMessagesByConversation(
            Long conversationId,
            String email
    ) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.UNAUTHORIZED,
                        "Authenticated user not found"
                ));

        if (!conversationRepository.existsById(conversationId)) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Conversation not found"
            );
        }

        boolean isMember =
                conversationMemberRepository
                        .existsByConversationIdAndUserId(
                                conversationId,
                                user.getId()
                        );

        if (!isMember) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "You are not a member of this conversation"
            );
        }

        List<Message> messages =
                messageRepository
                        .findByConversationIdOrderByCreatedAtAsc(
                                conversationId
                        );

        return messages.stream()
                .map(this::toResponse)
                .toList();
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getContent(),
                message.getCreatedAt()
        );
    }
}
