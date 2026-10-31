package com.store.reporting;

import com.jayway.jsonpath.JsonPath;
import com.store.common.events.Events;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class ReportingFlowTest extends SharedContainers {

    @Autowired
    MockMvc mvc;

    // tz des TestTokens : Africa/Algiers
    static final LocalDate TODAY = LocalDate.now(ZoneId.of("Africa/Algiers"));

    final String admin = TestTokens.token(9, 50, "ADMIN", "STATS_VIEW", "SALES_VIEW_ALL");
    final String cashier1 = TestTokens.token(1, 50, "GUEST", "SALE_CREATE");
    final String cashier2 = TestTokens.token(2, 50, "GUEST", "SALE_CREATE");
    final String otherStore = TestTokens.token(3, 51, "ADMIN", "STATS_VIEW", "SALES_VIEW_ALL");

    @Test
    void agregats_annulation_doublon_graphes_etPermissions() throws Exception {
        String sale1 = sale(1, 1, TODAY, "175.50", line(10, "Lait", 1, "Crèmerie", 2, "160.00"), line(11, "Pain", 2, "Boulangerie", 1, "15.50"));
        String sale2 = sale(2, 2, TODAY, "62.00", line(11, "Pain", 2, "Boulangerie", 4, "62.00"));
        String sale3 = sale(3, 1, TODAY.minusDays(3), "80.00", line(10, "Lait", 1, "Crèmerie", 1, "80.00"));
        try (var producer = new KafkaProducer<String, String>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class))) {
            String duplicated = UUID.randomUUID().toString();
            send(producer, Events.SALE_COMPLETED, sale1, duplicated);
            send(producer, Events.SALE_COMPLETED, sale1, duplicated); // relivraison : ignorée
            send(producer, Events.SALE_COMPLETED, sale2, UUID.randomUUID().toString());
            send(producer, Events.SALE_COMPLETED, sale3, UUID.randomUUID().toString());
            send(producer, Events.SALE_CANCELLED, sale2, UUID.randomUUID().toString()); // sort des totaux
        }

        // attend que les 5 messages soient consommés : 255.50 = 175.50 + 80 (vente 2 annulée, doublon ignoré)
        long deadline = System.currentTimeMillis() + 30_000;
        String revenue = "";
        while (System.currentTimeMillis() < deadline) {
            revenue = body(get("/reporting/revenue?from=" + TODAY.minusDays(6) + "&to=" + TODAY), admin);
            if (JsonPath.<Integer>read(revenue, "$.tickets") == 2 && "255.5".equals(String.valueOf(JsonPath.<Object>read(revenue, "$.revenue")))) break;
            Thread.sleep(300);
        }
        assertJson(revenue, "$.tickets", 2);
        assertJson(revenue, "$.itemsSold", 4);
        assertJson(revenue, "$.averageBasket", 127.75);
        assertJson(revenue, "$.series.length()", 7);
        assertJson(revenue, "$.series[3].revenue", 80.0);

        // M9 : chaque caissier ne voit que son encaissé du jour
        call(get("/reporting/me/today"), cashier1).andExpect(jsonPath("$.cashed").value(175.50)).andExpect(jsonPath("$.tickets").value(1));
        call(get("/reporting/me/today"), cashier2).andExpect(jsonPath("$.cashed").value(0)).andExpect(jsonPath("$.tickets").value(0));

        // M7 : encaissé par caissier et produits vendus aujourd'hui
        call(get("/reporting/today"), admin)
                .andExpect(jsonPath("$.cashed").value(175.50))
                .andExpect(jsonPath("$.cashiers", hasSize(1)))
                .andExpect(jsonPath("$.products[*].name", contains("Lait", "Pain")));

        // graphes et filtres
        String period = "from=" + TODAY.minusDays(6) + "&to=" + TODAY;
        call(get("/reporting/top-products?" + period), admin).andExpect(jsonPath("$[*].name", contains("Lait", "Pain")))
                .andExpect(jsonPath("$[0].qty").value(3)).andExpect(jsonPath("$[0].revenue").value(240.0));
        call(get("/reporting/top-products?" + period + "&categoryId=2"), admin).andExpect(jsonPath("$[*].name", contains("Pain")));
        call(get("/reporting/top-products?" + period + "&limit=1&by=REVENUE"), admin).andExpect(jsonPath("$", hasSize(1)));
        call(get("/reporting/top-categories?" + period), admin).andExpect(jsonPath("$[*].name", contains("Crèmerie", "Boulangerie")));
        call(get("/reporting/top-products?from=" + TODAY + "&to=" + TODAY.minusDays(1)), admin).andExpect(status().isBadRequest());
        call(get("/reporting/top-products?limit=99"), admin).andExpect(status().isBadRequest());

        // permissions et isolation : jamais le CA pour un guest, rien pour un autre store
        call(get("/reporting/revenue"), cashier1).andExpect(status().isForbidden());
        call(get("/reporting/today"), cashier1).andExpect(status().isForbidden());
        call(get("/reporting/revenue?" + period), otherStore).andExpect(jsonPath("$.revenue").value(0));
    }

    private static String line(int productId, String name, int categoryId, String category, int qty, String total) {
        return """
                {"productId":%d,"productName":"%s","categoryId":%d,"categoryName":"%s","qty":%d,
                 "unitPrice":0,"discountPercent":0,"lineTotal":%s}""".formatted(productId, name, categoryId, category, qty, total);
    }

    private static String sale(long id, long cashier, LocalDate day, String total, String... lines) {
        return """
                {"saleId":%d,"storeId":50,"cashierId":%d,"cashierName":"Caissier %d","saleDate":"%s","total":%s,"lines":[%s]}"""
                .formatted(id, cashier, cashier, day, total, String.join(",", lines));
    }

    private static void send(KafkaProducer<String, String> producer, String topic, String payload, String eventId) throws Exception {
        var record = new ProducerRecord<>(topic, "50", payload);
        record.headers().add(Events.EVENT_ID_HEADER, eventId.getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private static void assertJson(String json, String path, Object expected) {
        Object actual = JsonPath.read(json, path);
        if (actual instanceof Number n && expected instanceof Number e) {
            org.assertj.core.api.Assertions.assertThat(n.doubleValue()).as(path).isEqualTo(e.doubleValue());
        } else {
            org.assertj.core.api.Assertions.assertThat(actual).as(path).isEqualTo(expected);
        }
    }

    private String body(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token) throws Exception {
        return call(req, token).andReturn().getResponse().getContentAsString();
    }

    private ResultActions call(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }
}
