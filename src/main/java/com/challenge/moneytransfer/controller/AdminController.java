package com.challenge.moneytransfer.controller;

import com.challenge.moneytransfer.dto.AuditLogResponse;
import com.challenge.moneytransfer.model.AuditLog;
import com.challenge.moneytransfer.repository.AuditLogRepository;
import com.challenge.moneytransfer.repository.UserRepository;
import com.challenge.moneytransfer.repository.WalletRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/admin")
public class AdminController {

    private final AuditLogRepository auditLogRepository;
    private final UserRepository userRepository;
    private final WalletRepository walletRepository;

    public AdminController(AuditLogRepository auditLogRepository,
                           UserRepository userRepository,
                           WalletRepository walletRepository) {
        this.auditLogRepository = auditLogRepository;
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
    }

    @GetMapping("/audit-logs")
    public ResponseEntity<Page<AuditLogResponse>> auditLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size) {
        int safeSize = Math.min(Math.max(size, 1), 200);
        PageRequest pageable = PageRequest.of(Math.max(page, 0), safeSize,
                Sort.by(Sort.Direction.DESC, "createdAt"));
        return ResponseEntity.ok(auditLogRepository.findAllByOrderByCreatedAtDesc(pageable)
                .map(this::toResponse));
    }

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(Map.of(
                "totalUsers", userRepository.count(),
                "totalWallets", walletRepository.count(),
                "totalAuditEvents", auditLogRepository.count()
        ));
    }

    private AuditLogResponse toResponse(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getActorUsername(), log.getAction(),
                log.getDetails(), log.getIpAddress(), log.getCreatedAt());
    }
}
