package com.regulus.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulus.api.service.LegacyAssetService;
import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import regulusbo.book.entity.cash;
import regulusbo.book.entity.trade;
import regulusbo.book.entity.stock;
import regulusbo.util.jsonUtil;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class VendorAssetApiTest {
    @Autowired MockMvc mvc;
    @Autowired LegacyAssetService service;
    @Autowired ObjectMapper mapper;
    @Autowired JdbcTemplate jdbc;

    private cash deposit(String accountId, String date) throws Exception {
        cash c = new cash(); c.setDeposit(1, accountId, 1, "USD", 100.0, "DEPOSIT", "Owner's deposit");
        c.setProcessDate(date); c.setTradeDate(date); c.setSettleDate(date); c.setAsofDate(date);
        return c;
    }

    @Test void cashPersistsVendorBookAndReturnsVendorDemoSchema() throws Exception {
        String json = jsonUtil.toJson(deposit("O'BRIEN", "2026-10-08"));
        mvc.perform(post("/api/cash").with(user("test").roles("API"))
                .contentType("application/json").content(json))
                .andExpect(status().isOk()).andExpect(jsonPath("$.account.accountId").value("O'BRIEN"))
                .andExpect(jsonPath("$.cashes[0].amount").value(100.0));
        mvc.perform(get("/api/asset").param("firmId", "1").param("accountId", "O'BRIEN")
                .with(user("test").roles("API"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.cashes[0].desc1").value("Owner's deposit"));
        mvc.perform(get("/api/asset").param("firmId", "1").param("accountId", "O'BRIEN")
                .param("processDate", "2026-10-08").with(user("test").roles("API")))
                .andExpect(status().isOk()).andExpect(jsonPath("$.balances[0].balance").value(100.0));
        assertEquals(1, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accountBook", Integer.class));
        assertTrue(jdbc.queryForObject("SELECT bookJson FROM dbo.accountBook", String.class).contains("acctProfile"));
    }

    @Test void dateRolloverPreservesEarlierSnapshotAndRejectsBackdating() throws Exception {
        service.cash(jsonUtil.toJson(deposit("ROLL", "2026-10-07")));
        service.cash(jsonUtil.toJson(deposit("ROLL", "2026-10-08")));
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accountBook", Integer.class));
        JsonNode old = mapper.readTree(service.asset(1, "ROLL", "2026-10-07"));
        assertEquals("2026-10-07", old.path("processDate").asText());
        mvc.perform(post("/api/cash").with(user("test").roles("API"))
                .contentType("application/json").content(jsonUtil.toJson(deposit("ROLL", "2026-10-06"))))
                .andExpect(status().isConflict());
        assertEquals(2, jdbc.queryForObject("SELECT COUNT(*) FROM dbo.accountBook", Integer.class));
    }

    @Test void tradeAndStockUseRealVendorClasses() throws Exception {
        trade t = new trade(); t.setAccount(1,"TRADE","","",""); t.setProcessDate("2026-10-08");
        t.setTradeDate("2026-10-08");t.setSettleDate("2026-10-09");
        t.setSec("EQUITY","NVDA","","NVIDIA");t.setInitQtyPrice(2.0,10.0);
        mvc.perform(post("/api/trade").with(user("test").roles("API"))
                .contentType("application/json").content(jsonUtil.toJson(t)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.trades[0].secId").value("NVDA"));
        stock s = new stock(); s.setAccount(1,"STOCK","","","");s.setProcessDate("2026-10-08");
        s.setTradeDate("2026-10-08");s.setSettleDate("2026-10-09");
        s.setSec("EQUITY","NVDA","","NVIDIA");s.setInitQtyPrice(3.0,10.0);s.location="99";
        mvc.perform(post("/api/stock").with(user("test").roles("API"))
                .contentType("application/json").content(jsonUtil.toJson(s)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.stocks[0].secId").value("NVDA"));
    }

    @Test void malformedStoredBookIsNotSilentlyReplaced() throws Exception {
        jdbc.update("INSERT INTO dbo.accountBook(firmId,accountId,processDate,bookJson) VALUES (?,?,?,?)",
                1,"BROKEN",Date.valueOf("2026-10-08"),"{}");
        mvc.perform(get("/api/asset").param("firmId","1").param("accountId","BROKEN")
                .with(user("test").roles("API"))).andExpect(status().isInternalServerError());
        assertEquals("{}",jdbc.queryForObject("SELECT bookJson FROM dbo.accountBook",String.class));
    }
}
