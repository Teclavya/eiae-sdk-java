package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

@DisplayName("EntitlementServiceImpl.refundQuota — HTTP contract")
class EntitlementServiceImplRefundContractTest {

    private static final String UUID_STR = "3f6b8e2e-6b9c-4b1e-9f0a-8f2a6c1d4e10";

    private EiaeProperties properties;
    private MockRestServiceServer server;
    private EntitlementServiceImpl service;

    @BeforeEach
    void setUp() {
        properties = new EiaeProperties();
        properties.setApiBase("http://eiae-test:8089");
        properties.setInternalToken("test-token");
        properties.setCacheTtlSeconds(60);

        RestClient.Builder builder = EntitlementServiceImpl.restClientBuilder(properties);
        server = MockRestServiceServer.bindTo(builder).build();
        service = new EntitlementServiceImpl(properties, builder.build());
    }

    @Test
    @DisplayName("REQ-02: 200 hits refund-by-external-id with X-Internal-Token and invalidates cache")
    void refund200_hitsCorrectEndpointAndInvalidatesCache() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/entitlements/by-external-id/" + UUID_STR))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"features\":[\"learning_path.propose_template\"],\"quotas\":{},\"sourceOrgs\":[]}"));

        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/refund-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(header("X-Idempotency-Key", "idem-refund-1"))
                .andExpect(jsonPath("$.externalUserId").value(UUID_STR))
                .andExpect(jsonPath("$.featureName").value("learning_path.propose_template"))
                .andExpect(jsonPath("$.amount").value(1))
                .andExpect(jsonPath("$.idempotencyKey").value("idem-refund-1"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"outcome\":\"ALLOWED\",\"consumed\":2,\"max\":10,\"idempotent\":false}"));

        // Cache was invalidated so next getSnapshot hits EIAE again
        server.expect(requestTo("http://eiae-test:8089/api/v1/entitlements/by-external-id/" + UUID_STR))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"features\":[\"learning_path.propose_template\"],\"quotas\":{},\"sourceOrgs\":[]}"));

        service.getSnapshot(UUID_STR); // populate cache
        QuotaResult result = service.refundQuota(UUID_STR, "learning_path.propose_template", 1, "idem-refund-1");
        assertThat(result.isAllowed()).isTrue();
        assertThat(result.newConsumed()).isEqualTo(2);
        service.getSnapshot(UUID_STR);
        server.verify();
    }

    @Test
    @DisplayName("REQ-02/REQ-04: 500 from EIAE returns DENIED without throwing")
    void refund500_returnsDeniedGracefully() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/refund-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR));

        QuotaResult result = service.refundQuota(UUID_STR, "learning_path.propose_template", 1, "idem-refund-2");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
        server.verify();
    }
}
