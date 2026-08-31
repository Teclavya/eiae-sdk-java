package com.teclavya.eiae.sdk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class EiaePropertiesTest {

    @Test
    @DisplayName("Default localOverride is false and default enforceEntitlements is true")
    void defaultValues() {
        EiaeProperties properties = new EiaeProperties();
        assertThat(properties.isLocalOverride()).isFalse();
        assertThat(properties.getEnforceEntitlements()).isTrue();
    }

    @Test
    @DisplayName("Setting enforceEntitlements=true sets localOverride=false")
    void setEnforceEntitlementsTrue() {
        EiaeProperties properties = new EiaeProperties();
        properties.setEnforceEntitlements(true);

        assertThat(properties.getEnforceEntitlements()).isTrue();
        assertThat(properties.isLocalOverride()).isFalse();
    }

    @Test
    @DisplayName("Setting enforceEntitlements=false sets localOverride=true")
    void setEnforceEntitlementsFalse() {
        EiaeProperties properties = new EiaeProperties();
        properties.setEnforceEntitlements(false);

        assertThat(properties.getEnforceEntitlements()).isFalse();
        assertThat(properties.isLocalOverride()).isTrue();
    }

    @Test
    @DisplayName("Explicit enforceEntitlements takes precedence over localOverride")
    void enforceEntitlementsPrecedence() {
        EiaeProperties properties = new EiaeProperties();
        properties.setLocalOverride(true);
        properties.setEnforceEntitlements(true);

        assertThat(properties.isLocalOverride()).isFalse();
        assertThat(properties.getEnforceEntitlements()).isTrue();
    }
}
