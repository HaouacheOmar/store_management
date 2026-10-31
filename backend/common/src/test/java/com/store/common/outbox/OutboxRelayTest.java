package com.store.common.outbox;

import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void echecEnvoi_arreteLeCycle_etNeMarqueQueLesEvenementsEnvoyes() {
        var ok = new OutboxEvent("sale.completed", "1", "{}");
        var ko = new OutboxEvent("sale.completed", "1", "{}");
        var after = new OutboxEvent("sale.completed", "1", "{}");

        TypedQuery<OutboxEvent> query = mock(TypedQuery.class, RETURNS_SELF);
        when(query.getResultList()).thenReturn(List.of(ok, ko, after));
        var em = mock(EntityManager.class);
        when(em.createQuery(anyString(), eq(OutboxEvent.class))).thenReturn(query);

        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        new OutboxRelay(em, kafka).relay();

        assertThat(ok.getSentAt()).isNotNull();
        assertThat(ko.getSentAt()).isNull();
        assertThat(after.getSentAt()).isNull();
        verify(kafka, times(2)).send(any(ProducerRecord.class)); // "after" n'est pas tenté : ordre préservé
    }
}
