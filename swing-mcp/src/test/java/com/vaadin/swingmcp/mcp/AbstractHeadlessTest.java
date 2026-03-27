package com.vaadin.swingmcp.mcp;

import org.junit.jupiter.api.BeforeAll;

public abstract class AbstractHeadlessTest {
    @BeforeAll
    public static void enableHeadless() {
        System.setProperty("java.awt.headless", "true");
    }
}
