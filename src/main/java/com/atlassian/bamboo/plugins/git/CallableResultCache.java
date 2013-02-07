package com.atlassian.bamboo.plugins.git;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import org.jetbrains.annotations.NotNull;

import java.util.Arrays;
import java.util.concurrent.Callable;

public class CallableResultCache<T>
{
    private final Cache<CachedOperationId<T>, T> cache;

    public CallableResultCache(final Cache<CachedOperationId<T>, T> cache)
    {
        this.cache = cache;
    }

    private static <T> CacheLoader<CachedOperationId<T>, T> loader()
    {
        return
                new CacheLoader<CachedOperationId<T>, T>()
                {
                    @Override
                    public T load(CachedOperationId<T> key) throws Exception
                    {
                        return key.getCallable().call();
                    }
                };
    }

    public static final class CachedOperationId<T>
    {
        private final Callable<T> callable;
        private final Object[] keys;

        public CachedOperationId(final Callable<T> callable, final Object... keys)
        {
            this.callable = callable;
            this.keys = keys;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            final CachedOperationId<T> that = (CachedOperationId<T>) o;

            return Arrays.equals(keys, that.keys);
        }

        public Callable<T> getCallable()
        {
            return callable;
        }

        @Override
        public int hashCode()
        {
            return Arrays.hashCode(keys);
        }
    }

    public static <T> CallableResultCache<T> build(@NotNull final CacheBuilder<Object, Object> builder)
    {
        final CacheLoader<CachedOperationId<T>, T> loader = loader();
        final Cache<CachedOperationId<T>, T> cache = builder.build(loader);
        return new CallableResultCache<T>(cache);
    }

    public T call(@NotNull Callable<T> callable, Object... keys)
    {
        return cache.getUnchecked(new CachedOperationId<T>(callable, keys));
    }
}
