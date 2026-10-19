package com.hyeokjin.devflow.taskservice.config;

import com.hyeokjin.devflow.taskservice.event.CodeModifiedEvent;
import com.hyeokjin.devflow.taskservice.event.MergeResultEvent;
import com.hyeokjin.devflow.taskservice.event.ReviewResultEvent;
import com.hyeokjin.devflow.taskservice.event.TestResultEvent;
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
public class TaskProjectionKafkaConfig {

    private final String bootstrapServers;

    public TaskProjectionKafkaConfig(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
            String bootstrapServers
    ) {
        this.bootstrapServers = bootstrapServers;
    }

    @Bean("codeModifiedKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, CodeModifiedEvent>
    codeModifiedKafkaListenerContainerFactory() {
        return factory(CodeModifiedEvent.class);
    }

    @Bean("testResultKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, TestResultEvent>
    testResultKafkaListenerContainerFactory() {
        return factory(TestResultEvent.class);
    }

    @Bean("reviewResultKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, ReviewResultEvent>
    reviewResultKafkaListenerContainerFactory() {
        return factory(ReviewResultEvent.class);
    }

    @Bean("mergeResultKafkaListenerContainerFactory")
    ConcurrentKafkaListenerContainerFactory<String, MergeResultEvent>
    mergeResultKafkaListenerContainerFactory() {
        return factory(MergeResultEvent.class);
    }

    private <T> ConcurrentKafkaListenerContainerFactory<String, T> factory(
            Class<T> eventType
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG,
                bootstrapServers
        );
        properties.put(
                ConsumerConfig.GROUP_ID_CONFIG,
                "task-status-projection-group"
        );
        properties.put(
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG,
                "earliest"
        );

        JacksonJsonDeserializer<T> deserializer =
                new JacksonJsonDeserializer<>(eventType);
        deserializer.addTrustedPackages(
                "com.hyeokjin.devflow.taskservice.event"
        );
        deserializer.setUseTypeHeaders(false);

        DefaultKafkaConsumerFactory<String, T> consumerFactory =
                new DefaultKafkaConsumerFactory<>(
                        properties,
                        new StringDeserializer(),
                        deserializer
                );

        ConcurrentKafkaListenerContainerFactory<String, T> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory);
        return factory;
    }
}
