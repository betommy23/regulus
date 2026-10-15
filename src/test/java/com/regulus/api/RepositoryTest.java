package com.regulus.api;

import com.regulus.api.model.*;
import com.regulus.api.repository.*;
import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("local")
@Transactional
class RepositoryTest {
    @Autowired AccountBookRepository books;
    @Autowired AccountRepository accounts;
    @Autowired JdbcTemplate jdbc;

    @Test void bookUpsertLatestAndDateQueries() {
        assertNull(books.findLatest(1, "ABC"));
        assertNull(books.findByDate(1, "ABC", Date.valueOf("2026-10-07")));
        books.save(1, "ABC", Date.valueOf("2026-10-07"), "{\"value\":1}");
        books.save(1, "ABC", Date.valueOf("2026-10-08"), "{\"value\":2}");
        books.save(1, "ABC", Date.valueOf("2026-10-08"), "{\"value\":3}");
        AccountBook latest = books.findLatest(1, "ABC");
        assertEquals("{\"value\":3}", latest.getBookJson());
        assertEquals(1, latest.getFirmId()); assertEquals("ABC", latest.getAccountId());
        assertTrue(latest.getAccountBookID() > 0);
        assertEquals(Date.valueOf("2026-10-08"), latest.getProcessDate());
        assertEquals("{\"value\":1}", books.findByDate(1, "ABC", Date.valueOf("2026-10-07")).getBookJson());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accountBook", Integer.class));
    }
    @Test void nullableAccountFieldsAndFirmIsolation() {
        Account a = new Account(); a.setFirmId(1); a.setAccountId("A"); accounts.insert(a);
        Account b = new Account(); b.setFirmId(2); b.setAccountId("A"); b.setAccountName("Other"); accounts.insert(b);
        Account loaded = accounts.findByFirmAndAccount(1, "A");
        assertNull(loaded.getMarginable()); assertNull(loaded.getAggregate());
        assertNull(loaded.getCorrespondent()); assertEquals(2, accounts.findAllAccounts().size());
        assertEquals(1, accounts.findByFirmId(2).size());
        assertEquals("Other", accounts.findByFirmId(2).get(0).getAccountName());
        assertEquals(1, accounts.delete(1, "A"));
        assertNotNull(accounts.findByFirmAndAccount(2, "A"));
    }
}
