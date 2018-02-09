package org.openkilda.wfm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class WatchDog {
    private static final Logger logger = LoggerFactory.getLogger(WatchDog.class);

    private final long safePeriod;
    private long lastReset;
    private boolean available = true;

    public WatchDog(float safePeriod) {
        this((long) (safePeriod * 1000));
    }

    public WatchDog(long safePeriod) {
        this.lastReset = System.currentTimeMillis();
        this.safePeriod = safePeriod;
    }

    public void reset() {
        lastReset = System.currentTimeMillis();
        logger.debug("Being kicked");
    }

    public boolean isTimeout() {
        return ! this.isAvailable();
    }

    public boolean isAvailable() {
        long current = System.currentTimeMillis();
        if (current < lastReset) {
            lastReset = current;
        }

        boolean become = (current - lastReset) < safePeriod;
        if (available != become) {
            logger.info(String.format("Become %savailable", become ? "" : "un"));
        }
        available = become;

        return available;
    }
}
