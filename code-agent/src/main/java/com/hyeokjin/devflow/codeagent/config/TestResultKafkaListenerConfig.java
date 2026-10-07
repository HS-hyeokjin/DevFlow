package com.hyeokjin.devflow.codeagent.config;

import com.hyeokjin.devflow.codeagent.event.TestResultEvent;
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

/**
 * A separate deserializer is required: DEV_TASK_CREATED and TEST_FAILED
 * have different event schemas and different consumer groups.
 */
@Configuration
public class TestResultKafkaListenerConfig {

    @Bean("testResultKafkaListenerContainerFactory")
    public ConcurrentKafkaListenerContainerFactory<String, TestResultEvent>
    testResultKafkaListenerContainerFactory(
            @Value("${spring.kafka.bootstrap-servers:localhost:9092}") String bootstrapServers
    ) {
        Map<String, Object> properties = new HashMap<>();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "code-agent-retry-group");
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        properties.put(
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                JacksonJsonDeserializer.class
        );
        properties.put(
                JacksonJsonDeserializer.VALUE_DEFAULT_TYPE,
                TestResultEvent.class.getName()
        );
        properties.put(
                JacksonJsonDeserializer.TRUSTED_PACKAGES,
                "com.hyeokjin.devflow.codeagent.event"
        );
        properties.put(JacksonJsonDeserializer.USE_TYPE_INFO_HEADERS, false);

        var containerFactory =
                new ConcurrentKafkaListenerContainerFactory<String, TestResultEvent>();
        containerFactory.setConsumerFactory(new DefaultKafkaConsumerFactory<>(properties));
        return containerFactory;
    }
}
