package com.hyeokjin.devflow.taskservice.api;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class DashboardResourceTest {

    @Test
    void dashboardStaticResourcesArePackaged() {
        ClassLoader loader = getClass().getClassLoader();

        assertNotNull(loader.getResource("static/index.html"));
        assertNotNull(loader.getResource("static/app.css"));
        assertNotNull(loader.getResource("static/app.js"));
    }
}
