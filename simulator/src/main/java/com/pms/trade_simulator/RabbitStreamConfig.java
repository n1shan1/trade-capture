package com.pms.trade_simulator;

import com.rabbitmq.stream.Environment;
import com.rabbitmq.stream.Producer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;

@Configuration
public class RabbitStreamConfig {

    @Value("${app.simulator.stream.name}")
    private String streamName;

    @Value("${app.simulator.stream.host}")
    private String streamHost;

    @Value("${app.simulator.stream.port}")
    private int streamPort;

    @Bean
    public Environment streamEnvironment() {
        return Environment.builder()
                .host(streamHost)
                .port(streamPort)
                .build();
    }

    @Bean
    @DependsOn("streamDeclaration")
    public Producer streamProducer(Environment environment) {
        return environment.producerBuilder()
                .stream(streamName)
                .build();
    }

    @Bean
    public String streamDeclaration(Environment streamEnvironment) {
        try {
            streamEnvironment.streamCreator().stream(streamName).create();
            return "stream-declared";
        } catch (Exception e) {
            // Stream might already exist, ignore
            return "stream-already-exists";
        }
    }
}