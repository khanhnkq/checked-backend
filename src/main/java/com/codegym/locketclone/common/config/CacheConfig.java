package com.codegym.locketclone.common.config;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;
import org.springframework.security.jackson2.CoreJackson2Module;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    // Phân vùng 1: Auth & User Identity
    public static final String USERS_CACHE = "users";
    public static final String FRIENDS_CACHE = "friends";

    // Phân vùng 2: Photo & Reactions
    public static final String REACTIONS_SUMMARY_CACHE = "reactions_summary";
    public static final String FEED_CACHE = "feed";
    public static final String MY_PHOTOS_CACHE = "myphotos";
    public static final String PHOTO_DETAIL_CACHE = "photo_detail";

    // Phân vùng 3: Financial & Expense Dashboard
    public static final String EXPENSE_SUMMARY_CACHE = "expense_summary";
    public static final String EXPENSE_CASHFLOW_CACHE = "expense_cashflow";
    public static final String EXPENSE_CATEGORIES_CACHE = "expense_categories";
    public static final String EXPENSE_BUDGETS_CACHE = "expense_budgets";

    // Phân vùng 4: Calorie & Health Tracker
    public static final String CALORIE_DAILY_CACHE = "calorie_daily";
    public static final String CALORIE_SUMMARY_CACHE = "calorie_summary";
    public static final String CALORIE_GOAL_CACHE = "calorie_goal";

    // Phân vùng 5: Realtime Chat & WebSocket State
    public static final String CHAT_UNREAD_CACHE = "chat_unread";
    public static final String CHAT_CONVERSATIONS_CACHE = "chat_conversations";

    @Bean
    @Primary
    public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
        ObjectMapper redisObjectMapper = new ObjectMapper();
        redisObjectMapper.registerModule(new JavaTimeModule());
        redisObjectMapper.registerModule(new CoreJackson2Module());
        redisObjectMapper.activateDefaultTyping(
                LaissezFaireSubTypeValidator.instance,
                ObjectMapper.DefaultTyping.EVERYTHING,
                JsonTypeInfo.As.WRAPPER_ARRAY
        );
        GenericJackson2JsonRedisSerializer jsonSerializer = new GenericJackson2JsonRedisSerializer(redisObjectMapper);

        RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
                .serializeKeysWith(RedisSerializationContext.SerializationPair.fromSerializer(new StringRedisSerializer()))
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(jsonSerializer))
                .entryTtl(Duration.ofMinutes(30))
                .disableCachingNullValues();

        Map<String, RedisCacheConfiguration> initialCacheConfigurations = new HashMap<>();

        // 30 phút TTL (User Identity, Dashboards, Calorie Logs, Photo Details)
        RedisCacheConfiguration ttl30m = defaultConfig.entryTtl(Duration.ofMinutes(30));
        initialCacheConfigurations.put(USERS_CACHE, ttl30m);
        initialCacheConfigurations.put(EXPENSE_SUMMARY_CACHE, ttl30m);
        initialCacheConfigurations.put(EXPENSE_CASHFLOW_CACHE, ttl30m);
        initialCacheConfigurations.put(EXPENSE_CATEGORIES_CACHE, ttl30m);
        initialCacheConfigurations.put(EXPENSE_BUDGETS_CACHE, ttl30m);
        initialCacheConfigurations.put(CALORIE_DAILY_CACHE, ttl30m);
        initialCacheConfigurations.put(CALORIE_SUMMARY_CACHE, ttl30m);
        initialCacheConfigurations.put(PHOTO_DETAIL_CACHE, ttl30m);

        // 20 phút TTL (Friendships)
        RedisCacheConfiguration ttl20m = defaultConfig.entryTtl(Duration.ofMinutes(20));
        initialCacheConfigurations.put(FRIENDS_CACHE, ttl20m);

        // 15 phút TTL (Reactions Summary & MyPhotos)
        RedisCacheConfiguration ttl15m = defaultConfig.entryTtl(Duration.ofMinutes(15));
        initialCacheConfigurations.put(REACTIONS_SUMMARY_CACHE, ttl15m);
        initialCacheConfigurations.put(MY_PHOTOS_CACHE, ttl15m);

        // 10 phút TTL (Feed & Chat Badges / Conversations)
        RedisCacheConfiguration ttl10m = defaultConfig.entryTtl(Duration.ofMinutes(10));
        initialCacheConfigurations.put(FEED_CACHE, ttl10m);
        initialCacheConfigurations.put(CHAT_UNREAD_CACHE, ttl10m);
        initialCacheConfigurations.put(CHAT_CONVERSATIONS_CACHE, ttl10m);

        // 1 giờ TTL (Mục tiêu Calo Goal)
        RedisCacheConfiguration ttl1h = defaultConfig.entryTtl(Duration.ofHours(1));
        initialCacheConfigurations.put(CALORIE_GOAL_CACHE, ttl1h);

        return RedisCacheManager.builder(connectionFactory)
                .cacheDefaults(defaultConfig)
                .withInitialCacheConfigurations(initialCacheConfigurations)
                .build();
    }

    @Override
    public CacheErrorHandler errorHandler() {
        return new CustomCacheErrorHandler();
    }
}
