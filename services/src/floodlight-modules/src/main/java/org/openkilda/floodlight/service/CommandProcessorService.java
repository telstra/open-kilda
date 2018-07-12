/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.floodlight.service;

import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.PendingCommandSubmitter;
import org.openkilda.floodlight.utils.CommandContextFactory;

import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.core.module.IFloodlightService;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class CommandProcessorService implements IFloodlightService {
    private static final Logger log = LoggerFactory.getLogger(CommandProcessorService.class);

    private static final int FUTURE_COMPLETE_CHECK_INTERVAL = 200;
    private static final long WARN_LIMIT = 1024;
    private static final long WARN_COOLDOWN_ITERATIONS = 10;

    private final CommandContextFactory commandContextFactory;

    private ExecutorService executor = Executors.newCachedThreadPool();
    private long commandsInWork = 0;
    private long warnCooldown = -1;

    private LinkedList<ProcessorTask> tasks = new LinkedList<>();

    public CommandProcessorService(CommandContextFactory commandContextFactory) {
        this.commandContextFactory = commandContextFactory;
    }

    public void init(FloodlightModuleContext moduleContext) {
        scheduleFutureCheckTrigger(moduleContext.getServiceImpl(IThreadPoolService.class).getScheduledExecutor());
    }

    public void process(Command command) {
        processLazy(command);
        verifyPendingStatus();
    }

    /**
     * Execute command and initiate completion check.
     */
    public void process(List<Command> commands) {
        for (Command entry : commands) {
            this.processLazy(entry);
        }
        verifyPendingStatus();
    }

    /**
     * Execute command without intermediate completion check.
     */
    public void processLazy(Command command) {
        Future<Command> successor = executor.submit(command);
        synchronized (this) {
            commandsInWork += 1;
            tasks.addLast(new ProcessorTask(command, successor));
        }
    }

    /**
     * Submit pending command.
     *
     * <p>Initiator will receive exception returned by future object (if it will raise one). I.e. this interface
     * allow to wait for some background task to complete, without occupy any working thread.
     */
    public void submitPending(Command initiator, Future<Command> successor) {
        synchronized (this) {
            tasks.add(new ProcessorTask(initiator, successor));
            commandsInWork += 1;
        }
    }

    public synchronized void markCompleted(ProcessorTask task) {
        commandsInWork -= 1;
    }

    private synchronized void reSubmitPending(List<ProcessorTask> pending) {
        tasks.addAll(pending);
    }

    private void timerTrigger() {
        reportPendingCount();
        verifyPendingStatus();
    }

    private void verifyPendingStatus() {
        LinkedList<ProcessorTask> checkList = rotatePendingCommands();
        if (checkList.size() == 0) {
            return;
        }

        try {
            CommandContext context = commandContextFactory.produce();
            VerifyBatch verifyBatch = new VerifyBatch(this, checkList);
            PendingCommandSubmitter checkCommands = new PendingCommandSubmitter(context, verifyBatch);
            processLazy(checkCommands);
        } catch (Throwable e) {
            synchronized (this) {
                tasks.addAll(checkList);
            }
            throw e;
        }
    }

    private synchronized LinkedList<ProcessorTask> rotatePendingCommands() {
        LinkedList<ProcessorTask> current = tasks;
        tasks = new LinkedList<>();
        return current;
    }

    private void scheduleFutureCheckTrigger(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                timerTrigger();
            }
        }, FUTURE_COMPLETE_CHECK_INTERVAL, FUTURE_COMPLETE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    private void reportPendingCount() {
        log.trace("Pending tasks count is {}", commandsInWork);

        final long amount;
        synchronized (this) {
            amount = commandsInWork;
        }

        if (WARN_LIMIT < amount && warnCooldown < 0) {
            log.warn("Too many pending tasks - {}", amount);
            warnCooldown = WARN_COOLDOWN_ITERATIONS;
        }

        warnCooldown = warnCooldown < 0 ? -1 : warnCooldown - 1;
    }

    public static class VerifyBatch implements AutoCloseable {
        private CommandProcessorService commandProcessor;
        private final LinkedList<ProcessorTask> tasksBatch;

        VerifyBatch(CommandProcessorService commandProcessor, LinkedList<ProcessorTask> tasksBatch) {
            this.commandProcessor = commandProcessor;
            this.tasksBatch = tasksBatch;
        }

        @Override
        public void close() throws Exception {
            commandProcessor.reSubmitPending(tasksBatch);
        }

        public CommandProcessorService getCommandProcessor() {
            return commandProcessor;
        }

        public List<ProcessorTask> getTasksBatch() {
            return tasksBatch;
        }
    }

    public static class ProcessorTask {
        public final Command initiator;
        public final Future<Command> pendingSuccessor;

        public ProcessorTask(Command initiator, Future<Command> pendingSuccessor) {
            this.initiator = initiator;
            this.pendingSuccessor = pendingSuccessor;
        }
    }
}
