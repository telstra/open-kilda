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

import static org.easymock.EasyMock.capture;
import static org.easymock.EasyMock.expect;
import static org.easymock.EasyMock.expectLastCall;
import static org.easymock.EasyMock.newCapture;

import org.openkilda.floodlight.service.CommandProcessorService;
import org.openkilda.floodlight.service.CommandProcessorService.ProcessorTask;

import com.google.common.collect.ImmutableList;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.easymock.Capture;
import org.easymock.CaptureType;
import org.easymock.EasyMockSupport;
import org.easymock.Mock;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.LinkedList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class PendingCommandSubmitterTest extends EasyMockSupport {
    private final FloodlightModuleContext moduleContext = new FloodlightModuleContext();

    @Mock
    private CommandProcessorService commandProcessor;

    @Mock
    private CommandProcessorService.VerifyBatch verifyBatch;

    private Capture<Command> processCatcher = newCapture(CaptureType.ALL);

    @Before
    public void setUp() throws Exception {
        injectMocks(this);

        expect(verifyBatch.getCommandProcessor()).andReturn(commandProcessor).anyTimes();
        verifyBatch.close();

        commandProcessor.processLazy(capture(processCatcher));
        expectLastCall().andVoid().anyTimes();
    }

    @After
    public void tearDown() throws Exception {
        verifyAll();
    }

    @Test
    public void handleCanceled() throws Exception {
        @SuppressWarnings("unchecked")
        Future<Command> cancelledFuture = createMock(Future.class);
        expect(cancelledFuture.isDone()).andReturn(true);
        expect(cancelledFuture.isCancelled()).andReturn(true);

        List<ProcessorTask> initialTasks = ImmutableList.of(
                new ProcessorTask(createMock(Command.class), cancelledFuture),
                new ProcessorTask(createMock(Command.class), createPendingFuture(2)));
        LinkedList<ProcessorTask> tasks = new LinkedList<>(initialTasks);
        expect(verifyBatch.getTasksBatch()).andReturn(tasks);

        commandProcessor.markCompleted(tasks.get(0));

        replayAll();
        execute();

        Assert.assertEquals(ImmutableList.of(initialTasks.get(1)), tasks);
    }

    @Test
    public void handleCompleted() throws Exception {
        @SuppressWarnings("unchecked")
        Future<Command> completedFuture = createMock(Future.class);
        expect(completedFuture.isCancelled()).andReturn(false);
        expect(completedFuture.isDone()).andReturn(true);
        final Command completeResult = createMock(Command.class);
        expect(completedFuture.get()).andReturn(completeResult);

        List<ProcessorTask> initialTasks = ImmutableList.of(
                new ProcessorTask(createMock(Command.class), completedFuture),
                new ProcessorTask(createMock(Command.class), createPendingFuture(2)));
        LinkedList<ProcessorTask> tasks = new LinkedList<>(initialTasks);
        expect(verifyBatch.getTasksBatch()).andReturn(tasks);

        commandProcessor.markCompleted(tasks.get(0));

        replayAll();
        execute();

        Assert.assertEquals(ImmutableList.of(completeResult), processCatcher.getValues());
        Assert.assertEquals(ImmutableList.of(initialTasks.get(1)), tasks);
    }

    @Test
    public void handleExceptional() throws Exception {
        Throwable error = new NullPointerException("(testing) forced NPE");

        @SuppressWarnings("unchecked")
        Future<Command> pending = createMock(Future.class);
        expect(pending.isCancelled()).andReturn(false);
        expect(pending.isDone()).andReturn(true);
        expect(pending.get()).andThrow(new ExecutionException("(testing) error wrapper", error));

        Command initiator = createMock(Command.class);
        expect(initiator.exceptional(error)).andReturn(null);

        LinkedList<ProcessorTask> tasks = new LinkedList<>();
        tasks.add(new ProcessorTask(initiator, pending));
        expect(verifyBatch.getTasksBatch()).andReturn(tasks);

        commandProcessor.markCompleted(tasks.get(0));

        replayAll();
        execute();

        Assert.assertFalse(processCatcher.hasCaptured());
        Assert.assertTrue(tasks.isEmpty());
    }

    @Test
    public void noSecondIteration() throws Exception {
        List<ProcessorTask> initialTasks = ImmutableList.of(
                new ProcessorTask(createMock(Command.class), createPendingFuture(1)),
                new ProcessorTask(createMock(Command.class), createPendingFuture(1)));
        LinkedList<ProcessorTask> tasks = new LinkedList<>(initialTasks);
        expect(verifyBatch.getTasksBatch()).andReturn(tasks);

        replayAll();
        execute();

        Assert.assertEquals(initialTasks, tasks);
    }

    @Test
    public void secondIteration() throws Exception {
        @SuppressWarnings("unchecked")
        Future<Command> iter0Cancel = createMock(Future.class);
        expect(iter0Cancel.isDone()).andReturn(true);
        expect(iter0Cancel.isCancelled()).andReturn(true);
        @SuppressWarnings("unchecked")
        Future<Command> iter1Cancel = createMock(Future.class);
        expect(iter1Cancel.isDone()).andReturn(false).andReturn(true);
        expect(iter1Cancel.isCancelled()).andReturn(true);

        LinkedList<ProcessorTask> tasks = new LinkedList<>();
        tasks.add(new ProcessorTask(createMock(Command.class), iter1Cancel));
        tasks.add(new ProcessorTask(createMock(Command.class), iter0Cancel));
        expect(verifyBatch.getTasksBatch()).andReturn(tasks);

        commandProcessor.markCompleted(tasks.get(1));
        commandProcessor.markCompleted(tasks.get(0));

        replayAll();
        execute();

        Assert.assertTrue(tasks.isEmpty());
    }

    private Future<Command> createPendingFuture(int rounds) {
        @SuppressWarnings("unchecked")
        Future<Command> pending = createMock(Future.class);
        expect(pending.isDone()).andReturn(false).times(rounds);
        return pending;
    }

    private void execute() throws Exception {
        PendingCommandSubmitter command = new PendingCommandSubmitter(makeCommandContext(), verifyBatch);
        command.call();
    }

    private CommandContext makeCommandContext() {
        return new CommandContext(moduleContext);
    }
}
