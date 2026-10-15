package com.regulus.api;

import com.regulus.api.repository.AccountBookRepository;
import com.regulus.api.service.LegacyAssetService;
import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import regulusbo.book.entity.cash;
import regulusbo.util.jsonUtil;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

// No enclosing test transaction: assertions observe the real service commit/rollback boundary.
@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:rollback;MODE=MSSQLServer;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=false")
@ActiveProfiles("local")
class FinancialTransactionTest {
    @Autowired LegacyAssetService service;
    @Autowired JdbcTemplate jdbc;
    @SpyBean AccountBookRepository repository;

    @Test void failureAfterWriteRollsBackTheServiceTransaction() throws Exception {
        cash c = new cash(); c.setDeposit(1, "ROLLBACK", 1, "USD", 100.0, "DEPOSIT", "test");
        c.setProcessDate("2026-10-08");
        doAnswer(call -> {
            call.callRealMethod();
            throw new IllegalStateException("simulated failure after database write");
        }).when(repository).save(anyInt(), anyString(), any(Date.class), anyString());
        assertThrows(IllegalStateException.class, () -> service.cash(jsonUtil.toJson(c)));
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accountBook", Integer.class));
    }
}
