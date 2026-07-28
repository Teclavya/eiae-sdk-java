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

/**
 * Wire-contract tests for {@link EntitlementServiceImpl#consumeQuota}: exercises the
 * REAL {@link RestClient} built the same way the production 1-arg constructor builds
 * it (via {@link EntitlementServiceImpl#restClientBuilder(EiaeProperties)}), bound to a
 * {@link MockRestServiceServer}, so URI/headers/body/status-mapping are verified against
 * the actual wire contract with {@code POST /api/v1/quota/consume-by-external-id}.
 */
@DisplayName("EntitlementServiceImpl.consumeQuota — HTTP contract")
class EntitlementServiceImplConsumeQuotaContractTest {

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
    @DisplayName("200/ALLOWED: request hits consume-by-external-id, sends X-Internal-Token, no Authorization, correct body")
    void allowed_hitsCorrectEndpointWithCorrectHeadersAndBody() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Internal-Token", "test-token"))
                .andExpect(request -> assertThat(request.getHeaders().containsKey("Authorization")).isFalse())
                .andExpect(jsonPath("$.externalUserId").value(UUID_STR))
                .andExpect(jsonPath("$.featureName").value("mock_interviews"))
                .andExpect(jsonPath("$.amount").value(1))
                .andExpect(jsonPath("$.idempotencyKey").value("key-abc"))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"outcome\":\"ALLOWED\",\"consumed\":3,\"max\":10,\"idempotent\":false}"));

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        assertThat(result.newConsumed()).isEqualTo(3);
        assertThat(result.max()).isEqualTo(10);
        server.verify();
    }

    @Test
    @DisplayName("200/IDEMPOTENT: maps to ALLOWED (a replay must NOT be treated as denied)")
    void idempotent_mapsToAllowed() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.OK)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"outcome\":\"IDEMPOTENT\",\"consumed\":3,\"max\":10,\"idempotent\":true}"));

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        server.verify();
    }

    @Test
    @DisplayName("402/DENIED: maps to a clean DENIED QuotaResult (does not throw)")
    void denied_mapsToDeniedWithoutThrowing() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.PAYMENT_REQUIRED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"outcome\":\"DENIED\",\"consumed\":10,\"max\":10,\"idempotent\":false}"));

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
        assertThat(result.newConsumed()).isEqualTo(10);
        assertThat(result.max()).isEqualTo(10);
        server.verify();
    }

    @Test
    @DisplayName("EIAE 500 with failOpenOnError=true (default): fails OPEN (ALLOWED)")
    void serverError_failsOpenByDefault() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"boom\"}"));

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        server.verify();
    }

    @Test
    @DisplayName("EIAE 500 with failOpenOnError=false: fails CLOSED (DENIED)")
    void serverError_failsClosedWhenConfigured() {
        properties.setFailOpenOnError(false);

        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.INTERNAL_SERVER_ERROR)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\":\"boom\"}"));

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
        server.verify();
    }

    @Test
    @DisplayName("Connection failure (server unreachable) with failOpenOnError=true: fails OPEN (ALLOWED)")
    void connectionFailure_failsOpenByDefault() {
        server.expect(requestTo("http://eiae-test:8089/api/v1/quota/consume-by-external-id"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new java.io.IOException("Connection refused");
                });

        QuotaResult result = service.consumeQuota(UUID_STR, "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        server.verify();
    }
}
