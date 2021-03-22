package evencache;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.*;

public class DedupingLoader<T> {

    private final ExecutorService executor;
    private final Map<String, Future<T>> futuresByKey;

    public DedupingLoader() {
        executor = Executors.newFixedThreadPool(100);
        futuresByKey = new HashMap<>();
    }

    /**
     * If a load is already in progress for the given key,
     * waits for it to finish and returns its result.
     * If not, calls the given load function and returns its result.
     */
    public T loadOrAwait(String key, Callable<T> load) throws ExecutionException, InterruptedException {
        Future<T> future;

        // Take a lock for the purpose of mutating the map.
        // Unlock it before waiting (potentially a long time) for the load to finish.
        synchronized (this) {
            future = this.futuresByKey.get(key);

            if (future == null) {
                // This is the first concurrent call for this key.
                // Call the load function async'ly and save the future for other concurrent calls.
                // Clear the future when complete to avoid holding onto data forever;
                // all other concurrent calls will have already gotten a reference to it.
                future = executor.submit(() -> {
                    try {
                        return load.call();
                    } finally {
                        clear(key);
                    }
                });
                futuresByKey.put(key, future);
            }
        }

        try {
            return load.call();
        } catch (Exception e) {
            throw new ExecutionException(e);
        }
    }

    private synchronized void clear(String key) {
        futuresByKey.remove(key);
    }
}
