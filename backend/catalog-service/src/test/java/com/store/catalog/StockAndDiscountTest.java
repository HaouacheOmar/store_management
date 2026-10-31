package com.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.store.catalog.client.SupplierClient;
import com.store.catalog.client.SupplierClient.SupplierRef;
import com.store.common.events.Events;
import com.store.common.security.TestTokens;
import com.store.common.testing.SharedContainers;
import feign.FeignException;
import feign.Request;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class StockAndDiscountTest extends SharedContainers {

    @MockitoBean
    SupplierClient suppliers;

    @Autowired
    MockMvc mvc;

    final String admin = TestTokens.token(1, 10, "ADMIN", "CATEGORY_WRITE", "PRODUCT_WRITE", "STOCK_RESTOCK", "DISCOUNT_WRITE");
    final String guest = TestTokens.token(2, 10, "GUEST", "SALE_CREATE");
    final String otherStore = TestTokens.token(3, 20, "ADMIN", "DISCOUNT_WRITE", "STOCK_RESTOCK");

    @Test
    void restockage_metAJourLeStock_historise_etPublieLEvenementKafka() throws Exception {
        int category = id(call(post("/catalog/categories"), admin, "{\"name\":\"Boissons\"}"));
        int product = id(call(post("/catalog/products"), admin, """
                {"name":"Coca","categoryId":%d,"price":100,"stockQty":2,"alertThreshold":5}""".formatted(category)));
        when(suppliers.get(7)).thenReturn(new SupplierRef(7, "Atlas"));
        when(suppliers.get(99)).thenThrow(new FeignException.NotFound("absent",
                Request.create(Request.HttpMethod.GET, "/suppliers/99", Map.of(), null, StandardCharsets.UTF_8, null), null, null));

        call(post("/catalog/restocks"), admin, restock(product, 7, 10)).andExpect(status().isCreated())
                .andExpect(jsonPath("$.supplierName").value("Atlas"));
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(12));

        call(post("/catalog/restocks"), admin, restock(product, 99, 10)).andExpect(jsonPath("$.code").value("SUPPLIER_NOT_FOUND"));
        call(post("/catalog/restocks"), admin, restock(product, 7, 0)).andExpect(status().isBadRequest());
        call(post("/catalog/restocks"), guest, restock(product, 7, 5)).andExpect(status().isForbidden());
        call(post("/catalog/restocks"), otherStore, restock(product, 7, 5)).andExpect(status().isNotFound());
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(12));

        call(get("/catalog/restocks?supplierId=7"), admin, null).andExpect(jsonPath("$.page.totalElements").value(1));
        call(get("/catalog/restocks"), otherStore, null).andExpect(jsonPath("$.page.totalElements").value(0));

        // l'édition du produit ne touche pas au stock (colonne non modifiable par l'entité)
        call(put("/catalog/products/" + product), admin, """
                {"name":"Coca 1L","categoryId":%d,"price":100,"stockQty":0,"alertThreshold":5,"version":0}""".formatted(category))
                .andExpect(jsonPath("$.stockQty").value(12));

        // outbox -> Kafka : l'événement arrive, avec son id en header
        ConsumerRecord<String, String> event = awaitRecord(Events.STOCK_RESTOCKED, r -> "10".equals(r.key()));
        assertThat(event.key()).isEqualTo("10");
        assertThat(JsonPath.<Integer>read(event.value(), "$.stockQty")).isEqualTo(12);
        assertThat(event.headers().lastHeader(Events.EVENT_ID_HEADER)).isNotNull();
    }

    @Test
    void remises_laPlusForteSApplique_sansCumul() throws Exception {
        int category = id(call(post("/catalog/categories"), admin, "{\"name\":\"Épicerie\"}"));
        int product = id(call(post("/catalog/products"), admin, """
                {"name":"Huile","categoryId":%d,"price":100,"stockQty":5,"alertThreshold":1}""".formatted(category)));
        Instant now = Instant.now();

        call(post("/catalog/discounts"), admin, discount("Soldes", 10, "CATEGORY", category, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS)));
        int productDiscount = id(call(post("/catalog/discounts"), admin,
                discount("Flash", 25, "PRODUCT", product, now.minus(1, ChronoUnit.HOURS), now.plus(1, ChronoUnit.DAYS))));
        int upcoming = id(call(post("/catalog/discounts"), admin,
                discount("Demain", 50, "PRODUCT", product, now.plus(1, ChronoUnit.DAYS), now.plus(2, ChronoUnit.DAYS))));

        // 25 % (produit) > 10 % (catégorie) ; 50 % pas encore commencée
        call(get("/catalog/products/" + product), guest, null)
                .andExpect(jsonPath("$.discountPercent").value(25))
                .andExpect(jsonPath("$.finalPrice").value(75.0));

        // une remise commencée ne se supprime pas, elle se termine
        call(delete("/catalog/discounts/" + productDiscount), admin, null).andExpect(jsonPath("$.code").value("DISCOUNT_STARTED"));
        call(post("/catalog/discounts/" + productDiscount + "/end"), admin, null).andExpect(jsonPath("$.status").value("PAST"));
        call(get("/catalog/products/" + product), guest, null)
                .andExpect(jsonPath("$.discountPercent").value(10))
                .andExpect(jsonPath("$.finalPrice").value(90.0));

        call(get("/catalog/discounts?status=UPCOMING"), admin, null).andExpect(jsonPath("$[0].targetName").value("Huile"));
        call(delete("/catalog/discounts/" + upcoming), admin, null).andExpect(status().isNoContent());

        // validations et isolation
        call(post("/catalog/discounts"), admin, discount("X", 101, "PRODUCT", product, now, now.plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest());
        call(post("/catalog/discounts"), admin, discount("X", 5, "PRODUCT", product, now, now.minus(1, ChronoUnit.DAYS)))
                .andExpect(status().isBadRequest());
        call(post("/catalog/discounts"), otherStore, discount("X", 5, "PRODUCT", product, now, now.plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isNotFound());
        call(post("/catalog/discounts"), guest, discount("X", 5, "PRODUCT", product, now, now.plus(1, ChronoUnit.DAYS)))
                .andExpect(status().isForbidden());
    }

    private static String restock(int product, int supplier, int qty) {
        return "{\"productId\":%d,\"supplierId\":%d,\"qty\":%d,\"unitCost\":60}".formatted(product, supplier, qty);
    }

    private static String discount(String label, int percent, String type, int target, Instant start, Instant end) {
        return """
                {"label":"%s","percent":%d,"targetType":"%s","targetId":%d,"startsAt":"%s","endsAt":"%s"}"""
                .formatted(label, percent, type, target, start, end);
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
