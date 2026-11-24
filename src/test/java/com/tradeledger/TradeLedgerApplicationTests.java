package com.tradeledger;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest
class TradeLedgerApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
        // Verifies that the Spring context starts successfully
        // with Postgres, MongoDB, Redis, Flyway migrations, and all beans.
        assertTrue(true);
    }
}

