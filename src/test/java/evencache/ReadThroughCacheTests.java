package evencache;

import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.concurrent.Callable;

import static org.junit.jupiter.api.Assertions.*;

public class ReadThroughCacheTests {

    /** Returns a random number (with no expiry) on every invocation. */
    private final Callable<ReadThroughCache.LoadResultWithExpiry<Double>> loadRandomNumber = () -> {
        return new ReadThroughCache.LoadResultWithExpiry(Math.random(), 0);
    };

    /** Throws a random exception on every invocation. */
    private final Callable<ReadThroughCache.LoadResultWithExpiry<Double>> loadRandomError = () -> {
        throw new Exception("Error with random number " + Math.random());
    };

    /**
     * Requesting items from the cache should load the items the first time and then
     * return the cached values for subsequent requests, until cleared (whether due
     * to expiry or capacity or manually cleared). For simplicity, never expire
     * items, and have an unbounded cache capacity
     */
    @Test
    void basic() {
        ReadThroughCache<Double> cache = new ReadThroughCache<>(new MemoryCache<>());

        String keyA = "a";
        String keyB = "b";

        // We'll be testing via load functions that return different random values
        // on every invocation; sanity check that assumption.

        double rand1 = 0, rand2 = 0;
        try {
            rand1 = loadRandomNumber.call().value;
            rand2 = loadRandomNumber.call().value;
        } catch (Exception e) {
            fail(e);
        }
        assertNotEquals(rand1, rand2);

        // Get first item; should call load function and return value.

        double valA = getCachedValueOrFail(cache, keyA, loadRandomNumber);

        double expectedValA = valA;

        // Get first item again; should return cached value, not call load function again.
        // Have the load function even return an error this time; shouldn't matter.

        valA = getCachedValueOrFail(cache, keyA, loadRandomError);
        assertEquals(expectedValA, valA);

        // Get second item; should call load function (since different key) and return value.
        // Have the load function return an error. Any returned expiry or value should get ignored.

        assertCachedValueThrowsException(cache, keyB, loadRandomError);

        // Get second item again, this time with a load function that returns successfully.
        // The error should *not* have been cached! So this load function should be called.

        double valB = getCachedValueOrFail(cache, keyB, loadRandomNumber);
        assertNotEquals(expectedValA, valB);

        double expectedValB = valB;

        // Clear first item. The second item should still be cached, but requesting
        // the first item should call the load function again.

        cache.cache.clear(keyA);

        valB = getCachedValueOrFail(cache, keyB, loadRandomNumber);
        assertEquals(expectedValB, valB);

        valA = getCachedValueOrFail(cache, keyA, loadRandomNumber);
        assertNotEquals(expectedValA, valA);
    }

    /**
     * ReadThroughCache should eagerly -- but asynchronously -- refresh
     * items on read, with random jitter to prevent cache stampedes.
     */
    @Test
    @Disabled
    void eagerAsyncRefresh() {
        // TODO: Implement test! (How, given randomness & asynchronousness?)
    }

    /**
     * Helper function to get the cached value and fail the test on any exception.
     */
    private double getCachedValueOrFail(ReadThroughCache<Double> cache, String key,
            Callable<ReadThroughCache.LoadResultWithExpiry<Double>> load) {

        try {
            return cache.get(key, load);
        } catch (Exception e) {
            fail(e);
            return 0;
        }
    }

    private void assertCachedValueThrowsException(ReadThroughCache<Double> cache, String key,
            Callable<ReadThroughCache.LoadResultWithExpiry<Double>> load) {
        try {
            cache.get(key, load);
            fail(); // if we get here, the operation succeeded when we expected it to fail.
        } catch (Exception e) {
            // Expected
        }
    }
}
