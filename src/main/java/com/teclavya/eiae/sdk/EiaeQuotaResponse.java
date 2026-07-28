package com.teclavya.eiae.sdk;

/**
 * Wire-format response DTO for {@code POST /api/v1/quota/consume-by-external-id}.
 * Mirrors EIAE server's {@code eiae.entitlements.QuotaResult}: {@code outcome} is
 * one of {@code ALLOWED}/{@code DENIED}/{@code IDEMPOTENT}.
 */
record EiaeQuotaResponse(String outcome, int consumed, int max, boolean idempotent) {
}
