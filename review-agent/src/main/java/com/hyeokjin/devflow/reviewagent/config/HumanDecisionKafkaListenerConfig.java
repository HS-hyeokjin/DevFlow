package com.hyeokjin.devflow.reviewagent.config;

import com.hyeokjin.devflow.reviewagent.event.HumanDecisionEvent;
import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JacksonJsonDeserializer;

@Configuration
public class HumanDecisionKafkaListenerConfig {

    @Bean("humanDecisionKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, HumanDecisionEvent>
    humanDecisionKafkaListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
            String bootstrapServers
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "review-agent-human-decision-group"
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        JacksonJsonDeserializer<HumanDecisionEvent> deserializer =
                new JacksonJsonDeserializer<>(HumanDecisionEvent.class);
        deserializer.addTrustedPackages(
                "com.hyeokjin.devflow.reviewagent.event"
        );
        deserializer.setUseTypeHeaders(false);

        ConcurrentKafkaListenerContainerFactory<String, HumanDecisionEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(
                new DefaultKafkaConsumerFactory<>(
                        properties,
                        new StringDeserializer(),
                        deserializer
                )
        );
        return factory;
    }
}
