package com.store.reporting;

import com.jayway.jsonpath.JsonPath;
import com.store.common.events.Events;
import com.store.common.security.TestTokens;
import com.store.common.testing.SharedContainers;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.simp.stomp.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PRD M10 : alertes de stock en temps réel, statut lu par utilisateur, abonnements cloisonnés par store. */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"app.security.jwt-secret=" + TestTokens.SECRET, "eureka.client.enabled=false"})
@AutoConfigureMockMvc
class NotificationFlowTest extends SharedContainers {

    @LocalServerPort
    int port;

    @Autowired
    MockMvc mvc;

    static KafkaProducer<String, String> producer;

    final String admin = TestTokens.token(1, 60, "ADMIN", "STOCK_RESTOCK");
    final String manager = TestTokens.token(2, 60, "MANAGER", "SALE_CREATE", "STOCK_RESTOCK");
    final String guest = TestTokens.token(3, 60, "GUEST", "SALE_CREATE");
    final String otherStore = TestTokens.token(4, 61, "ADMIN", "STOCK_RESTOCK");

    @BeforeAll
    static void producer() {
        producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    @AfterAll
    static void close() {
        producer.close();
    }

    @Test
    void alertesTempsReel_luParUtilisateur_ruptureVersStockBas_puisResolution() throws Exception {
        BlockingQueue<String> pushes = new LinkedBlockingQueue<>();
        StompSession session = connect(admin);
        session.subscribe("/topic/stores/60/notifications", collector(pushes));
        Thread.sleep(300); // abonnement enregistré côté serveur

        send(Events.STOCK_LOW, low(7, "Lait", 3, "LOW"), UUID.randomUUID());
        assertThat(pushes.poll(30, TimeUnit.SECONDS)).as("push WebSocket").contains("NOTIFICATIONS_CHANGED");

        List<Map<String, Object>> list = notifications(admin, false);
        assertThat(list).hasSize(1);
        assertThat(list.getFirst()).containsEntry("type", "LOW").containsEntry("read", false);
        int lowId = (Integer) list.getFirst().get("id");

        // lu par l'admin, toujours non lu pour le responsable
        call(put("/reporting/notifications/" + lowId + "/read"), admin).andExpect(status().isOk());
        assertThat(notifications(admin, true)).isEmpty();
        assertThat(notifications(manager, true)).hasSize(1);

        // rupture : remplace le stock bas, nouvelle alerte non lue ; doublon Kafka ignoré
        UUID outEvent = UUID.randomUUID();
        send(Events.STOCK_LOW, low(7, "Lait", 0, "OUT"), outEvent);
        send(Events.STOCK_LOW, low(7, "Lait", 0, "OUT"), outEvent);
        await(() -> notifications(admin, false).stream().anyMatch(n -> "OUT".equals(n.get("type"))));
        assertThat(notifications(admin, false)).singleElement().satisfies(n -> {
            assertThat(n).containsEntry("type", "OUT").containsEntry("read", false);
        });

        // restocké sous le seuil : la rupture redevient un stock bas ; au-dessus : plus rien
        send(Events.STOCK_RESTOCKED, "{\"storeId\":60,\"productId\":7,\"stockQty\":2,\"alertThreshold\":5}", UUID.randomUUID());
        await(() -> notifications(admin, false).stream().anyMatch(n -> "LOW".equals(n.get("type"))));
        assertThat(notifications(admin, false)).singleElement().satisfies(n ->
                assertThat(n).containsEntry("type", "LOW").containsEntry("stockQty", 2));
        send(Events.STOCK_RESTOCKED, "{\"storeId\":60,\"productId\":7,\"stockQty\":20,\"alertThreshold\":5}", UUID.randomUUID());
        await(() -> notifications(admin, false).isEmpty());

        session.disconnect();
    }

    @Test
    void cloisonnement_parPermission_parStore_etParJwt() throws Exception {
        call(get("/reporting/notifications"), guest).andExpect(status().isForbidden());
        call(put("/reporting/notifications/1/read"), otherStore).andExpect(status().isNotFound());

        // connexion WebSocket sans JWT : refusée
        assertThatThrownBy(() -> connectWith(new StompHeaders())).isInstanceOf(Exception.class);

        // abonnement au topic d'un autre store : la session est coupée
        StompSession intruder = connect(otherStore);
        intruder.subscribe("/topic/stores/60/notifications", collector(new LinkedBlockingQueue<>()));
        long deadline = System.currentTimeMillis() + 10_000;
        while (intruder.isConnected() && System.currentTimeMillis() < deadline) Thread.sleep(100);
        assertThat(intruder.isConnected()).isFalse();

        // pas de STOCK_RESTOCK : abonnement refusé même sur son propre store
        StompSession cashier = connect(guest);
        cashier.subscribe("/topic/stores/60/notifications", collector(new LinkedBlockingQueue<>()));
        deadline = System.currentTimeMillis() + 10_000;
        while (cashier.isConnected() && System.currentTimeMillis() < deadline) Thread.sleep(100);
        assertThat(cashier.isConnected()).isFalse();
    }

    private StompSession connect(String token) throws Exception {
        StompHeaders headers = new StompHeaders();
        headers.add("Authorization", "Bearer " + token);
        return connectWith(headers);
    }

    private StompSession connectWith(StompHeaders headers) throws Exception {
        var client = new WebSocketStompClient(new StandardWebSocketClient());
        return client.connectAsync("ws://localhost:" + port + "/ws", new WebSocketHttpHeaders(), headers,
                new StompSessionHandlerAdapter() {
                }).get(10, TimeUnit.SECONDS);
    }

    private static StompFrameHandler collector(BlockingQueue<String> queue) {
        return new StompFrameHandler() {
            @Override
            public Type getPayloadType(StompHeaders headers) {
                return byte[].class;
            }

            @Override
            public void handleFrame(StompHeaders headers, Object payload) {
                queue.add(new String((byte[]) payload, StandardCharsets.UTF_8));
            }
        };
    }

    private static String low(long productId, String name, int qty, String type) {
        return """
                {"storeId":60,"productId":%d,"productName":"%s","stockQty":%d,"alertThreshold":5,"type":"%s"}"""
                .formatted(productId, name, qty, type);
    }

    private static void send(String topic, String payload, UUID eventId) throws Exception {
        var record = new ProducerRecord<>(topic, "60", payload);
        record.headers().add(Events.EVENT_ID_HEADER, eventId.toString().getBytes(StandardCharsets.UTF_8));
        producer.send(record).get();
    }

    private List<Map<String, Object>> notifications(String token, boolean unread) throws Exception {
        String body = call(get("/reporting/notifications?unread=" + unread), token)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$");
    }

    private interface Check {
        boolean ok() throws Exception;
    }

    private static void await(Check check) throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        while (!check.ok()) {
            if (System.currentTimeMillis() > deadline) throw new AssertionError("Condition jamais atteinte");
            Thread.sleep(200);
        }
    }

    private ResultActions call(MockHttpServletRequestBuilder req, String token) throws Exception {
        return mvc.perform(req.header("Authorization", "Bearer " + token));
    }
}
