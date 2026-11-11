package com.challenge.moneytransfer.config;

import com.challenge.moneytransfer.model.Role;
import com.challenge.moneytransfer.model.User;
import com.challenge.moneytransfer.model.Wallet;
import com.challenge.moneytransfer.repository.UserRepository;
import com.challenge.moneytransfer.repository.WalletRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Seeds an admin account and two demo users with starting balances.
 * Demo credentials are for evaluation only — change them via env vars.
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UserRepository userRepository;
    private final WalletRepository walletRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${app.seed.admin-password:admin123}")
    private String adminPassword;
    @Value("${app.seed.demo-password:demo123}")
    private String demoPassword;

    public DataSeeder(UserRepository userRepository,
                      WalletRepository walletRepository,
                      PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.walletRepository = walletRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        seedUser("admin", "admin@challenge.dev", Role.ADMIN, adminPassword, new BigDecimal("10000.00"));
        seedUser("alice", "alice@challenge.dev", Role.USER, demoPassword, new BigDecimal("5000.00"));
        seedUser("bob", "bob@challenge.dev", Role.USER, demoPassword, new BigDecimal("2500.00"));
        log.info("Seed data ready. Admin: admin/{} | Demo users: alice/{}, bob/{}", adminPassword, demoPassword, demoPassword);
    }

    private void seedUser(String username, String email, Role role, String rawPassword, BigDecimal balance) {
        if (userRepository.existsByUsername(username)) {
            return;
        }
        User user = userRepository.save(User.builder()
                .username(username)
                .email(email)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .role(role)
                .enabled(true)
                .build());
        walletRepository.save(Wallet.builder().user(user).balance(balance).build());
    }
}
