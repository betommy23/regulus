package com.regulus.api.controller;

import com.regulus.api.model.Account;
import com.regulus.api.model.AccountSummary;
import com.regulus.api.service.AccountService;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
public class AccountController {
    private final AccountService service;
    public AccountController(AccountService service) { this.service = service; }

    @GetMapping(value = "/database", produces = "text/plain")
    public String database() { return service.database(); }

    @GetMapping("/accounts")
    public List<AccountSummary> accounts(@RequestParam(required = false) Integer firmId) {
        return service.list(firmId);
    }

    @PostMapping(value = "/account/edit", consumes = "application/json")
    @ResponseStatus(HttpStatus.CREATED)
    public Account create(@RequestBody Account account) { return service.create(account); }

    @PatchMapping(value = "/account/edit", consumes = "application/json")
    public Account update(@RequestParam Integer firmId, @RequestParam String accountId,
                          @RequestBody Account changes) {
        return service.update(firmId, accountId, changes);
    }

    @DeleteMapping("/account/edit")
    public Map<String, Object> delete(@RequestParam Integer firmId, @RequestParam String accountId) {
        service.delete(firmId, accountId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("message", "Account deleted successfully");
        result.put("firmId", firmId);
        result.put("accountId", accountId);
        return result;
    }
}
