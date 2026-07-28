package com.teclavya.eiae.sdk;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Wire-format response DTO for {@code GET /api/v1/entitlements/by-external-id/{id}}.
 * Mirrors EIAE server's {@code eiae.entitlements.EntitlementsResult}.
 */
record EiaeEntitlementsResponse(Set<String> features, Map<String, EiaeQuotaState> quotas,
                                 List<EiaeOrgContribution> sourceOrgs) {

    record EiaeQuotaState(int consumed, int max, String periodKey) {
    }

    record EiaeOrgContribution(Long orgId, Set<String> contributingFeatures) {
    }
}
