package com.codegym.locketclone.common.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.Cache;
import org.springframework.cache.interceptor.CacheErrorHandler;

/**
 * Bộ xử lý lỗi Cache (Graceful Degradation).
 * Khi Upstash Redis gặp sự cố timeout, mất mạng hoặc kết nối chập chờn,
 * bộ xử lý này sẽ bắt ngoại lệ và ghi log WARN, cho phép ứng dụng fallback
 * trực tiếp về cơ sở dữ liệu quan hệ (Neon/Mongo) mà không ném lỗi HTTP 500 sập API.
 */
@Slf4j
public class CustomCacheErrorHandler implements CacheErrorHandler {

    @Override
    public void handleCacheGetError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Redis GET error on cache '{}', key '{}': {}. Falling back to DB directly.",
                cache != null ? cache.getName() : "unknown", key, exception.getMessage());
    }

    @Override
    public void handleCachePutError(RuntimeException exception, Cache cache, Object key, Object value) {
        log.warn("Redis PUT error on cache '{}', key '{}': {}. Proceeding without caching.",
                cache != null ? cache.getName() : "unknown", key, exception.getMessage());
    }

    @Override
    public void handleCacheEvictError(RuntimeException exception, Cache cache, Object key) {
        log.warn("Redis EVICT error on cache '{}', key '{}': {}. Proceeding without eviction.",
                cache != null ? cache.getName() : "unknown", key, exception.getMessage());
    }

    @Override
    public void handleCacheClearError(RuntimeException exception, Cache cache) {
        log.warn("Redis CLEAR error on cache '{}': {}. Proceeding.",
                cache != null ? cache.getName() : "unknown", exception.getMessage());
    }
}
