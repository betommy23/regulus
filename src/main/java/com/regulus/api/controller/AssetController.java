package com.regulus.api.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.regulus.api.service.LegacyAssetService;
import java.time.LocalDate;
import javax.validation.constraints.NotBlank;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@Validated
@RequestMapping(value = "/api", produces = "application/json")
public class AssetController {
    private final LegacyAssetService service;
    public AssetController(LegacyAssetService service) { this.service = service; }

    @PostMapping(value = "/cash", consumes = "application/json")
    public String cash(@RequestBody JsonNode body) throws Exception { return service.cash(body.toString()); }
    @PostMapping(value = "/trade", consumes = "application/json")
    public String trade(@RequestBody JsonNode body) throws Exception { return service.trade(body.toString()); }
    @PostMapping(value = "/stock", consumes = "application/json")
    public String stock(@RequestBody JsonNode body) throws Exception { return service.stock(body.toString()); }
    @GetMapping("/asset")
    public String asset(@RequestParam Integer firmId, @RequestParam @NotBlank String accountId,
                        @RequestParam(required = false) String processDate) throws Exception {
        if (processDate != null) LocalDate.parse(processDate);
        return service.asset(firmId, accountId, processDate);
    }
}
