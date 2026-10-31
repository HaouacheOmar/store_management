package com.store.auth;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "app.security.jwt-secret=secret-de-test-auth-service-32-octets-min",
        "eureka.client.enabled=false"})
@AutoConfigureMockMvc
@Testcontainers
class AuthFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16");

    @Autowired
    MockMvc mvc;

    @Autowired
    org.springframework.boot.test.web.client.TestRestTemplate http;

    @Test
    void parcoursCompletEtIsolationEntreStores() throws Exception {
        String adminA = token(register("Épicerie A", "a@shop.dz").andExpect(status().isCreated()));
        String adminB = token(register("Épicerie B", "b@shop.dz").andExpect(status().isCreated()));
        register("Autre", "A@SHOP.DZ").andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("EMAIL_TAKEN"));

        // l'admin A crée un caissier : GUEST avec SALE_CREATE seulement
        String created = call(post("/auth/users"), adminA, """
                {"name":"Caissier","email":"cash@shop.dz","password":"motdepasse1"}""")
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        int guestId = JsonPath.read(created, "$.id");
        String guest = login("cash@shop.dz", "motdepasse1");
        call(get("/auth/me"), guest, null)
                .andExpect(jsonPath("$.role").value("GUEST"))
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("SALE_CREATE")));

        // le guest n'a pas USER_MANAGE ; sans token -> 401
        call(get("/auth/users"), guest, null).andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("FORBIDDEN"));
        mvc.perform(get("/auth/me")).andExpect(status().isUnauthorized());

        // isolation : l'admin B ne voit pas l'utilisateur du store A
        call(put("/auth/users/" + guestId + "/role"), adminB, """
                {"role":"MANAGER"}""").andExpect(status().isNotFound());

        // permissions réservées aux MANAGER, puis promotion
        call(put("/auth/users/" + guestId + "/permissions"), adminA, """
                {"permissions":["STOCK_RESTOCK"]}""").andExpect(status().isConflict());
        call(put("/auth/users/" + guestId + "/role"), adminA, """
                {"role":"MANAGER"}""").andExpect(status().isOk());
        call(put("/auth/users/" + guestId + "/permissions"), adminA, """
                {"permissions":["STOCK_RESTOCK"]}""")
                .andExpect(jsonPath("$.permissions", containsInAnyOrder("SALE_CREATE", "STOCK_RESTOCK")));

        // rétrogradation -> retour aux droits par défaut
        call(put("/auth/users/" + guestId + "/role"), adminA, """
                {"role":"GUEST"}""").andExpect(jsonPath("$.permissions", containsInAnyOrder("SALE_CREATE")));

        // désactivation -> login refusé ; mauvais mot de passe -> 401
        call(put("/auth/users/" + guestId), adminA, """
                {"name":"Caissier","active":false}""").andExpect(status().isOk());
        loginCall("cash@shop.dz", "motdepasse1").andExpect(status().isForbidden());
        loginCall("a@shop.dz", "mauvais-mdp").andExpect(status().isUnauthorized());
    }

    /** Vrai HTTP (MockMvc ne fait pas le forward vers /error, il masquerait le bug). */
    @Test
    void jsonInvalideSurRoutePublique_donne400_pas401() {
        var headers = new org.springframework.http.HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        var response = http.postForEntity("/auth/login",
                new org.springframework.http.HttpEntity<>("{pas du json", headers), String.class);
        org.assertj.core.api.Assertions.assertThat(response.getStatusCode().value()).isEqualTo(400);
    }

    private ResultActions register(String store, String email) throws Exception {
        return mvc.perform(post("/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                {"storeName":"%s","name":"Admin","email":"%s","password":"motdepasse1","timezone":"Africa/Algiers"}"""
                .formatted(store, email)));
    }

    private ResultActions loginCall(String email, String password) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                {"email":"%s","password":"%s"}""".formatted(email, password)));
    }

    private String login(String email, String password) throws Exception {
        return token(loginCall(email, password).andExpect(status().isOk()));
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req,
                               String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private static String token(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.token");
    }
}
