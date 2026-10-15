package com.regulus.api.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.regulus.api.exception.ApiException;
import com.regulus.api.model.AccountBook;
import com.regulus.api.repository.AccountBookRepository;
import java.sql.Date;
import java.time.LocalDate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import regulusbo.book.asset.accountAsset;
import regulusbo.book.base.trans;
import regulusbo.book.entity.cash;
import regulusbo.book.entity.trade;
import regulusbo.book.entity.stock;
import regulusbo.util.jsonUtil;

/** Vendor calculations with parameterized Spring persistence, not the vendor's static SQL utility. */
@Service
@Transactional(isolation = Isolation.SERIALIZABLE, rollbackFor = Exception.class)
public class RegulusBoAssetService implements LegacyAssetService {
    private static final Object ENGINE_LOCK = new Object();
    private final AccountBookRepository repository;
    private final ObjectMapper mapper;

    public RegulusBoAssetService(AccountBookRepository repository, ObjectMapper mapper) {
        this.repository = repository;
        this.mapper = mapper;
    }

    public String cash(String json) throws Exception { return process(json, cash.class); }
    public String trade(String json) throws Exception { return process(json, trade.class); }
    public String stock(String json) throws Exception { return process(json, stock.class); }

    private <T extends trans> String process(String json, Class<T> type) throws Exception {
        synchronized (ENGINE_LOCK) {
            JsonNode input = mapper.readTree(json);
            if (input == null || !input.isObject() || !input.path("account").isObject()
                    || !input.path("account").path("firmId").isIntegralNumber()
                    || !input.path("account").path("firmId").canConvertToInt()
                    || !input.path("account").path("accountId").isTextual()
                    || input.path("account").path("accountId").asText().trim().isEmpty()
                    || !input.path("processDate").isTextual()) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "account.firmId, account.accountId and processDate are required");
            }
            LocalDate.parse(input.get("processDate").asText());
            T transaction = jsonUtil.fromJson(json, type);
            if (transaction == null || transaction.account == null || transaction.processDate == null) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid financial transaction");
            }
            accountAsset book = load(transaction.account.firmId, transaction.account.accountId, null);
            if (book == null) {
                book = new accountAsset();
                book.setAcctId(transaction.account.firmId, transaction.account.accountId,
                        transaction.account.correspondent, transaction.account.branch, transaction.account.rep);
                book.startProcessDate(transaction.processDate);
            } else if (book.processDate.compareTo(transaction.processDate) < 0) {
                book.endProcessDate();
                book.startProcessDate(transaction.processDate);
            } else if (book.processDate.compareTo(transaction.processDate) > 0) {
                throw new ApiException(HttpStatus.CONFLICT, "Transaction processDate precedes the latest book");
            }
            if (transaction instanceof cash) book.addCash((cash) transaction);
            else if (transaction instanceof trade) book.addTrade((trade) transaction);
            else book.addStock((stock) transaction);
            String persisted = jsonUtil.toJson(book);
            if (persisted == null || !mapper.readTree(persisted).isObject()) {
                throw new IllegalStateException("Vendor did not serialize an account book");
            }
            repository.save(transaction.account.firmId, transaction.account.accountId,
                    Date.valueOf(input.get("processDate").asText()), persisted);
            return book.outDemoJson();
        }
    }

    public String asset(Integer firmId, String accountId, String processDate) throws Exception {
        synchronized (ENGINE_LOCK) {
            accountAsset book = load(firmId, accountId, processDate);
            if (book == null) throw new ApiException(HttpStatus.NOT_FOUND, "Account book not found");
            return book.outDemoJson();
        }
    }

    private accountAsset load(Integer firmId, String accountId, String processDate) throws Exception {
        AccountBook row = processDate == null ? repository.findLatest(firmId, accountId)
                : repository.findByDate(firmId, accountId, Date.valueOf(LocalDate.parse(processDate)));
        if (row == null) return null;
        if (row.getBookJson() == null || row.getBookJson().trim().isEmpty()) {
            throw new IllegalStateException("Stored account book is empty");
        }
        // Fail loudly on malformed data instead of allowing the vendor parser to return defaults.
        JsonNode stored = mapper.readTree(row.getBookJson());
        if (!stored.isObject() || !stored.path("account").isObject() || !stored.path("processDate").isTextual()) {
            throw new IllegalStateException("Stored account book has an incompatible schema");
        }
        accountAsset book = jsonUtil.fromJson(row.getBookJson(), accountAsset.class);
        if (book == null || book.account == null || !firmId.equals(book.account.firmId)
                || !accountId.trim().equals(book.account.accountId) || book.processDate == null) {
            throw new IllegalStateException("Stored account book identity is invalid");
        }
        return book;
    }
}
