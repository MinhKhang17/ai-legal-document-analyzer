package com.analyzer.api.exception.payment;

import lombok.Getter;

// Carries a VNPay-contract RspCode so the vnpay-return/vnpay-ipn controller endpoints can
// always answer in the {RspCode, Message} shape VNPay's gateway expects, instead of letting
// GlobalExceptionHandler turn a callback failure into a generic ApiResponseDTO error body.
@Getter
public class VnPayCallbackException extends RuntimeException {

    private final String rspCode;

    public VnPayCallbackException(String rspCode, String message) {
        super(message);
        this.rspCode = rspCode;
    }
}
