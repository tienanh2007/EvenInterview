package evencache;

import java.util.concurrent.CountDownLatch;

public class RedisCache<T> implements Cache<T> {

    public RedisCache() {
        throw new UnsupportedOperationException("TODO: Implement");
    }

    @Override
    public synchronized CacheResult<T> get(String key) {
        throw new UnsupportedOperationException("TODO: Implement");
    }

    @Override
    public CountDownLatch set(String key, T value) {
        return this.set(key, value, 0);
    }

    @Override
    public synchronized CountDownLatch set(String key, T value, long expireAfterMS) {
        return null;
    }

    @Override
    public synchronized boolean clear(String key) {
        throw new UnsupportedOperationException("TODO: Implement");
    }
}
