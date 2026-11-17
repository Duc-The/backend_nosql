package vn.ducthe.organic.repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import vn.ducthe.organic.exception.ApiError;
import vn.ducthe.organic.exception.ApiException;

@Component
public class RedisCart {
    private static final String ADDRESS = "@address";
    private static final String REVISION = "@revision";
    private static final String PROMOTION = "@promotion";
    private static final int MAX_LINES = 100;
    private static final int MAX_QUANTITY = 99;

    private static final DefaultRedisScript<Long> CHANGE = new DefaultRedisScript<>("""
            local key = KEYS[1]
            local field = ARGV[1]
            local mode = ARGV[2]
            local ttl = tonumber(ARGV[3])
            if mode == 'REMOVE' then
                redis.call('HDEL', key, field)
            elseif mode == 'METADATA' then
                redis.call('HSET', key, field, ARGV[4])
            else
                local previous = redis.call('HGET', key, field)
                if mode == 'SET' and not previous then return -1 end
                local lines = redis.call('HLEN', key) - redis.call('HEXISTS', key, ARGV[8])
                    - redis.call('HEXISTS', key, ARGV[9]) - redis.call('HEXISTS', key, ARGV[11])
                if not previous and lines >= tonumber(ARGV[7]) then
                    return -2
                end
                local quantity = tonumber(ARGV[4])
                if mode == 'ADD' then quantity = quantity + (tonumber(previous) or 0) end
                if quantity > tonumber(ARGV[6]) then return -2 end
                if quantity > tonumber(ARGV[5]) then return -3 end
                redis.call('HSET', key, field, quantity)
            end
            if redis.call('EXISTS', key) == 1 then
                redis.call('HSET', key, ARGV[9], ARGV[10])
                redis.call('EXPIRE', key, ttl)
            end
            return 1
            """, Long.class);
    private static final DefaultRedisScript<Long> CLEAR = new DefaultRedisScript<>("""
            if redis.call('HGET', KEYS[1], ARGV[1]) == ARGV[2] then
                return redis.call('DEL', KEYS[1])
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redis;
    private final long ttlSeconds;

    RedisCart(StringRedisTemplate redis, @Value("${app.cart.ttl:P30D}") Duration ttl) {
        this.redis = redis;
        this.ttlSeconds = ttl.toSeconds();
        if (ttlSeconds <= 0) {
            throw new IllegalArgumentException("app.cart.ttl must be positive");
        }
    }

    public Snapshot read(String customerId) {
        Map<String, Integer> quantities = new HashMap<>();
        Map<String, String> entries = redis.<String, String>opsForHash().entries(key(customerId));
        entries.forEach((field, value) -> {
            if (!ADDRESS.equals(field) && !REVISION.equals(field) && !PROMOTION.equals(field)) {
                quantities.put(field, Integer.parseInt(value));
            }
        });
        return new Snapshot(quantities, entries.get(ADDRESS), entries.get(REVISION), entries.get(PROMOTION));
    }

    public void clearIfUnchanged(String customerId, String revision) {
        if (revision != null) {
            redis.execute(CLEAR, List.of(key(customerId)), REVISION, revision);
        }
    }

    public void update(String customerId, String variantId, int quantity, int available, boolean add) {
        change(customerId, variantId, add ? "ADD" : "SET", Integer.toString(quantity), available);
    }

    public void remove(String customerId, String variantId) {
        change(customerId, variantId, "REMOVE", "", 0);
    }

    public void selectAddress(String customerId, String addressId) {
        change(customerId, ADDRESS, "METADATA", addressId, 0);
    }

    public void setPromotion(String customerId, String code) {
        change(customerId, PROMOTION, code == null ? "REMOVE" : "METADATA", code == null ? "" : code, 0);
    }

    private void change(String customerId, String field, String mode, String value, int available) {
        long result = redis.execute(CHANGE, List.of(key(customerId)), field, mode, Long.toString(ttlSeconds),
                value, Integer.toString(available), Integer.toString(MAX_QUANTITY), Integer.toString(MAX_LINES),
                ADDRESS, REVISION, UUID.randomUUID().toString(), PROMOTION);
        switch ((int) result) {
            case -1 -> throw new ApiException(ApiError.RESOURCE_NOT_FOUND);
            case -2 -> throw new ApiException(ApiError.CART_LIMIT);
            case -3 -> throw new ApiException(ApiError.INSUFFICIENT_STOCK);
            default -> { }
        }
    }

    private static String key(String customerId) {
        return "organic:cart:v1:" + customerId;
    }

    public record Snapshot(Map<String, Integer> quantities, String addressId, String revision, String promotionCode) {}
}
