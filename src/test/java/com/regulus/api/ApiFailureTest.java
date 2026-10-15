package com.regulus.api;

import com.regulus.api.controller.AccountController;
import com.regulus.api.service.AccountService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AccountController.class)
@ActiveProfiles("local")
@org.springframework.context.annotation.Import(com.regulus.api.config.SecurityConfig.class)
class ApiFailureTest {
    @Autowired MockMvc mvc;
    @MockBean AccountService service;

    @Test void internalDatabaseDetailsAreNotExposed() throws Exception {
        when(service.list(null)).thenThrow(new DataAccessResourceFailureException("secret database details"));
        mvc.perform(get("/api/accounts").with(user("test").roles("API")))
                .andExpect(status().isInternalServerError()).andExpect(content().json("{\"error\":\"Internal server error\"}"));
    }
    @Test void concurrentDuplicateInsertReturnsConflict() throws Exception {
        when(service.create(any())).thenThrow(new DataIntegrityViolationException("duplicate PK"));
        mvc.perform(post("/api/account/edit").with(user("test").roles("API"))
                .contentType("application/json").content("{\"firmId\":1,\"accountId\":\"ABC\"}"))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.error").value("Request conflicts with existing data"));
    }
    @Test void unsupportedMediaAndMethod() throws Exception {
        mvc.perform(post("/api/account/edit").with(user("test").roles("API"))
                .contentType("text/plain").content("hello")).andExpect(status().isUnsupportedMediaType());
        mvc.perform(put("/api/account/edit").with(user("test").roles("API")))
                .andExpect(status().isMethodNotAllowed());
    }
}
