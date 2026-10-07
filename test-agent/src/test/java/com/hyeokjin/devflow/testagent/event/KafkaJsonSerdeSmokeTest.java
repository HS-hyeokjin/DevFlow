package com.hyeokjin.devflow.testagent.event;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;
import org.springframework.kafka.support.serializer.JacksonJsonSerializer;

class KafkaJsonSerdeSmokeTest {

    @Test
    void jacksonKafkaSerdeCanBeConstructedAtRuntime() {
        JacksonJsonDeserializer<CodeModifiedEvent> deserializer =
                new JacksonJsonDeserializer<>(CodeModifiedEvent.class);
        JacksonJsonSerializer<TestResultEvent> serializer =
                new JacksonJsonSerializer<>();

        assertNotNull(deserializer);
        assertNotNull(serializer);

        deserializer.close();
        serializer.close();
    }
}
