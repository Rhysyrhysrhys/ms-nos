package com.workshare.msnos.usvc.api.routing.strategies;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.workshare.msnos.usvc.Microservice;
import com.workshare.msnos.usvc.api.routing.ApiEndpoint;
import com.workshare.msnos.usvc.api.routing.RoutingStrategy;

public class CachingRoutingStrategy implements RoutingStrategy {

    public static final String SYSP_TIMEOUT = "com.ws.nsnos.usvc.api.routing.strategy.caching.timeout";

    private final RoutingStrategy delegate;

    private long timeout;
    private List<ApiEndpoint> result;

    private long lastrun;
    private long lastend;

    public CachingRoutingStrategy(RoutingStrategy delegate) {
        this.delegate = delegate;
        this.timeout = getDefaultTimeout();
        this.result = Collections.emptyList();

        this.lastrun = System.currentTimeMillis();
        this.lastend = lastrun - 1;
    }

    private static long getDefaultTimeout() {
        return Long.getLong(SYSP_TIMEOUT, 250l);
    }

    private void reset() {
        this.lastrun = System.currentTimeMillis();
        this.lastend = System.currentTimeMillis() + timeout;
    }

    @Override
    public List<ApiEndpoint> select(Microservice from, List<ApiEndpoint> apis) {
        if (timeout == 0L) {
            return delegate.select(from, apis);
        }

        if (System.currentTimeMillis() > lastend) {
            reset();
            result = delegate.select(from, apis);
        }

        return result;
    }

    public CachingRoutingStrategy withTimeout(int duration, TimeUnit unit) {
        this.timeout = TimeUnit.MILLISECONDS.convert(duration, unit);
        return this;
    }
}