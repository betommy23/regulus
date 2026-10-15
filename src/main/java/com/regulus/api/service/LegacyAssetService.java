package com.regulus.api.service;

/** Integration boundary for the proprietary engine; local AccountAsset is not equivalent. */
public interface LegacyAssetService {
    String cash(String json) throws Exception;
    String trade(String json) throws Exception;
    String stock(String json) throws Exception;
    String asset(Integer firmId, String accountId, String processDate) throws Exception;
}
