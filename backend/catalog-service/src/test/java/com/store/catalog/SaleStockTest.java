package com.store.catalog;

import com.jayway.jsonpath.JsonPath;
import com.store.common.events.Events;
import com.store.common.security.InternalApiFilter;
import com.store.common.security.TestTokens;
import com.store.common.testing.SharedContainers;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Flux de stock du lot 4 : ventes concurrentes, alerte stock bas, annulation via Kafka. */
@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET,
        "app.security.internal-secret=" + TestTokens.INTERNAL_SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class SaleStockTest extends SharedContainers {

    @Autowired
    MockMvc mvc;

    final String admin = TestTokens.token(1, 30, "ADMIN", "CATEGORY_WRITE", "PRODUCT_WRITE", "DISCOUNT_WRITE", "SALE_CREATE");
    final String cashier = TestTokens.token(2, 30, "GUEST", "SALE_CREATE");
    final String otherCashier = TestTokens.token(4, 30, "GUEST", "SALE_CREATE");
    final String otherStore = TestTokens.token(3, 31, "GUEST", "SALE_CREATE");

    @Test
    void ventesConcurrentes_jamaisDeSurvente_etAlerteRupture() throws Exception {
        int product = newProduct("Pain", 5, 2);

        // 10 caisses tentent de vendre 1 pain en même temps : exactement 5 réussissent
        var pool = Executors.newFixedThreadPool(10);
        List<Future<Integer>> results = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            Callable<Integer> sale = () -> decrement(cashier, product, 1).andReturn().getResponse().getStatus();
            results.add(pool.submit(sale));
        }
        int ok = 0;
        for (var r : results) if (r.get() == 200) ok++;
        pool.shutdown();
        assertThat(ok).isEqualTo(5);
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(0));

        decrement(cashier, product, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.details.available").value(0));
        decrement(otherStore, product, 1).andExpect(status().isNotFound());

        var alert = awaitRecord(Events.STOCK_LOW, r -> r.value().contains("\"productId\":" + product + ",")
                && r.value().contains("\"type\":\"OUT\""));
        assertThat(JsonPath.<Integer>read(alert.value(), "$.stockQty")).isZero();
    }

    @Test
    void decrement_renvoieLePrixRemise_etEstToutOuRien() throws Exception {
        int cheap = newProduct("Lait", 10, 1);
        int rare = newProduct("Truffe", 1, 0);
        call(post("/catalog/discounts"), admin, """
                {"label":"Promo","percent":20,"targetType":"PRODUCT","targetId":%d,
                 "startsAt":"2020-01-01T00:00:00Z","endsAt":"2100-01-01T00:00:00Z"}""".formatted(cheap));

        call(internal(post("/internal/stock/decrement")), cashier, """
                {"lines":[{"productId":%d,"qty":2},{"productId":%d,"qty":1}]}""".formatted(cheap, cheap))
                .andExpect(jsonPath("$.reservationId").isNotEmpty())
                .andExpect(jsonPath("$.lines[0].discountPercent").value(20))
                .andExpect(jsonPath("$.lines[0].finalUnitPrice").value(80.0));
        call(get("/catalog/products/" + cheap), admin, null).andExpect(jsonPath("$.stockQty").value(7));

        // le 2e produit manque : la sortie du 1er est annulée aussi
        call(internal(post("/internal/stock/decrement")), cashier, """
                {"lines":[{"productId":%d,"qty":1},{"productId":%d,"qty":5}]}""".formatted(cheap, rare))
                .andExpect(status().isConflict());
        call(get("/catalog/products/" + cheap), admin, null).andExpect(jsonPath("$.stockQty").value(7));
    }

    @Test
    void venteAnnulee_recrediteLeStock_uneSeuleFois() throws Exception {
        int product = newProduct("Café", 10, 1);
        decrement(cashier, product, 4).andExpect(status().isOk());

        String event = """
                {"saleId":1,"storeId":30,"cashierId":2,"cashierName":"C","saleDate":"2026-10-31","total":400,
                 "lines":[{"productId":%d,"productName":"Café","categoryId":1,"categoryName":"X","qty":4,
                           "unitPrice":100,"discountPercent":0,"lineTotal":400}]}""".formatted(product);
        String eventId = UUID.randomUUID().toString();
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            for (int i = 0; i < 2; i++) { // relivraison : même eventId deux fois
                var record = new ProducerRecord<>(Events.SALE_CANCELLED, "30", event);
                record.headers().add(Events.EVENT_ID_HEADER, eventId.getBytes(StandardCharsets.UTF_8));
                producer.send(record).get();
            }
        }

        long deadline = System.currentTimeMillis() + 30_000;
        int stock = 6;
        while (stock != 10 && System.currentTimeMillis() < deadline) {
            Thread.sleep(300);
            stock = JsonPath.read(call(get("/catalog/products/" + product), admin, null)
                    .andReturn().getResponse().getContentAsString(), "$.stockQty");
        }
        assertThat(stock).isEqualTo(10);
        Thread.sleep(2000); // laisse au doublon le temps d'être (non) appliqué
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(10));
    }

    @Test
    void routesInternes_exigentLeSecretDeService_etLaCompensationEstAUsageUnique() throws Exception {
        int product = newProduct("Sucre", 10, 1);
        String body = "{\"lines\":[{\"productId\":%d,\"qty\":3}]}".formatted(product);

        // un caissier qui joint le catalogue avec son seul JWT est refusé, même avec la bonne permission
        call(post("/internal/stock/decrement"), cashier, body).andExpect(status().isForbidden());
        call(post("/internal/stock/decrement").header(InternalApiFilter.HEADER, "mauvais-secret-mais-de-plus-de-32-octets"),
                cashier, body).andExpect(status().isForbidden());
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(10));

        String reservation = JsonPath.read(call(internal(post("/internal/stock/decrement")), cashier, body)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.reservationId");
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(7));

        // seul l'auteur de la sortie de stock peut la rendre, une seule fois, et rien d'autre que ce qui est sorti
        String release = "/internal/stock/reservations/" + reservation + "/release";
        call(internal(post(release)), otherCashier, null).andExpect(jsonPath("$.code").value("RESERVATION_NOT_RELEASABLE"));
        call(internal(post(release)), otherStore, null).andExpect(status().isConflict());
        call(internal(post(release)), cashier, null).andExpect(status().isNoContent());
        call(internal(post(release)), cashier, null).andExpect(status().isConflict());
        call(internal(post("/internal/stock/reservations/" + UUID.randomUUID() + "/release")), cashier, null)
                .andExpect(status().isConflict());
        call(get("/catalog/products/" + product), admin, null).andExpect(jsonPath("$.stockQty").value(10));
    }

    private static MockHttpServletRequestBuilder internal(MockHttpServletRequestBuilder req) {
        return req.header(InternalApiFilter.HEADER, TestTokens.INTERNAL_SECRET);
    }

    private int newProduct(String name, int stock, int threshold) throws Exception {
        String category = call(post("/catalog/categories"), admin, "{\"name\":\"Cat " + UUID.randomUUID() + "\"}")
                .andReturn().getResponse().getContentAsString();
        int categoryId = JsonPath.read(category, "$.id");
        return JsonPath.read(call(post("/catalog/products"), admin, """
                {"name":"%s","categoryId":%d,"price":100,"stockQty":%d,"alertThreshold":%d}"""
                .formatted(name, categoryId, stock, threshold)).andReturn().getResponse().getContentAsString(), "$.id");
    }

    private ResultActions decrement(String token, int product, int qty) throws Exception {
        return call(internal(post("/internal/stock/decrement")), token,
                "{\"lines\":[{\"productId\":%d,\"qty\":%d}]}".formatted(product, qty));
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token, String body) throws Exception {
        req.header("Authorization", "Bearer " + token);
        if (body != null) req.contentType(MediaType.APPLICATION_JSON).content(body);
        return mvc.perform(req);
    }
}
