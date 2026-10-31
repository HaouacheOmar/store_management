package com.store.supplier;

import com.jayway.jsonpath.JsonPath;
import com.store.common.security.TestTokens;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class SupplierFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    final String admin1 = TestTokens.token(1, 1, "ADMIN", "SUPPLIER_WRITE");
    final String guest1 = TestTokens.token(2, 1, "GUEST", "SALE_CREATE");
    final String admin2 = TestTokens.token(3, 2, "ADMIN", "SUPPLIER_WRITE");

    @Test
    void crudIsolationEtSuppressionLogique() throws Exception {
        String created = call(post("/suppliers"), admin1, """
                {"name":"Grossiste Atlas","phone":"0555 00 00 00","email":"contact@atlas.dz"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int id = JsonPath.read(created, "$.id");

        call(post("/suppliers"), admin1, "{\"name\":\"grossiste atlas\"}").andExpect(status().isConflict());
        call(post("/suppliers"), admin1, "{\"name\":\"X\",\"email\":\"pas-un-email\"}").andExpect(status().isBadRequest());
        call(post("/suppliers"), guest1, "{\"name\":\"Interdit\"}").andExpect(status().isForbidden());

        call(get("/suppliers"), guest1, null).andExpect(jsonPath("$", hasSize(1)));
        call(get("/suppliers"), admin2, null).andExpect(jsonPath("$", hasSize(0)));
        call(get("/suppliers/" + id), admin2, null).andExpect(status().isNotFound());
        call(delete("/suppliers/" + id), admin2, null).andExpect(status().isNotFound());

        call(put("/suppliers/" + id), admin1, "{\"name\":\"Atlas Distribution\",\"phone\":\"\"}")
                .andExpect(jsonPath("$.name").value("Atlas Distribution"))
                .andExpect(jsonPath("$.phone").doesNotExist());

        // supprimé : invisible, et son nom redevient libre
        call(delete("/suppliers/" + id), admin1, null).andExpect(status().isNoContent());
        call(get("/suppliers"), admin1, null).andExpect(jsonPath("$", hasSize(0)));
        call(post("/suppliers"), admin1, "{\"name\":\"Atlas Distribution\"}").andExpect(status().isCreated());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
