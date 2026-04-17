package io.oneiros.pool.lucid;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * A highly optimized, lock-free, zero-allocation object pool for Oneiros-Core.
 * It prevents garbage collection lags by recycling objects.
 *
 * @param <T> The type of objects to be pooled.
 */
public final class LucidPool<T> {

    private final Queue<T> pool;
    private final Supplier<T> factory;
    private final Consumer<T> resetter;

    /**
     * @param factory  The factory used to create new objects when the pool is empty.
     * @param resetter The logic to reset an object to its default state before putting it back into the pool.
     */
    private LucidPool(Supplier<T> factory, Consumer<T> resetter) {
        // ConcurrentLinkedQueue is non-blocking and lock-free
        this.pool = new ConcurrentLinkedQueue<>();
        this.factory = factory;
        this.resetter = resetter;
    }

    /**
     * Creates a new LucidPool.
     *
     * @param factory  Function to generate new objects.
     * @param resetter Function to clean/reset the object when it returns to the pool.
     * @param <T>      Type of the object.
     * @return A new instance of LucidPool.
     */
    public static <T> LucidPool<T> create(Supplier<T> factory, Consumer<T> resetter) {
        return new LucidPool<>(factory, resetter);
    }

    /**
     * Acquires an object from the pool. If the pool is empty, a new object is created.
     *
     * @return An instance of T ready to be used.
     */
    public T acquire() {
        T object = pool.poll();
        if (object == null) {
            object = factory.get();
        }
        return object;
    }

    /**
     * Returns an object to the pool for reuse.
     *
     * @param object The object to release.
     */
    public void release(T object) {
        if (object != null) {
            resetter.accept(object); // Reset state to prevent data leaks
            pool.offer(object);
        }
    }
}
