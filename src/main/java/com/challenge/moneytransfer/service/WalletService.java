package com.challenge.moneytransfer.service;

import com.challenge.moneytransfer.dto.TransferRequest;
import com.challenge.moneytransfer.dto.TransactionResponse;
import com.challenge.moneytransfer.dto.WalletResponse;
import com.challenge.moneytransfer.exception.InsufficientFundsException;
import com.challenge.moneytransfer.exception.InvalidTransferException;
import com.challenge.moneytransfer.exception.ResourceNotFoundException;
import com.challenge.moneytransfer.model.Transaction;
import com.challenge.moneytransfer.model.TransactionStatus;
import com.challenge.moneytransfer.model.User;
import com.challenge.moneytransfer.model.Wallet;
import com.challenge.moneytransfer.repository.TransactionRepository;
import com.challenge.moneytransfer.repository.UserRepository;
import com.challenge.moneytransfer.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

@Service
public class WalletService {

    private final WalletRepository walletRepository;
    private final UserRepository userRepository;
    private final TransactionRepository transactionRepository;
    private final AuditService auditService;

    public WalletService(WalletRepository walletRepository,
                         UserRepository userRepository,
                         TransactionRepository transactionRepository,
                         AuditService auditService) {
        this.walletRepository = walletRepository;
        this.userRepository = userRepository;
        this.transactionRepository = transactionRepository;
        this.auditService = auditService;
    }

    @Transactional(readOnly = true)
    public WalletResponse getMyWallet(String username) {
        User user = getUser(username);
        Wallet wallet = walletRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        return toWalletResponse(wallet);
    }

    /**
     * Atomic money transfer.
     *
     * Concurrency strategy: both wallet rows are locked with PESSIMISTIC_WRITE
     * in a deterministic order (lower user id first) to avoid deadlocks.
     * All balance checks and mutations happen inside one DB transaction, so
     * either everything commits or nothing does — no partial transfers.
     */
    @Transactional
    public TransactionResponse transfer(String senderUsername, TransferRequest request) {
        User sender = getUser(senderUsername);
        User recipient = userRepository.findByUsername(request.recipientUsername())
                .orElseThrow(() -> new ResourceNotFoundException("Recipient not found"));

        validateTransfer(sender, recipient, request.amount());

        // Lock wallets in deterministic order to prevent deadlocks
        List<User> ordered = List.of(sender, recipient)
                .stream()
                .sorted(Comparator.comparing(User::getId))
                .toList();

        Wallet first = walletRepository.findByUserIdForUpdate(ordered.get(0).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));
        Wallet second = walletRepository.findByUserIdForUpdate(ordered.get(1).getId())
                .orElseThrow(() -> new ResourceNotFoundException("Wallet not found"));

        Wallet senderWallet = first.getUser().getId().equals(sender.getId()) ? first : second;
        Wallet recipientWallet = first.getUser().getId().equals(recipient.getId()) ? first : second;

        // Re-check balance AFTER acquiring the lock (authoritative check)
        if (senderWallet.getBalance().compareTo(request.amount()) < 0) {
            auditService.log("TRANSFER_FAILED",
                    "from=" + sender.getUsername() + " to=" + recipient.getUsername()
                            + " amount=" + request.amount() + " reason=insufficient_funds");
            throw new InsufficientFundsException("Insufficient funds");
        }

        senderWallet.setBalance(senderWallet.getBalance().subtract(request.amount()));
        recipientWallet.setBalance(recipientWallet.getBalance().add(request.amount()));
        walletRepository.save(senderWallet);
        walletRepository.save(recipientWallet);

        Transaction tx = recordTransaction(sender, recipient, request.amount(),
                TransactionStatus.COMPLETED, null);

        auditService.log("TRANSFER_COMPLETED",
                "ref=" + tx.getReferenceId()
                        + " from=" + sender.getUsername()
                        + " to=" + recipient.getUsername()
                        + " amount=" + request.amount());

        return toTransactionResponse(tx);
    }

    @Transactional(readOnly = true)
    public Page<TransactionResponse> getMyTransactions(String username, Pageable pageable) {
        User user = getUser(username);
        return transactionRepository
                .findByFromUserIdOrToUserIdOrderByCreatedAtDesc(user.getId(), user.getId(), pageable)
                .map(this::toTransactionResponse);
    }

    private void validateTransfer(User sender, User recipient, BigDecimal amount) {
        if (sender.getId().equals(recipient.getId())) {
            throw new InvalidTransferException("Cannot transfer to yourself");
        }
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidTransferException("Amount must be positive");
        }
        if (!recipient.isEnabled()) {
            throw new InvalidTransferException("Recipient account is disabled");
        }
    }

    private Transaction recordTransaction(User from, User to, BigDecimal amount,
                                          TransactionStatus status, String failureReason) {
        return transactionRepository.save(Transaction.builder()
                .fromUser(from)
                .toUser(to)
                .amount(amount)
                .status(status)
                .failureReason(failureReason)
                .build());
    }

    private User getUser(String username) {
        return userRepository.findByUsername(username)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));
    }

    private WalletResponse toWalletResponse(Wallet wallet) {
        return new WalletResponse(wallet.getUser().getId(), wallet.getUser().getUsername(), wallet.getBalance());
    }

    private TransactionResponse toTransactionResponse(Transaction tx) {
        return new TransactionResponse(
                tx.getId(),
                tx.getReferenceId(),
                tx.getFromUser().getUsername(),
                tx.getToUser().getUsername(),
                tx.getAmount(),
                tx.getStatus().name(),
                tx.getFailureReason(),
                tx.getCreatedAt());
    }
}
