package com.baseball.admin.domain;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;

@Entity
@Table(name = "faq", schema = "chatbot_schema")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Faq {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long faqId;

    private String category;
    private String question;
    private String answer;
    private Boolean enabled;

    @CreationTimestamp
    @Column(updatable = false)
    private LocalDateTime createdAt;

    @Builder
    private Faq(String category, String question, String answer, Boolean enabled) {
        this.category = category;
        this.question = question;
        this.answer = answer;
        this.enabled = enabled;
    }
}
