package com.hyeokjin.devflow.reviewagent.event;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaJsonSerdeSmokeTest {

    @Test
    void kafkaJsonSerdeCanBeConstructedAtRuntime() {
        JacksonJsonDeserializer<TestResultEvent> deserializer =
                new JacksonJsonDeserializer<>(TestResultEvent.class);
        JacksonJsonSerializer<ReviewResultEvent> serializer =
                new JacksonJsonSerializer<>();

        assertNotNull(deserializer);
        assertNotNull(serializer);

        deserializer.close();
        serializer.close();
    }
}
