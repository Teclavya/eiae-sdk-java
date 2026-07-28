package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link EntitlementServiceImpl}'s {@code has()}/{@code getSnapshot}/cache
 * behavior (Mockito fluent-mock style against the SDK's own {@link RestClient} usage).
 *
 * <p>HTTP-contract tests for {@code consumeQuota} (path, headers, body, status mapping,
 * fail-open behavior) live in {@link EntitlementServiceImplConsumeQuotaContractTest},
 * which exercises a real {@link RestClient} bound to a {@code MockRestServiceServer} so
 * the exact wire contract (URI, X-Internal-Token, no Authorization header, request body
 * shape) is verified end-to-end rather than through mocked fluent-builder plumbing.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementServiceImpl — has()/getSnapshot/cache")
class EntitlementServiceImplTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> getSpec;

    @Mock
    private RestClient.ResponseSpec getResponseSpec;

    private EiaeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EiaeProperties();
        properties.setApiBase("http://eiae-test:8089");
        properties.setCacheTtlSeconds(60);
        properties.setInternalToken("test-token");
    }

    @SuppressWarnings("unchecked")
    private EiaeEntitlementsResponse buildResponse(List<String> features) {
        return new EiaeEntitlementsResponse(
                Set.copyOf(features),
                Map.of("mock_interviews", new EiaeEntitlementsResponse.EiaeQuotaState(2, 10, "2026-07")),
                List.of());
    }

    @Test
    @DisplayName("localOverride=true: has() returns true without HTTP call")
    void localOverride_hasReturnsTrue() {
        properties.setLocalOverride(true);
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isTrue();
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("localOverride=true: consumeQuota returns ALLOWED without HTTP call")
    void localOverride_consumeQuotaReturnsAllowed() {
        properties.setLocalOverride(true);
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        QuotaResult result = service.consumeQuota("user-1", "mock_interviews", 1, "key-123");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("enforceEntitlements=true overrides localOverride and enforces HTTP path")
    @SuppressWarnings("unchecked")
    void enforceEntitlementsTrue_overridesLocalOverride() {
        properties.setLocalOverride(true);
        properties.setEnforceEntitlements(true);
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EiaeEntitlementsResponse response = buildResponse(List.of());

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(response).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isFalse();
        verify(restClient, times(1)).get();
    }

    @Test
    @DisplayName("enforceEntitlements=false short-circuits even when localOverride=false")
    void enforceEntitlementsFalse_shortCircuits() {
        properties.setLocalOverride(false);
        properties.setEnforceEntitlements(false);
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isTrue();
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("has() returns true when feature is in snapshot")
    @SuppressWarnings("unchecked")
    void has_featurePresent_returnsTrue() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EiaeEntitlementsResponse response = buildResponse(List.of("mock_interviews", "ai_resume"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(response).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("has() returns false when feature is NOT in snapshot")
    @SuppressWarnings("unchecked")
    void has_featureAbsent_returnsFalse() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EiaeEntitlementsResponse response = buildResponse(List.of("ai_resume"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(response).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Cache hit: second call does not make another HTTP request")
    @SuppressWarnings("unchecked")
    void cacheHit_secondCallNoHttpRequest() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EiaeEntitlementsResponse response = buildResponse(List.of("mock_interviews"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(response).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        service.has("user-1", "mock_interviews");
        service.has("user-1", "mock_interviews");

        // HTTP should only be called once (cache hit on second call)
        verify(restClient, times(1)).get();
    }

    @Test
    @DisplayName("invalidateCache causes re-fetch on next has() call")
    @SuppressWarnings("unchecked")
    void invalidateCache_causesRefetch() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EiaeEntitlementsResponse response = buildResponse(List.of("mock_interviews"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(response).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        service.has("user-1", "mock_interviews");
        service.invalidateCache("user-1");
        service.has("user-1", "mock_interviews");

        // Should have fetched twice (once before invalidate, once after)
        verify(restClient, times(2)).get();
    }

    @Test
    @DisplayName("has() returns false when EIAE is unreachable (fail-safe empty snapshot)")
    @SuppressWarnings("unchecked")
    void has_eiaeUnreachable_returnsFalse() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doThrow(new RuntimeException("Connection refused")).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("consumeQuota: non-UUID userId fails OPEN when failOpenOnError=true (default)")
    void consumeQuota_nonUuidUserId_failOpenDefault() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        QuotaResult result = service.consumeQuota("not-a-uuid", "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isTrue();
        assertThat(result.outcome()).isEqualTo(QuotaResult.ALLOWED);
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("consumeQuota: non-UUID userId fails CLOSED when failOpenOnError=false")
    void consumeQuota_nonUuidUserId_failClosedWhenConfigured() {
        properties.setFailOpenOnError(false);
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        QuotaResult result = service.consumeQuota("not-a-uuid", "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
        verifyNoInteractions(restClient);
    }

    @Test
    @DisplayName("getSnapshot returns empty snapshot when EIAE returns null")
    @SuppressWarnings("unchecked")
    void getSnapshot_eiaeReturnsNull_returnsEmpty() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(null).when(getResponseSpec).body(EiaeEntitlementsResponse.class);

        EntitlementsSnapshot snapshot = service.getSnapshot("user-1");

        assertThat(snapshot.features()).isEmpty();
        assertThat(snapshot.quotas()).isEmpty();
        assertThat(snapshot.sourceOrgIds()).isEmpty();
    }
}
