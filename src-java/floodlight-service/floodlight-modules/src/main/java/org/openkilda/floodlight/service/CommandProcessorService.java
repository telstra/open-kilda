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

import static org.openkilda.floodlight.utils.Utils.mapToString;

import org.openkilda.floodlight.KildaCore;
import org.openkilda.floodlight.KildaCoreConfig;
import org.openkilda.floodlight.command.Command;
import org.openkilda.floodlight.command.CommandContext;
import org.openkilda.floodlight.command.CommandWrapper;
import org.openkilda.floodlight.command.PendingCommandSubmitter;
import org.openkilda.floodlight.utils.CommandContextFactory;
import org.openkilda.floodlight.utils.Utils;

import lombok.Value;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import net.floodlightcontroller.threadpool.IThreadPoolService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

public class CommandProcessorService implements IService {
    private static final Logger log = LoggerFactory.getLogger(CommandProcessorService.class);

    private static final int FUTURE_COMPLETE_CHECK_INTERVAL = 200;
    private static final long REJECTED_REPORT_INTERVAL = 1000;
    private static final long REJECTED_ERROR_LIMIT = 1024;

    private final KildaCore kildaCore;
    private final CommandContextFactory commandContextFactory;

    private ThreadPoolExecutor executor;

    private LinkedList<ProcessorTask> tasks = new LinkedList<>();
    private final LinkedList<Runnable> rejectedQueue = new LinkedList<>();
    private long lastRejectCountReportedAt = 0;

    public CommandProcessorService(KildaCore kildaCore, CommandContextFactory commandContextFactory) {
        this.kildaCore = kildaCore;
        this.commandContextFactory = commandContextFactory;
    }

    /**
     * Service initialize(late) method.
     */
    @Override
    public void setup(FloodlightModuleContext moduleContext) {
        KildaCoreConfig config = kildaCore.getConfig();

        log.info("config - persistent workers = {}", config.getCommandPersistentWorkersCount());
        log.info("config - workers limit = {}", config.getCommandWorkersLimit());
        log.info("config - idle workers keep alive seconds = {}", config.getCommandIdleWorkersKeepAliveSeconds());
        log.info("config - deferred requests limit = {}", config.getCommandDeferredRequestsLimit());

        executor = new ThreadPoolExecutor(
                config.getCommandPersistentWorkersCount(), config.getCommandWorkersLimit(),
                config.getCommandIdleWorkersKeepAliveSeconds(), TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getCommandDeferredRequestsLimit()),
                new RejectedExecutor(this));
        executor.prestartAllCoreThreads();

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
        command = wrapCommand(command);
        if (command.isOneShot()) {
            executeOneShot(command);
        } else {
            executeChainResult(command);
        }
    }

    private Command wrapCommand(Command target) {
        return new CommandWrapper(target);
    }

    private void executeOneShot(Command command) {
        executor.execute(new CommandExecutor(command));
    }

    private void executeChainResult(Command command) {
        Future<Command> successor = executor.submit(command);
        synchronized (this) {
            tasks.addLast(new ProcessorTask(command, successor));
        }
    }

    private synchronized void reSubmitPending(List<ProcessorTask> pending) {
        tasks.addAll(pending);
    }

    private void handleExecutorReject(Runnable command) {
        synchronized (rejectedQueue) {
            rejectedQueue.addLast(command);
        }
    }

    private void timerTrigger() {
        pushRejected();
        verifyPendingStatus();
    }

    private void pushRejected() {
        if (executor.isShutdown()) {
            return;
        }

        BlockingQueue<Runnable> queue = executor.getQueue();
        int count;
        int activeWorkerCount;
        String rejectedCommandsDescription;
        synchronized (rejectedQueue) {
            while (!rejectedQueue.isEmpty()) {
                Runnable entry = rejectedQueue.getFirst();
                if (queue.offer(entry)) {
                    rejectedQueue.removeFirst();
                    continue;
                }

                break;
            }
            count = rejectedQueue.size();
            activeWorkerCount = executor.getActiveCount();
            rejectedCommandsDescription = getRejectedCommandsDescription(rejectedQueue);
        }

        reportQueueStatus(count, activeWorkerCount, rejectedCommandsDescription);
    }

    private static String getRejectedCommandsDescription(List<Runnable> queue) {
        Map<String, Integer> commandsByName = new HashMap<>();
        for (Runnable runnable : queue) {
            String name = getCommandName(runnable);
            commandsByName.put(name, commandsByName.getOrDefault(name, 0) + 1);
        }
        return mapToString(commandsByName);
    }

    private static String getCommandName(Runnable runnable) {
        if (runnable instanceof CommandExecutor) {
            CommandExecutor executor = ((CommandExecutor) runnable);
            return executor.getCommandName();
        } else {
            return Utils.getClassName(runnable.getClass());
        }
    }

    private void verifyPendingStatus() {
        LinkedList<ProcessorTask> checkList = rotatePendingCommands();
        if (checkList.isEmpty()) {
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

    private void reportQueueStatus(int rejectedQueueSize, int activeWorkerCount, String rejectedCommandsDescription) {
        if (0 < rejectedQueueSize) {
            long now = System.currentTimeMillis();
            if (lastRejectCountReportedAt + REJECTED_REPORT_INTERVAL < now) {
                lastRejectCountReportedAt = now;

                String message = String.format(
                        "Rejected commands queue size: %d (workers: %d / %d). Command classes: %s",
                        rejectedQueueSize, activeWorkerCount, executor.getPoolSize(), rejectedCommandsDescription);
                if (rejectedQueueSize < REJECTED_ERROR_LIMIT) {
                    log.warn(message);
                } else {
                    log.error(message);
                }
            }
        } else if (0 < lastRejectCountReportedAt) {
            log.warn("All rejected command have been submitted into executor");
            lastRejectCountReportedAt = 0;
        }
    }

    private synchronized LinkedList<ProcessorTask> rotatePendingCommands() {
        LinkedList<ProcessorTask> current = tasks;
        tasks = new LinkedList<>();
        return current;
    }

    private void scheduleFutureCheckTrigger(ScheduledExecutorService scheduler) {
        scheduler.scheduleAtFixedRate(
                this::timerTrigger,
                FUTURE_COMPLETE_CHECK_INTERVAL, FUTURE_COMPLETE_CHECK_INTERVAL, TimeUnit.MILLISECONDS);
    }

    public static class VerifyBatch implements AutoCloseable {
        private CommandProcessorService commandProcessor;
        private final LinkedList<ProcessorTask> tasksBatch;

        VerifyBatch(CommandProcessorService commandProcessor, LinkedList<ProcessorTask> tasksBatch) {
            this.commandProcessor = commandProcessor;
            this.tasksBatch = tasksBatch;
        }

        @Override
        public void close() {
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

    private static class RejectedExecutor implements RejectedExecutionHandler {
        private final CommandProcessorService commandProcessor;

        RejectedExecutor(CommandProcessorService commandProcessor) {
            this.commandProcessor = commandProcessor;
        }

        @Override
        public void rejectedExecution(Runnable r, ThreadPoolExecutor executor) {
            commandProcessor.handleExecutorReject(r);
        }
    }

    @Value
    private static class CommandExecutor implements Runnable {
        Command command;

        @Override
        public void run() {
            try {
                command.call();
            } catch (Exception e) {
                command.exceptional(e);
            }
        }

        public String getCommandName() {
            return command.getName();
        }
    }
}
