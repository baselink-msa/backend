package com.baseball.admin.repository;

import com.baseball.admin.domain.Faq;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FaqRepository extends JpaRepository<Faq, Long> {
}
