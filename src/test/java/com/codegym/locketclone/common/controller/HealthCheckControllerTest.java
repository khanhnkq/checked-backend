package com.codegym.locketclone.common.controller;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class HealthCheckControllerTest {

    private HealthCheckController healthCheckController;

    @BeforeEach
    void setUp() {
        healthCheckController = new HealthCheckController();
    }

    @Test
    void health_returnsOkWithStatusUpAndTimestamp() {
        ResponseEntity<Map<String, Object>> response = healthCheckController.health();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNotNull(response.getBody());

        Map<String, Object> body = response.getBody();
        assertEquals("UP", body.get("status"));
        assertEquals("checked-backend", body.get("service"));
        assertTrue(body.containsKey("timestamp"));
        assertTrue((Long) body.get("timestamp") > 0);
    }

    @Test
    void ping_headRequest_returnsOkWithEmptyBody() {
        ResponseEntity<Void> response = healthCheckController.ping();

        assertNotNull(response);
        assertEquals(HttpStatus.OK, response.getStatusCode());
        assertNull(response.getBody());
    }
}
