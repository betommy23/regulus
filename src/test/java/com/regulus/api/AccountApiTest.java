package com.regulus.api;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("local")
@Transactional
class AccountApiTest {
    @Autowired MockMvc mvc;
    private static final String ACCOUNT = "{\"firmId\":1,\"accountId\":\"ABC\",\"accountName\":\"First\",\"correspondent\":\"C\",\"branch\":\"B\",\"rep\":\"R\",\"accountCat\":\"CASH\",\"marginable\":true,\"aggregate\":true,\"taxlotMethod\":\"FIFO\"}";

    @Test void accountLifecycleAndPartialUpdates() throws Exception {
        mvc.perform(post("/api/account/edit").with(httpBasic("developer", "local-only-password"))
                .contentType("application/json").content(ACCOUNT))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.accountId").value("ABC"));
        mvc.perform(post("/api/account/edit").with(user("test").roles("API"))
                .contentType("application/json").content(ACCOUNT)).andExpect(status().isConflict());
        mvc.perform(get("/api/accounts").with(user("test").roles("API")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountName").value("First"));
        mvc.perform(get("/api/accounts").param("firmId", "1").with(user("test").roles("API")))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].accountId").value("ABC"));
        mvc.perform(get("/api/accounts").param("firmId", "2").with(user("test").roles("API")))
                .andExpect(status().isOk()).andExpect(content().json("[]"));
        mvc.perform(patch("/api/account/edit").param("firmId", "1").param("accountId", "ABC")
                .with(user("test").roles("API")).contentType("application/json")
                .content("{\"accountName\":\"Updated\",\"marginable\":false,\"aggregate\":false,\"correspondent\":\"C2\",\"branch\":\"B2\",\"rep\":\"R2\",\"accountCat\":\"MARGIN\",\"taxlotMethod\":\"LIFO\",\"firmId\":99,\"accountId\":\"OTHER\"}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.marginable").value(false))
                .andExpect(jsonPath("$.firmId").value(1)).andExpect(jsonPath("$.accountId").value("ABC"))
                .andExpect(jsonPath("$.taxlotMethod").value("LIFO"));
        mvc.perform(patch("/api/account/edit").param("firmId", "1").param("accountId", "ABC")
                .with(user("test").roles("API")).contentType("application/json").content("{\"accountName\":null}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.accountName").value("Updated"));
        mvc.perform(delete("/api/account/edit").param("firmId", "1").param("accountId", "ABC")
                .with(user("test").roles("API"))).andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Account deleted successfully"));
        mvc.perform(delete("/api/account/edit").param("firmId", "1").param("accountId", "ABC")
                .with(user("test").roles("API"))).andExpect(status().isNotFound());
    }

    @Test void invalidAndMissingAccounts() throws Exception {
        for (String json : new String[]{"{}", "{\"firmId\":1}", "{\"firmId\":1,\"accountId\":\"  \"}", "{", "null"}) {
            mvc.perform(post("/api/account/edit").with(user("test").roles("API"))
                    .contentType("application/json").content(json)).andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/account/edit").param("firmId", "1").param("accountId", "missing")
                .with(user("test").roles("API")).contentType("application/json").content("{}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/account/edit").with(user("test").roles("API"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/accounts").param("firmId", "bad").with(user("test").roles("API")))
                .andExpect(status().isBadRequest());
        mvc.perform(delete("/api/account/edit").param("firmId", "1").param("accountId", " ")
                .with(user("test").roles("API"))).andExpect(status().isBadRequest());
    }

    @Test void authenticationAndDatabase() throws Exception {
        mvc.perform(get("/api/accounts")).andExpect(status().isUnauthorized())
                .andExpect(header().string("WWW-Authenticate", "Basic realm=\"regulus\""));
        mvc.perform(get("/api/accounts").with(httpBasic("developer", "wrong"))).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/accounts").with(user("test").roles("OTHER"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/database").with(user("test").roles("API")))
                .andExpect(status().isOk()).andExpect(content().string("regulus-local"));
    }

    @Test void financialValidationIsExplicit() throws Exception {
        for (String route : new String[]{"cash", "trade", "stock"}) {
            mvc.perform(post("/api/" + route).with(user("test").roles("API"))
                    .contentType("application/json").content("{}"))
                    .andExpect(status().isBadRequest()).andExpect(jsonPath("$.error").exists());
            mvc.perform(post("/api/" + route).with(user("test").roles("API"))
                    .contentType("application/json").content("{")).andExpect(status().isBadRequest());
        }
        mvc.perform(get("/api/asset").param("firmId", "1").param("accountId", "ABC")
                .with(user("test").roles("API"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/asset").param("firmId", "1").param("accountId", "ABC").param("processDate", "bad")
                .with(user("test").roles("API"))).andExpect(status().isBadRequest());
        mvc.perform(get("/api/asset").param("firmId", "1").param("accountId", " ")
                .with(user("test").roles("API"))).andExpect(status().isBadRequest());
    }
}
