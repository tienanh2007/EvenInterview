package evencache;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ReadThroughCache<T> {

    /**
     * Wrapper around values to contain metadata specific to ReadThroughCache.
     */
    private class Entry {
        final T value;
        final long expiresAtMS; // 0 means don't expire
        final long loadDurationMS;

        private Entry(T value, long expiresAtMS, long loadDurationMS) {
            this.value = value;
            this.expiresAtMS = expiresAtMS;
            this.loadDurationMS = loadDurationMS;
        }
    }

    /**
     * Wrapper for the result of a load function, so we can include an expiry.
     * Package-private (instead of private) for testing.
     */
    static class LoadResultWithExpiry<T> {
        final T value;
        final long expireAfterMS; // 0 means never expire

        LoadResultWithExpiry(T value, long expireAfterMS) {
            this.value = value;
            this.expireAfterMS = expireAfterMS;
        }
    }

    protected final Cache<Entry> cache; // allow tests to access
    private final DedupingLoader<T> loader;
    private final ExecutorService executor;

    public ReadThroughCache(Cache<Entry> cache) {
        this.cache = cache;
        this.loader = new DedupingLoader<>();
        this.executor = Executors.newFixedThreadPool(100);
    }

    /**
     * Queries this cache for the value under the given key.
     * If found (& unexpired), returns it.
     * If not found (or expired), calls the given load function and
     * caches & returns its result.
     *
     * (NOTE: Only one load function is called at a time per key,
     * so if there's already another load in progress for this key,
     * awaits that load and uses its results rather than calling this load.)
     */
    public T get(String key, Callable<LoadResultWithExpiry<T>> load)
            throws ExecutionException, InterruptedException {

        // If the result is in our cache and not expired yet, return it directly.

        CacheResult<Entry> result = this.cache.get(key);
        if (result.cached) {
            Entry entry = result.value;
            if (shouldRefreshEagerly(entry)) {
                tryRefreshAsync(key, load);
            }
            return entry.value;
        }

        // Otherwise, load from our underlying data source & cache the result.

        return this.refresh(key, load);
    }

    /**
     * Calls the given load function, updates this cache with its result,
     * and returns that result.
     *
     * (NOTE: Only one load function is called at a time per key,
     * so if there's already another load in progress for this key,
     * awaits that load and uses its results rather than calling this load.)
     */
    public T refresh(String key, Callable<LoadResultWithExpiry<T>> load)
            throws ExecutionException, InterruptedException {
        // Guard against concurrent calls; only refresh once at a time.

        return this.loader.loadOrAwait(key, () -> {
            // Load data, timing how long it takes.

            long loadStartTimeMS = System.currentTimeMillis();
            LoadResultWithExpiry<T> loadResult = load.call();
            long loadDurationMS = System.currentTimeMillis() - loadStartTimeMS;

            // Update our cache with the results.

            long expiresAtMS = 0;
            if (loadResult.expireAfterMS > 0) {
                expiresAtMS = loadStartTimeMS + loadResult.expireAfterMS;
            }
            Entry entry = new Entry(loadResult.value, expiresAtMS, loadDurationMS);
            this.cache.set(key, entry, loadResult.expireAfterMS);

            return entry.value;
        });
    }

    private void tryRefreshAsync(String key, Callable<LoadResultWithExpiry<T>> load) {
        executor.submit(() -> {
            try {
                refresh(key, load);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }

    /**
     * Returns whether we should refresh the given cache entry eagerly --
     * before it actually expires, and using probabilistic random jitter --
     * to help prevent cache stampedes.
     *
     * Specifically, directly implements the algorithm documented here,
     * with `beta` set to 1:
     * https://en.wikipedia.org/wiki/Cache_stampede#Probabilistic_early_expiration
     */
    private boolean shouldRefreshEagerly(Entry entry) {
        if (entry.expiresAtMS == 0) {
            return false; // doesn't expire, so no need to refresh
        }

        long randomizedDeltaMS = (long) (entry.loadDurationMS * Math.log(Math.random()));
        return System.currentTimeMillis() + randomizedDeltaMS >= entry.expiresAtMS;
    }
}
