package com.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.store.common.security.TestTokens;
import com.store.common.testing.SharedContainers;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class CatalogFlowTest extends SharedContainers {

    @Autowired
    MockMvc mvc;

    final String admin1 = TestTokens.token(1, 1, "ADMIN", "CATEGORY_WRITE", "PRODUCT_WRITE");
    final String guest1 = TestTokens.token(2, 1, "GUEST", "SALE_CREATE");
    final String admin2 = TestTokens.token(3, 2, "ADMIN", "CATEGORY_WRITE", "PRODUCT_WRITE");

    @Test
    void catalogueCompletEtIsolationEntreStores() throws Exception {
        int boissons = id(call(post("/catalog/categories"), admin1, "{\"name\":\"Boissons\"}").andExpect(status().isCreated()));
        call(post("/catalog/categories"), admin1, "{\"name\":\"boissons\"}").andExpect(status().isConflict());

        int coca = id(product(admin1, "Coca 1L", "C1", boissons, 3).andExpect(status().isCreated()));
        int eau = id(product(admin1, "Eau", null, boissons, 0));
        product(admin1, "Jus", null, boissons, 50);
        product(admin1, "Autre coca", "c1", boissons, 1).andExpect(jsonPath("$.code").value("CODE_TAKEN"));

        // filtres : stock bas (≤ seuil 5, ruptures comprises), rupture, recherche nom et code
        names(admin1, "stock=LOW").andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Coca 1L", "Eau")));
        names(admin1, "stock=OUT").andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Eau")));
        names(admin1, "q=COC").andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Coca 1L")));
        names(admin1, "q=c1").andExpect(jsonPath("$.content[*].name", containsInAnyOrder("Coca 1L")));

        // isolation : le store 2 ne voit ni n'utilise rien du store 1
        names(admin2, "").andExpect(jsonPath("$.page.totalElements").value(0));
        call(get("/catalog/products/" + coca), admin2, null).andExpect(status().isNotFound());
        product(admin2, "Intrus", null, boissons, 1).andExpect(status().isNotFound());

        // permissions : le guest lit, n'écrit pas
        names(guest1, "").andExpect(jsonPath("$.page.totalElements").value(3));
        product(guest1, "Interdit", null, boissons, 1).andExpect(status().isForbidden());

        // édition : version obsolète refusée ; le stock ne change pas par l'édition
        call(put("/catalog/products/" + coca), admin1, body("Coca 1.5L", "C1", boissons, 999, 7))
                .andExpect(status().isConflict()).andExpect(jsonPath("$.code").value("STALE_PRODUCT"));
        call(put("/catalog/products/" + coca), admin1, body("Coca 1.5L", "C1", boissons, 999, 0))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.stockQty").value(3))
                .andExpect(jsonPath("$.version").value(1));

        // catégorie non vide : suppression refusée tant qu'il reste des produits actifs
        call(get("/catalog/categories"), admin1, null).andExpect(jsonPath("$[0].productCount").value(3));
        call(delete("/catalog/categories/" + boissons), admin1, null)
                .andExpect(jsonPath("$.code").value("CATEGORY_NOT_EMPTY"));
        call(delete("/catalog/products/" + eau), admin1, null).andExpect(status().isNoContent());
        call(get("/catalog/products/" + eau), admin1, null).andExpect(status().isNotFound());
    }

    private ResultActions product(String token, String name, String code, int categoryId, int stock) throws Exception {
        return call(post("/catalog/products"), token, body(name, code, categoryId, stock, null));
    }

    private static String body(String name, String code, int categoryId, int stock, Integer version) {
        return """
                {"name":"%s","code":%s,"categoryId":%d,"price":120.00,"stockQty":%d,"alertThreshold":5,"version":%s}"""
                .formatted(name, code == null ? "null" : "\"" + code + "\"", categoryId, stock, version);
    }

    private ResultActions names(String token, String query) throws Exception {
        return call(get("/catalog/products?" + query), token, null).andExpect(status().isOk());
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }

    private static int id(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.id");
    }
}
