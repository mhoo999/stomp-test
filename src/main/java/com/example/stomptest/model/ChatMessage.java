package com.example.stomptest.model;

import java.time.LocalDateTime;

public class ChatMessage {

    public enum MessageType {
        CHAT, JOIN, LEAVE
    }

    private MessageType type;
    private String content;
    private String sender;
    private LocalDateTime timestamp;

    public ChatMessage() {
        this.timestamp = LocalDateTime.now();
    }

    public ChatMessage(MessageType type, String content, String sender) {
        this();
        this.type = type;
        this.content = content;
        this.sender = sender;
    }

    // Getters and Setters
    public MessageType getType() {
        return type;
    }

    public void setType(MessageType type) {
        this.type = type;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getSender() {
        return sender;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(LocalDateTime timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public String toString() {
        return "ChatMessage [type=" + type + ", content=" + content + ", sender=" + sender + ", timestamp=";
    }
}
