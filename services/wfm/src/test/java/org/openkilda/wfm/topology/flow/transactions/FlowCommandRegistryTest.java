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

package org.openkilda.wfm.topology.flow.transactions;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.FlowCommandGroup;
import org.openkilda.messaging.command.flow.FlowCommandGroup.FailureReaction;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.model.SwitchId;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class FlowCommandRegistryTest {
    private static final String TEST_FLOW = "test-flow";
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final BaseInstallFlow FLOW_COMMAND_1 =
            new BaseInstallFlow(UUID.randomUUID(), TEST_FLOW, 0L, SWITCH_ID_1, 1, 1);
    private static final RemoveFlow FLOW_COMMAND_2 =
            new RemoveFlow(UUID.randomUUID(), TEST_FLOW, 0L, SWITCH_ID_1, null, null);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldNotPollBeforeRegistration() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<BaseFlow> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(currentGroup, empty());
    }

    @Test
    public void failToLocateTransactionBeforeRegistration() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        assertFalse(registry.hasCommand(TEST_FLOW));

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
    }

    @Test
    public void failToLocateBatchBeforeRegistration() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownTransactionException.class);
        registry.removeBatch(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
    }

    @Test
    public void failToRegisterTransactionWithWrongFlowId() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<FlowCommandGroup> groups = asList(
                new FlowCommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new FlowCommandGroup(singletonList(FLOW_COMMAND_2), FailureReaction.IGNORE));

        thrown.expect(IllegalArgumentException.class);
        registry.registerBatch(TEST_FLOW + "_fake", groups);
    }

    @Test
    public void failToRegisterDuplicationTransaction() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<FlowCommandGroup> groups = asList(
                new FlowCommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new FlowCommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE));

        thrown.expect(IllegalArgumentException.class);
        registry.registerBatch(TEST_FLOW, groups);
    }

    @Test
    public void shouldNotPollNextGroupBeforeCommandsRemoved() {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        List<BaseFlow> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_1, currentGroup.get(0));

        List<BaseFlow> nextGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(nextGroup, empty());
    }

    @Test
    public void shouldPollNextGroupWhenCurrentGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());

        List<BaseFlow> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_2, currentGroup.get(0));
    }

    @Test
    public void shouldLocateCommandOnActiveBatch() {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        assertTrue(registry.hasCommand(TEST_FLOW));
        List<BaseFlow> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_1, currentGroup.get(0));
        assertTrue(registry.hasCommand(TEST_FLOW));
    }

    @Test
    public void failToLocateWrongTransaction() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW, UUID.randomUUID());
    }

    @Test
    public void failToLocateFlowByWrongIdAndCorrectTransaction() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW + "_fake", FLOW_COMMAND_1.getTransactionId());
    }

    @Test
    public void failToLocateBatchByWrongTransaction() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        thrown.expect(UnknownTransactionException.class);
        registry.removeBatch(TEST_FLOW, UUID.randomUUID());
    }

    @Test
    public void shouldRemoveBatchWhenAllGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        assertFalse(registry.hasCommand(TEST_FLOW));
    }

    @Test
    public void failToLocateTransactionWhenAllGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());
    }

    @Test
    public void failToRemoveBatchWhenAllGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());
    }

    @Test
    public void shouldNotPollWhenAllGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        List<BaseFlow> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(currentGroup, empty());
    }

    @Test
    public void shouldReturnFlowForExpiredBatch() throws InterruptedException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        Set<String> affectedFlows = Failsafe.with(new RetryPolicy()
                .withDelay(10, TimeUnit.MILLISECONDS)
                .withMaxDuration(100, TimeUnit.MILLISECONDS)
                .retryIf(result -> ((Set) result).isEmpty()))
                .get(() -> registry.removeExpiredBatch(Duration.ZERO));
        assertThat(affectedFlows, hasItem(TEST_FLOW));
    }

    private void registerBatchWith2Groups(FlowCommandRegistry registry) {
        List<FlowCommandGroup> groups = asList(
                new FlowCommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new FlowCommandGroup(singletonList(FLOW_COMMAND_2), FailureReaction.IGNORE));
        registry.registerBatch(TEST_FLOW, groups);
    }
}
