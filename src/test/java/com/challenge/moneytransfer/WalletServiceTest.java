package com.challenge.moneytransfer;

import com.challenge.moneytransfer.dto.TransferRequest;
import com.challenge.moneytransfer.exception.InsufficientFundsException;
import com.challenge.moneytransfer.exception.InvalidTransferException;
import com.challenge.moneytransfer.model.Role;
import com.challenge.moneytransfer.model.User;
import com.challenge.moneytransfer.model.Wallet;
import com.challenge.moneytransfer.repository.TransactionRepository;
import com.challenge.moneytransfer.repository.UserRepository;
import com.challenge.moneytransfer.repository.WalletRepository;
import com.challenge.moneytransfer.service.AuditService;
import com.challenge.moneytransfer.service.WalletService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WalletServiceTest {

    @Mock UserRepository userRepository;
    @Mock WalletRepository walletRepository;
    @Mock TransactionRepository transactionRepository;
    @Mock AuditService auditService;

    @InjectMocks WalletService walletService;

    private User alice;
    private User bob;
    private Wallet aliceWallet;
    private Wallet bobWallet;

    @BeforeEach
    void setUp() {
        alice = User.builder().id(1L).username("alice").email("a@x.com")
                .passwordHash("x").role(Role.USER).enabled(true).build();
        bob = User.builder().id(2L).username("bob").email("b@x.com")
                .passwordHash("x").role(Role.USER).enabled(true).build();
        aliceWallet = Wallet.builder().id(1L).user(alice).balance(new BigDecimal("100.00")).build();
        bobWallet = Wallet.builder().id(2L).user(bob).balance(new BigDecimal("50.00")).build();
    }

    @Test
    void transfer_success_debitsSenderCreditsRecipient() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        // deterministic lock order: alice(id1) first, bob(id2) second
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(bobWallet));
        when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        walletService.transfer("alice", new TransferRequest("bob", new BigDecimal("30.00")));

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("70.00");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("80.00");
        verify(walletRepository, times(2)).save(any());
        verify(transactionRepository).save(any());
        verify(auditService).log(eq("TRANSFER_COMPLETED"), anyString());
    }

    @Test
    void transfer_insufficientFunds_throwsAndDoesNotMutate() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));
        when(walletRepository.findByUserIdForUpdate(1L)).thenReturn(Optional.of(aliceWallet));
        when(walletRepository.findByUserIdForUpdate(2L)).thenReturn(Optional.of(bobWallet));

        assertThatThrownBy(() ->
                walletService.transfer("alice", new TransferRequest("bob", new BigDecimal("500.00"))))
                .isInstanceOf(InsufficientFundsException.class);

        assertThat(aliceWallet.getBalance()).isEqualByComparingTo("100.00");
        assertThat(bobWallet.getBalance()).isEqualByComparingTo("50.00");
        verify(transactionRepository, never()).save(any());
        verify(auditService).log(eq("TRANSFER_FAILED"), anyString());
    }

    @Test
    void transfer_toSelf_rejected() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));

        assertThatThrownBy(() ->
                walletService.transfer("alice", new TransferRequest("alice", new BigDecimal("10.00"))))
                .isInstanceOf(InvalidTransferException.class);
    }

    @Test
    void transfer_zeroAmount_rejected() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(alice));
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(bob));

        assertThatThrownBy(() ->
                walletService.transfer("alice", new TransferRequest("bob", BigDecimal.ZERO)))
                .isInstanceOf(InvalidTransferException.class);
    }
}
