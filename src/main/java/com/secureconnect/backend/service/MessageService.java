package com.secureconnect.backend.service;

import com.secureconnect.backend.model.Conversation;
import com.secureconnect.backend.model.Message;
import com.secureconnect.backend.model.User;
import com.secureconnect.backend.repository.ConversationRepository;
import com.secureconnect.backend.repository.MessageRepository;
import com.secureconnect.backend.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationRepository conversationRepository;
    private final UserRepository userRepository;

    public MessageService(
            MessageRepository messageRepository,
            ConversationRepository conversationRepository,
            UserRepository userRepository
    ) {
        this.messageRepository = messageRepository;
        this.conversationRepository = conversationRepository;
        this.userRepository = userRepository;
    }

    public Message sendMessage(
            Long conversationId,
            Long senderId,
            String content
    ) {

        Conversation conversation =
                conversationRepository.findById(conversationId)
                        .orElseThrow(() ->
                                new RuntimeException("Conversation not found"));

        User sender =
                userRepository.findById(senderId)
                        .orElseThrow(() ->
                                new RuntimeException("Sender not found"));

        Message message = new Message(
                conversation,
                sender,
                content,
                LocalDateTime.now()
        );

        return messageRepository.save(message);
    }
}