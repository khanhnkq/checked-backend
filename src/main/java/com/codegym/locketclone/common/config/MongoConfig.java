package com.codegym.locketclone.common.config;

import org.bson.UuidRepresentation;
import org.springframework.boot.mongodb.autoconfigure.MongoClientSettingsBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.config.EnableMongoAuditing;
import org.springframework.data.mongodb.repository.config.EnableMongoRepositories;

@Configuration
@EnableMongoAuditing
@EnableMongoRepositories(basePackages = "com.codegym.locketclone.chat.repository")
public class MongoConfig {

    @Bean
    public MongoClientSettingsBuilderCustomizer mongoUuidCustomizer() {
        return clientSettingsBuilder -> clientSettingsBuilder.uuidRepresentation(UuidRepresentation.STANDARD);
    }
}
