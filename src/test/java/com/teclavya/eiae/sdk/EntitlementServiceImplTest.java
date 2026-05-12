package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("EntitlementServiceImpl")
class EntitlementServiceImplTest {

    @Mock
    private RestClient restClient;

    @Mock
    private RestClient.RequestHeadersUriSpec<?> getSpec;

    @Mock
    private RestClient.RequestBodyUriSpec postSpec;

    @Mock
    private RestClient.RequestBodySpec requestBodySpec;

    @Mock
    private RestClient.ResponseSpec getResponseSpec;

    @Mock
    private RestClient.ResponseSpec postResponseSpec;

    private EiaeProperties properties;

    @BeforeEach
    void setUp() {
        properties = new EiaeProperties();
        properties.setApiBase("http://eiae-test:8089");
        properties.setCacheTtlSeconds(60);
        properties.setInternalToken("test-token");
    }

    @SuppressWarnings("unchecked")
    private EntitlementsSnapshot buildSnapshot(List<String> features) {
        return new EntitlementsSnapshot(
                features,
                Map.of("mock_interviews", new EntitlementsSnapshot.QuotaInfo(2, 10, Instant.now().plusSeconds(3600))),
                List.of(),
                Instant.now()
        );
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
        EntitlementsSnapshot snapshot = buildSnapshot(List.of());

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(snapshot).when(getResponseSpec).body(EntitlementsSnapshot.class);

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
        EntitlementsSnapshot snapshot = buildSnapshot(List.of("mock_interviews", "ai_resume"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(snapshot).when(getResponseSpec).body(EntitlementsSnapshot.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("has() returns false when feature is NOT in snapshot")
    @SuppressWarnings("unchecked")
    void has_featureAbsent_returnsFalse() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EntitlementsSnapshot snapshot = buildSnapshot(List.of("ai_resume"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(snapshot).when(getResponseSpec).body(EntitlementsSnapshot.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("Cache hit: second call does not make another HTTP request")
    @SuppressWarnings("unchecked")
    void cacheHit_secondCallNoHttpRequest() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        EntitlementsSnapshot snapshot = buildSnapshot(List.of("mock_interviews"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(snapshot).when(getResponseSpec).body(EntitlementsSnapshot.class);

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
        EntitlementsSnapshot snapshot = buildSnapshot(List.of("mock_interviews"));

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(snapshot).when(getResponseSpec).body(EntitlementsSnapshot.class);

        service.has("user-1", "mock_interviews");
        service.invalidateCache("user-1");
        service.has("user-1", "mock_interviews");

        // Should have fetched twice (once before invalidate, once after)
        verify(restClient, times(2)).get();
    }

    @Test
    @DisplayName("has() returns false when EIAE is unreachable (fail-safe)")
    @SuppressWarnings("unchecked")
    void has_eiaeUnreachable_returnsFalse() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doThrow(new RuntimeException("Connection refused")).when(getResponseSpec).body(EntitlementsSnapshot.class);

        boolean result = service.has("user-1", "mock_interviews");

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("consumeQuota returns DENIED when EIAE returns DENIED")
    @SuppressWarnings("unchecked")
    void consumeQuota_denied() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);
        QuotaResult denied = new QuotaResult(QuotaResult.DENIED, 10, 10, Instant.now().plusSeconds(3600));

        doReturn(postSpec).when(restClient).post();
        doReturn(requestBodySpec).when(postSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).body(any());
        doReturn(postResponseSpec).when(requestBodySpec).retrieve();
        doReturn(denied).when(postResponseSpec).body(QuotaResult.class);

        // Stub get for invalidateCache triggered inside consumeQuota
        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(buildSnapshot(List.of())).when(getResponseSpec).body(EntitlementsSnapshot.class);

        QuotaResult result = service.consumeQuota("user-1", "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
    }

    @Test
    @DisplayName("consumeQuota returns DENIED when EIAE is unreachable (fail-safe)")
    @SuppressWarnings("unchecked")
    void consumeQuota_eiaeUnreachable_returnsDenied() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        doReturn(postSpec).when(restClient).post();
        doReturn(requestBodySpec).when(postSpec).uri(anyString());
        doReturn(requestBodySpec).when(requestBodySpec).header(anyString(), anyString());
        doReturn(requestBodySpec).when(requestBodySpec).body(any());
        doReturn(postResponseSpec).when(requestBodySpec).retrieve();
        doThrow(new RuntimeException("Connection refused")).when(postResponseSpec).body(QuotaResult.class);

        QuotaResult result = service.consumeQuota("user-1", "mock_interviews", 1, "key-abc");

        assertThat(result.isAllowed()).isFalse();
        assertThat(result.outcome()).isEqualTo(QuotaResult.DENIED);
    }

    @Test
    @DisplayName("getSnapshot returns empty snapshot when EIAE returns null")
    @SuppressWarnings("unchecked")
    void getSnapshot_eiaeReturnsNull_returnsEmpty() {
        EntitlementServiceImpl service = new EntitlementServiceImpl(properties, restClient);

        doReturn(getSpec).when(restClient).get();
        doReturn(getSpec).when(getSpec).uri(anyString(), anyString());
        doReturn(getResponseSpec).when((RestClient.RequestHeadersSpec<?>) getSpec).retrieve();
        doReturn(null).when(getResponseSpec).body(EntitlementsSnapshot.class);

        EntitlementsSnapshot snapshot = service.getSnapshot("user-1");

        assertThat(snapshot.features()).isEmpty();
        assertThat(snapshot.quotas()).isEmpty();
        assertThat(snapshot.sourceOrgIds()).isEmpty();
    }
}
