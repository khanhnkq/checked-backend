package com.codegym.locketclone.storage.appwrite;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "storage.type", havingValue = "appwrite")
public class AppwriteStorageConfig {

    @Bean
    public RestClient appwriteRestClient(AppwriteStorageProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(30));

        return RestClient.builder()
                .baseUrl(properties.getEndpoint())
                .defaultHeader("X-Appwrite-Project", properties.getProjectId())
                .defaultHeader("X-Appwrite-Key", properties.getApiKey())
                .requestFactory(requestFactory)
                .build();
    }
}
