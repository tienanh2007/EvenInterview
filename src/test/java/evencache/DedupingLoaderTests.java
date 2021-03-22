package evencache;

import org.junit.jupiter.api.Test;

import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

public class DedupingLoaderTests {

    // HACK: Unlike e.g. JavaScript with its single-threaded event loop,
    // threads in Java are not guaranteed to execute in any particular order.
    // So for tests, where order & determinism are helpful for simpler tests,
    // sleep a little bit of time to give threads a chance to actually *begin*.
    static int THREAD_START_TIME_MS = 50;

    static ExecutorService executor = Executors.newFixedThreadPool(100);

    /**
     * Runs DedupingLoader.loadOrAwait with the given params asynchronously.
     * Waits a bit of time for the function to actually start in the background.
     */
    private <T> Future<T> loadOrAwaitAsync(DedupingLoader<T> loader, String key, Callable<T> load) throws InterruptedException {
        Future<T> future = executor.submit(() -> loader.loadOrAwait(key, load));
        Thread.sleep(THREAD_START_TIME_MS);
        return future;
    }

    private void assertFutureExceptionEquals(Future future, Exception expected) {
        try {
            future.get();
            fail(); // We shouldn't get here, since there should've been an exception.
        } catch (ExecutionException e) {
            assertEquals(expected, unwrapExecutionException(e));
        } catch (InterruptedException e) {
            fail(e); // This path shouldn't happen.
        }
    }

    // Horrible code to unwrap an arbitrary number of ExecutionExceptions.
    private Throwable unwrapExecutionException(Throwable e) {
        if (e instanceof ExecutionException) {
            return unwrapExecutionException(e.getCause());
        } else {
            return e;
        }
    }

    /**
     * MockResource allows tests to provide a load function to DedupingLoader that:
     * • Waits for the test to signal before returning.
     * • Returns the specified result (or optionally throws an exception instead).
     * • Tracks the number of calls it receives.
     */
    class MockResource<T> {
        private int numLoadCalls = 0;
        private boolean isWaiting = false;
        private T result;
        private Exception exception = null;

        MockResource(T result) {
            this.result = result;
        }

        /** The load function that should be passed to DedupingLoader. */
        private synchronized T load() throws Exception {
            this.numLoadCalls++;
            isWaiting = true;
            while (isWaiting) {
                wait();
            }
            if (this.exception != null) {
                throw this.exception;
            }
            return this.result;
        }

        private synchronized void finishLoad() {
            isWaiting = false;
            notifyAll();
        }

        private void fail() {
            this.exception = new Exception(this.result.toString());
            this.finishLoad();
        }
    }

