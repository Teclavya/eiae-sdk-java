package com.teclavya.eiae.sdk;

import java.util.UUID;

/**
 * Wire-format request DTO for {@code POST /api/v1/quota/consume-by-external-id}.
 * Mirrors EIAE server's {@code ConsumeByExternalIdRequest}.
 */
record ConsumeByExternalIdRequest(UUID externalUserId, String featureName, int amount, String idempotencyKey) {
}
