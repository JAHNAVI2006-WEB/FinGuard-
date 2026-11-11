package com.challenge.moneytransfer;

import com.challenge.moneytransfer.model.Role;
import com.challenge.moneytransfer.model.User;
import com.challenge.moneytransfer.model.Wallet;
import com.challenge.moneytransfer.repository.UserRepository;
import com.challenge.moneytransfer.repository.WalletRepository;
import com.challenge.moneytransfer.security.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class ApiIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired UserRepository userRepository;
    @Autowired WalletRepository walletRepository;
    @Autowired PasswordEncoder passwordEncoder;
    @Autowired JwtService jwtService;

    private String tokenFor(String username, Role role) {
        return jwtService.generateToken(username, role.name());
    }

    private void ensureUser(String username, Role role, String balance) {
        if (userRepository.existsByUsername(username)) return;
        User u = userRepository.save(User.builder()
                .username(username).email(username + "@test.dev")
                .passwordHash(passwordEncoder.encode("pass12345"))
                .role(role).enabled(true).build());
        walletRepository.save(Wallet.builder().user(u).balance(new BigDecimal(balance)).build());
    }

    @Test
    void register_login_transfer_flow() throws Exception {
        // register
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","email":"carol@test.dev","password":"password123"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").exists())
                .andExpect(jsonPath("$.role").value("USER"));

        // duplicate register -> 409
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","email":"carol2@test.dev","password":"password123"}
                                """))
                .andExpect(status().isConflict());

        // login
        String loginResp = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"carol","password":"password123"}
                                """))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        String token = loginResp.replaceAll(".*\"token\":\"([^\"]+)\".*", "$1");

        // wallet starts at zero
        mockMvc.perform(get("/api/wallet/me")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.balance").value(0));

        // transfer with zero balance -> 422
        ensureUser("dave", Role.USER, "100.00");
        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUsername":"dave","amount":10.00}
                                """))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void unauthenticated_requests_rejected() throws Exception {
        mockMvc.perform(get("/api/wallet/me"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/admin/audit-logs"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void admin_endpoints_require_admin_role() throws Exception {
        ensureUser("regular", Role.USER, "0");
        String userToken = tokenFor("regular", Role.USER);

        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + userToken))
                .andExpect(status().isForbidden());

        ensureUser("boss", Role.ADMIN, "0");
        String adminToken = tokenFor("boss", Role.ADMIN);
        mockMvc.perform(get("/api/admin/audit-logs")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk());
    }

    @Test
    void invalid_input_returns_400_not_500() throws Exception {
        ensureUser("eve", Role.USER, "100.00");
        String token = tokenFor("eve", Role.USER);

        mockMvc.perform(post("/api/wallet/transfer")
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"recipientUsername":"","amount":-5}
                                """))
                .andExpect(status().isBadRequest());
    }
}
