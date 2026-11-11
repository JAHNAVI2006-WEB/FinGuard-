package com.challenge.moneytransfer.repository;

import com.challenge.moneytransfer.model.Transaction;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TransactionRepository extends JpaRepository<Transaction, Long> {

    Page<Transaction> findByFromUserIdOrToUserIdOrderByCreatedAtDesc(
            Long fromUserId, Long toUserId, Pageable pageable);
}
