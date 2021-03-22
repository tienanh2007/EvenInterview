package evencache;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;

public class MemoryCache<T> implements Cache<T> {

    // The maximum number of items this cache should have at any given time.
    // A value of 0 means unbounded.
    // When adding a new item would put this cache over capacity,
    // the least recently accessed item(s) will be purged to make room.
    private final int maxItems;

    // To purge the least recently accessed item(s) when over capacity,
    // we implement a standard LRU cache using a doubly linked list + map.
    //
    // The doubly linked list, sorted by most recently read keys first,
    // allows us to both query the least recently read keys quickly *and*
    // move or add keys (on read & write respectively) to the front quickly,
    // while the map allows us to look items up by key quickly.
    private final LinkedList<String> mostRecentlyReadKeys;
    private final Map<String, Item> itemsByKey;
    private final Map<String, Long> expirationByKey;
    private final int TIME_BUFFER = 1000;
    // Internally, we wrap values to support additional metadata.
    private class Item {
        final String key;
        final T value;

        private Item(String key, T value) {
            this.key = key;
            this.value = value;
        }
    }

    public MemoryCache() {
        this(0);
    }

    public MemoryCache(int maxItems) {
        this.maxItems = maxItems;
        this.mostRecentlyReadKeys = new LinkedList<>();
        this.itemsByKey = new HashMap<>();
        this.expirationByKey = new HashMap<>();
    }

    /**
     * Get queries this cache for the value under the given key.
     *
     * @param key cache key
     * @return a {@link CacheResult} containing whether the key was found,
     *         and its value
     */
    @Override
    public synchronized CacheResult<T> get(String key) {

        // Do we have this key in the cache?
        if (!this.itemsByKey.containsKey(key)) {
            return new CacheResult(false, null);
        }

        if(this.expirationByKey.get(key) > System.currentTimeMillis() + TIME_BUFFER) {
            return new CacheResult(false, null);
        }
        Item item = this.itemsByKey.get(key);

        // TODO: Check for expiry, and clear if expired.

        // Mark as most recently read.
        this.mostRecentlyReadKeys.remove(key);
        this.mostRecentlyReadKeys.addLast(key);

        return new CacheResult(true, item.value);
    }

    /**
     * Caches the given value (which may be null) under the given key.
     *
     * @param key   cache key
     * @param value value to cache under the given key
     */
    @Override
    public CountDownLatch set(String key, T value) {
        return this.set(key, value, -1);
    }

    /**
     * Caches the given value (which may be null) under the given key
     * with the given expiry.
     *
     * @param key           cache key
     * @param value         value to cache under the given key
     * @param expireAfterMS duration after which value should expire,
     *                      or 0 to never expire
     */
    @Override
    public synchronized CountDownLatch set(String key, T value, long expireAfterMS) {
        // Add item.
        // TODO: Store expiry too, and clear when expired.
        Item item = new Item(key, value);
        this.mostRecentlyReadKeys.addLast(key);
        this.itemsByKey.put(key, item);
        this.expirationByKey.put(key, System.currentTimeMillis()+expireAfterMS);
        // If we're over capacity, evict least recently read items.
        while (this.maxItems > 0 && this.itemsByKey.size() > this.maxItems) {
            String oldestKey = this.mostRecentlyReadKeys.getFirst();
            this.clear(oldestKey);
        }

        return this.timedRemove(key, expireAfterMS);
    }

    private synchronized CountDownLatch timedRemove(String key, long expireAfterMS) {
      final CountDownLatch latch = new CountDownLatch(1);
      if(expireAfterMS >= 0) {
        ScheduledExecutorService scheduler
                            = Executors.newSingleThreadScheduledExecutor();
        Runnable removeTask = new Runnable() {
            public void run() {
                clear(key);
                latch.countDown();
                System.out.println(key + "was removed from the cache after" + expireAfterMS + "Milliseconds");
            }
        };
        scheduler.schedule(removeTask, expireAfterMS, TimeUnit.MILLISECONDS);
        return latch;
      }
      return null;
    }

    /**
     * Clears the value, if any, cached under the given key.
     *
     * @param key cache key
     * @return whether a value was cached (and thus cleared)
     */
    @Override
    public synchronized boolean clear(String key) {
        if (!this.itemsByKey.containsKey(key)) {
            return false;
        }

        this.mostRecentlyReadKeys.remove(key);
        this.itemsByKey.remove(key);

        return true;
    }
}
