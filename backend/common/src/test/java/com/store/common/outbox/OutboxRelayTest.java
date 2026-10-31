package com.store.common.outbox;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.kafka.core.KafkaTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OutboxRelayTest {

    @Test
    @SuppressWarnings("unchecked")
    void echecEnvoi_arreteLeCycle_etNeMarqueQueLesLignesEnvoyees() {
        var jdbc = mock(JdbcTemplate.class);
        KafkaTemplate<String, String> kafka = mock(KafkaTemplate.class);
        var ok = new OutboxRelay.Row(UUID.randomUUID(), "sale.completed", "1", "{}");
        var ko = new OutboxRelay.Row(UUID.randomUUID(), "sale.completed", "1", "{}");
        var after = new OutboxRelay.Row(UUID.randomUUID(), "sale.completed", "1", "{}");
        when(jdbc.query(anyString(), any(RowMapper.class))).thenReturn(List.of(ok, ko, after));
        when(kafka.send(any(ProducerRecord.class)))
                .thenReturn(CompletableFuture.completedFuture(null))
                .thenReturn(CompletableFuture.failedFuture(new RuntimeException("broker down")));

        new OutboxRelay(jdbc, kafka).relay();

        verify(jdbc).update(contains("SET sent_at"), eq(ok.id()));
        verify(jdbc, never()).update(contains("SET sent_at"), eq(ko.id()));
        verify(kafka, times(2)).send(any(ProducerRecord.class)); // "after" n'est pas tenté : ordre préservé
    }
}
