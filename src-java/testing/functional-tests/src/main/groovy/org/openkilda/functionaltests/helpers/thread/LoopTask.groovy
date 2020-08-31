package org.openkilda.functionaltests.helpers.thread

import java.util.concurrent.Callable

class LoopTask implements Runnable {
    private volatile boolean stop
    private Callable action

    LoopTask(Callable action) {
        this.action = action
    }

    @Override
    void run() {
        stop = false
        while (!stop) { action() }
    }

    void stop() {
        stop = true
    }
}
