package com.challenge.moneytransfer.dto;

import java.math.BigDecimal;

public record WalletResponse(Long userId, String username, BigDecimal balance) {}
