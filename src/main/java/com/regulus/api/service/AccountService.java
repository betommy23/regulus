package com.regulus.api.service;

import com.regulus.api.exception.ApiException;
import com.regulus.api.model.Account;
import com.regulus.api.model.AccountSummary;
import com.regulus.api.repository.AccountRepository;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AccountService {
    private final AccountRepository repository;
    public AccountService(AccountRepository repository) { this.repository = repository; }

    public String database() { return repository.getDatabaseName(); }
    public List<AccountSummary> list(Integer firmId) {
        return firmId == null ? repository.findAllAccounts() : repository.findByFirmId(firmId);
    }

    @Transactional
    public Account create(Account account) {
        validateKey(account.getFirmId(), account.getAccountId());
        if (repository.findByFirmAndAccount(account.getFirmId(), account.getAccountId()) != null) {
            throw new ApiException(HttpStatus.CONFLICT, "Account already exists");
        }
        repository.insert(account);
        return account;
    }

    @Transactional
    public Account update(Integer firmId, String accountId, Account changes) {
        validateKey(firmId, accountId);
        Account account = repository.findByFirmAndAccount(firmId, accountId);
        if (account == null) throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        if (changes.getCorrespondent() != null) account.setCorrespondent(changes.getCorrespondent());
        if (changes.getBranch() != null) account.setBranch(changes.getBranch());
        if (changes.getRep() != null) account.setRep(changes.getRep());
        if (changes.getAccountName() != null) account.setAccountName(changes.getAccountName());
        if (changes.getAccountCat() != null) account.setAccountCat(changes.getAccountCat());
        if (changes.getMarginable() != null) account.setMarginable(changes.getMarginable());
        if (changes.getAggregate() != null) account.setAggregate(changes.getAggregate());
        if (changes.getTaxlotMethod() != null) account.setTaxlotMethod(changes.getTaxlotMethod());
        if (repository.update(firmId, accountId, account) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
        return account;
    }

    @Transactional
    public void delete(Integer firmId, String accountId) {
        validateKey(firmId, accountId);
        if (repository.delete(firmId, accountId) == 0) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Account not found");
        }
    }

    private void validateKey(Integer firmId, String accountId) {
        if (firmId == null) throw new ApiException(HttpStatus.BAD_REQUEST, "firmId is required");
        if (accountId == null || accountId.trim().isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "accountId is required");
        }
    }
}
