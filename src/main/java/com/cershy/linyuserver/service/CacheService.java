package com.cershy.linyuserver.service;

import lombok.Getter;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Service
public class CacheService {
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public void setWithExpiry(String key, String value, Duration expiry) {
        long expiryTime = System.currentTimeMillis() + expiry.toMillis();
        cache.put(key, new CacheEntry(value, expiryTime));
    }

    public String get(String key) {
        CacheEntry entry = cache.get(key);
        if (entry != null && !entry.isExpired()) {
            return entry.getValue();
        }
        cache.remove(key);
        return null;
    }

    public void delete(String key) {
        cache.remove(key);
    }

    private static class CacheEntry {
        @Getter
        private final String value;
        private final long expiryTime;

        public CacheEntry(String value, long expiryTime) {
            this.value = value;
            this.expiryTime = expiryTime;
        }

        public boolean isExpired() {
            return System.currentTimeMillis() > expiryTime;
        }
    }
}


