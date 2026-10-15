package com.regulus.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulus.api.model.*;
import com.regulus.api.repository.AccountBookRepository;
import java.math.BigDecimal;
import java.sql.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Tests the supplied, currently unexposed local engine, not regulusbo. */
class AccountAssetTest {
    private Cash cash(String type, String amount) {
        Cash c = new Cash(); c.setFirmId(1); c.setAccountId("ABC");
        c.setTransactionType(type); c.setAmount(new BigDecimal(amount)); return c;
    }
    private Trade trade(String side) {
        Trade t = new Trade(); t.setFirmId(1); t.setAccountId("ABC");
        t.setBuySell(side); t.setSecurityId("NVDA"); t.setQuantity(new BigDecimal("2"));
        t.setPrice(new BigDecimal("10")); t.setSecurityType("EQUITY"); t.setDescription("Nvidia"); return t;
    }
    private Stock stock(String type) {
        Stock s = new Stock(); s.setFirmId(1); s.setAccountId("ABC");
        s.setTransactionType(type); s.setSecurityId("NVDA"); s.setQuantity(new BigDecimal("3"));
        s.setPrice(new BigDecimal("10")); return s;
    }
    @ParameterizedTest @ValueSource(strings={"WITHDRAWAL", "WITHDRAW", " debit "})
    void withdrawalsReduceCash(String type) throws Exception {
        AccountAsset a = new AccountAsset(); a.addCash(cash("DEPOSIT", "100")); a.addCash(cash(type, "20"));
        assertEquals(new BigDecimal("80"), a.getCashBalance()); assertEquals(2, a.getCashes().size());
    }
    @ParameterizedTest @ValueSource(strings={"BUY", " b "})
    void buysAndSellsUpdateCashAndPositions(String side) throws Exception {
        AccountAsset a = new AccountAsset(); a.addCash(cash("DEPOSIT", "100")); a.addTrade(trade(side));
        assertEquals(new BigDecimal("80"), a.getCashBalance());
        assertEquals(new BigDecimal("20"), a.getPositions().get("NVDA").getMarketValue());
        Trade sell = trade("SELL"); sell.setAmount(new BigDecimal("25")); a.addTrade(sell);
        assertEquals(new BigDecimal("105"), a.getCashBalance());
        assertEquals(BigDecimal.ZERO, a.getPositions().get("NVDA").getQuantity());
        a.addTrade(trade("S")); assertEquals(3, a.getTrades().size());
    }
    @ParameterizedTest @ValueSource(strings={"RECEIVE", "RECEIPT", " in "})
    void stockReceiptsAndDeliveries(String type) throws Exception {
        AccountAsset a = new AccountAsset(); a.addStock(stock(type));
        assertEquals(new BigDecimal("3"), a.getPositions().get("NVDA").getQuantity());
        for (String out : new String[]{"DELIVER", "DELIVERY", "OUT"}) a.addStock(stock(out));
        assertEquals(new BigDecimal("-6"), a.getPositions().get("NVDA").getQuantity());
        assertEquals(BigDecimal.ZERO, a.getCashBalance()); assertEquals(4, a.getStocks().size());
    }
    @Test void rejectsMissingAndMismatchedIdentityAndInvalidSides() throws Exception {
        AccountAsset a = new AccountAsset();
        assertThrows(Exception.class, () -> a.addCash(null));
        assertThrows(Exception.class, () -> a.addTrade(null));
        assertThrows(Exception.class, () -> a.addStock(null));
        Cash c = cash("DEPOSIT", "1"); c.setFirmId(null); assertThrows(Exception.class, () -> a.addCash(c));
        c.setFirmId(1); c.setAccountId(" "); assertThrows(Exception.class, () -> a.addCash(c));
        a.addCash(cash("DEPOSIT", "1"));
        c.setFirmId(2); c.setAccountId("ABC"); assertThrows(Exception.class, () -> a.addCash(c));
        c.setFirmId(1); c.setAccountId("OTHER"); assertThrows(Exception.class, () -> a.addCash(c));
        Trade t = trade("INVALID"); assertThrows(Exception.class, () -> a.addTrade(t));
        t.setSecurityId(null); assertThrows(Exception.class, () -> a.addTrade(t));
        Stock s = stock("INVALID"); assertThrows(Exception.class, () -> a.addStock(s));
        s.setSecurityId(""); assertThrows(Exception.class, () -> a.addStock(s));
    }
    @Test void bookLoadingSavingAndNullDefaults() throws Exception {
        AccountBookRepository repo = mock(AccountBookRepository.class);
        AccountAsset a = new AccountAsset(repo);
        a.loadBookSQL(1, "ABC", "2026-10-08");
        assertEquals(Date.valueOf("2026-10-08"), a.getProcessDate());
        a.addCash(cash("DEPOSIT", "50")); a.saveBookSQL();
        verify(repo).save(eq(1), eq("ABC"), eq(Date.valueOf("2026-10-08")), contains("50"));
        AccountBook book = new AccountBook();
        book.setBookJson(new ObjectMapper().writeValueAsString(a));
        when(repo.findLatest(1, "ABC")).thenReturn(book);
        AccountAsset loaded = new AccountAsset(repo); loaded.loadBookSQL(1, "ABC", null);
        assertEquals(new BigDecimal("50"), loaded.getCashBalance());
        assertEquals(1, loaded.getCashes().size());
        book.setBookJson("{\"firmId\":1,\"accountId\":\"ABC\",\"cashBalance\":null,\"cashes\":null,\"stocks\":null,\"trades\":null,\"positions\":null}");
        loaded.loadBookSQL(1, "ABC", "");
        assertEquals(BigDecimal.ZERO, loaded.getCashBalance());
        assertTrue(loaded.getStocks().isEmpty()); assertTrue(loaded.getPositions().isEmpty());
        loaded.setProcessDate(null); loaded.saveBookSQL();
        assertNotNull(loaded.getProcessDate());
        book.setBookJson(" "); assertThrows(Exception.class, () -> loaded.loadBookSQL(1, "ABC", null));
        book.setBookJson("{"); assertThrows(Exception.class, () -> loaded.loadBookSQL(1, "ABC", null));
        assertThrows(Exception.class, () -> new AccountAsset().saveBookSQL());
        assertThrows(Exception.class, () -> a.loadBookSQL(null, "ABC", null));
        assertThrows(Exception.class, () -> a.loadBookSQL(1, null, null));
        assertThrows(Exception.class, () -> a.loadBookSQL(1, "ABC", "invalid"));
        AccountAsset fresh = new AccountAsset(); fresh.setRepository(repo);
        fresh.loadBookSQL(2, "NEW", null); assertEquals("NEW", fresh.getAccountId());
    }
    @Test void positionNullsAndJsonRoundTrip() throws Exception {
        ObjectMapper mapper = new ObjectMapper();
        AccountAsset a = new AccountAsset();
        a.addTrade(trade("BUY")); a.addStock(stock("IN"));
        String json = mapper.writeValueAsString(a);
        assertFalse(json.contains("repository"));
        AccountAsset copy = mapper.readValue(json, AccountAsset.class);
        assertEquals(new BigDecimal("5"), copy.getPositions().get("NVDA").getQuantity());
        assertEquals("EQUITY", copy.getPositions().get("NVDA").getSecurityType());
        Position p = new Position(); p.setQuantity(null); p.setMarketPrice(null);
        assertEquals(BigDecimal.ZERO, p.getMarketValue());
    }
}