    /**
     * Calling loadOrAwait multiple times with the same key but different load functions
     * should call only the first load function -- and only call it once until it completes.
     */
    @Test
    void singleKey() throws InterruptedException, ExecutionException {
        DedupingLoader<String> loader = new DedupingLoader();

        String key = "foo";

        MockResource<String> mockResourceA = new MockResource("A");
        MockResource<String> mockResourceB = new MockResource("B");
        MockResource<String> mockResourceC = new MockResource("C");

        Future<String> futureA = loadOrAwaitAsync(loader, key, () -> mockResourceA.load());
        assertEquals(1, mockResourceA.numLoadCalls);
        assertTrue(!futureA.isDone());

        Future<String> futureB = loadOrAwaitAsync(loader, key, () -> mockResourceB.load());
        assertEquals(0, mockResourceB.numLoadCalls); // new load func should not have been called!
        assertEquals(1, mockResourceA.numLoadCalls); // in-progress load func should not have been called *again*!
        assertTrue(!futureB.isDone());
        assertTrue(!futureA.isDone()); // still

        Future<String> futureC = loadOrAwaitAsync(loader, key, () -> mockResourceC.load());
        assertEquals(0, mockResourceC.numLoadCalls); // new load func should not have been called!
        assertEquals(1, mockResourceA.numLoadCalls); // in-progress load func should not have been called *again*!
        assertTrue(!futureC.isDone());
        assertTrue(!futureA.isDone()); // still

        // Once the first load function finishes, all callers should get its same result.
        // Have this first load fail (throw an exception) instead of load successfully.

        mockResourceA.fail();

        assertFutureExceptionEquals(futureA, mockResourceA.exception); // all function A's exception
        assertFutureExceptionEquals(futureB, mockResourceA.exception);
        assertFutureExceptionEquals(futureC, mockResourceA.exception);

        assertTrue(futureA.isDone());

        // Results should not be cached.

        MockResource<String> mockResourceD = new MockResource("D");
        MockResource<String> mockResourceE = new MockResource("E");

        Future<String> futureD = loadOrAwaitAsync(loader, key, () -> mockResourceD.load());
        assertEquals(1, mockResourceD.numLoadCalls);
        assertEquals(1, mockResourceA.numLoadCalls); // shouldn't have been called again! still 1
        assertTrue(!futureD.isDone());

        Future<String> futureE = loadOrAwaitAsync(loader, key, () -> mockResourceE.load());
        assertEquals(0, mockResourceE.numLoadCalls); // new load should not have been called!
        assertEquals(1, mockResourceD.numLoadCalls); // in-progress load should not have been called *again*!
        assertTrue(!futureE.isDone());
        assertTrue(!futureD.isDone()); // still

        // Once this new load function finishes, all current callers should get its same result.
        // Load *successfully* this time.

        mockResourceD.finishLoad();

        assertEquals("D", futureD.get()); // all function D's result
        assertEquals("D", futureE.get());
    }

    /**
     * Calling loadOrAwait with separate keys should properly use the separate keys' functions.
     * One function's results shouldn't leak to other functions' calls.
     */
    @Test
    void multipleKeys() throws InterruptedException, ExecutionException {
        DedupingLoader<String> loader = new DedupingLoader();

        String keyA = "a";
        String keyB = "b";
        String keyC = "c";

        MockResource<String> mockResourceA = new MockResource("A");
        MockResource<String> mockResourceB = new MockResource("B");
        MockResource<String> mockResourceC = new MockResource("C");

        Future<String> futureA = loadOrAwaitAsync(loader, keyA, () -> mockResourceA.load());
        assertEquals(1, mockResourceA.numLoadCalls);
        assertTrue(!futureA.isDone());

        Future<String> futureB = loadOrAwaitAsync(loader, keyB, () -> mockResourceB.load());
        assertEquals(1, mockResourceB.numLoadCalls); // this key's load func *should* have been called!
        assertEquals(1, mockResourceA.numLoadCalls); // other keys' load funcs should *not* have been called!
        assertTrue(!futureB.isDone());
        assertTrue(!futureA.isDone()); // still

        Future<String> futureC = loadOrAwaitAsync(loader, keyC, () -> mockResourceC.load());
        assertEquals(1, mockResourceC.numLoadCalls); // this key's load func *should* have been called!
        assertEquals(1, mockResourceB.numLoadCalls); // other keys' load funcs should *not* have been called!
        assertEquals(1, mockResourceA.numLoadCalls);
        assertTrue(!futureC.isDone());
        assertTrue(!futureB.isDone());
        assertTrue(!futureA.isDone());

        // One function finishing shouldn't affect other functions' calls.
        // (Have this function fail.)

        mockResourceB.fail();

        assertFutureExceptionEquals(futureB, mockResourceB.exception);
        assertTrue(!futureC.isDone());
        assertTrue(futureB.isDone());
        assertTrue(!futureA.isDone());

        // Ditto for another function. (Have this function succeed.)

        mockResourceC.finishLoad();
        assertEquals(mockResourceC.result, futureC.get());
        assertTrue(!futureA.isDone());
    }
}
