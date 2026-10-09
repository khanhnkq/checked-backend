package com.codegym.locketclone.common.config;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;

import static org.junit.jupiter.api.Assertions.*;

class CorsConfigTest {

    @Test
    void corsConfigurationSource_specificOrigins_enablesCredentials() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "http://localhost:3000,http://localhost:5173");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertNotNull(config);
        assertTrue(Boolean.TRUE.equals(config.getAllowCredentials()));
        assertEquals(2, config.getAllowedOriginPatterns().size());
        assertTrue(config.getAllowedOriginPatterns().contains("http://localhost:3000"));
        assertTrue(config.getAllowedOriginPatterns().contains("http://localhost:5173"));
    }

    @Test
    void corsConfigurationSource_wildcardOrigin_disablesCredentials() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", "*");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertNotNull(config);
        assertFalse(Boolean.TRUE.equals(config.getAllowCredentials()));
        assertEquals(1, config.getAllowedOriginPatterns().size());
        assertEquals("*", config.getAllowedOriginPatterns().get(0));
    }

    @Test
    void corsConfigurationSource_emptyOrigins_disablesCredentials() {
        CorsConfig corsConfig = new CorsConfig();
        ReflectionTestUtils.setField(corsConfig, "allowedOrigins", " , ");

        CorsConfigurationSource source = corsConfig.corsConfigurationSource();
        CorsConfiguration config = source.getCorsConfiguration(new org.springframework.mock.web.MockHttpServletRequest());

        assertNotNull(config);
        assertFalse(Boolean.TRUE.equals(config.getAllowCredentials()));
        assertEquals(1, config.getAllowedOriginPatterns().size());
        assertEquals("*", config.getAllowedOriginPatterns().get(0));
    }
}
