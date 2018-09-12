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

package org.openkilda.floodlight.command;

import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.CommandProcessorService.ProcessorTask;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutionException;

public class PendingCommandSubmitter extends Command {
    private static Logger log = LoggerFactory.getLogger(PendingCommandSubmitter.class);

    private final CommandProcessorService.VerifyBatch verifyBatch;
    private final CommandProcessorService commandProcessor;

    public PendingCommandSubmitter(
            CommandContext context, CommandProcessorService.VerifyBatch verifyBatch) {
        super(context);
        this.verifyBatch = verifyBatch;
        this.commandProcessor = verifyBatch.getCommandProcessor();
    }

    @Override
    public Command execute() throws Exception {
        try {
            int count;
            int iteration = 0;
            List<ProcessorTask> tasks = verifyBatch.getTasksBatch();
            do {
                count = tasks.size();
                lookupAndSubmit(tasks);
                log.trace("iteration {} - tasks in:{} completed:{}", iteration++, count, count - tasks.size());
            } while (0 < tasks.size() && count != tasks.size());
        } finally {
            verifyBatch.close();
        }

        return null;
    }

    private void lookupAndSubmit(List<ProcessorTask> tasks) throws InterruptedException {
        for (Iterator<ProcessorTask> iterator = tasks.iterator(); iterator.hasNext(); ) {
            ProcessorTask entry = iterator.next();

            if (!entry.pendingSuccessor.isDone()) {
                continue;
            }

            iterator.remove();
            commandProcessor.markCompleted(entry);

            if (entry.pendingSuccessor.isCancelled()) {
                continue;
            }

            Command command = consumeResult(entry);
            if (command != null) {
                commandProcessor.processLazy(command);
            }
        }
    }

    private Command consumeResult(ProcessorTask task) throws InterruptedException {
        Command successor;
        try {
            successor = task.pendingSuccessor.get();
        } catch (ExecutionException e) {
            successor = task.initiator.exceptional(e.getCause());
        }
        return successor;
    }
}
