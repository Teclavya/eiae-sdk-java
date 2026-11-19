package com.teclavya.eiae.sdk;

/**
 * Provider-side failure exception that triggers automatic quota compensation
 * in {@link RequiresQuotaAspect}.
 */
public class QuotaRefundableException extends RuntimeException implements QuotaRefundable {

    public QuotaRefundableException(String message) {
        super(message);
    }

    public QuotaRefundableException(String message, Throwable cause) {
        super(message, cause);
    }
}
