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
import static java.util.Collections.emptyList;
import static java.util.Collections.singletonList;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.hasItem;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandGroup;
import org.openkilda.messaging.command.CommandGroup.FailureReaction;
import org.openkilda.messaging.command.flow.BaseInstallFlow;
import org.openkilda.messaging.command.flow.DeallocateFlowResourcesRequest;
import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.PathId;
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
    private static final UUID FAKE_BATCH_ID = UUID.randomUUID();
    private static final SwitchId SWITCH_ID_1 = new SwitchId("00:00:00:00:00:00:00:01");
    private static final BaseInstallFlow FLOW_COMMAND_1 =
            new BaseInstallFlow(UUID.randomUUID(), TEST_FLOW, 0L, SWITCH_ID_1, 1, 1, false);
    private static final RemoveFlow FLOW_COMMAND_2 =
            new RemoveFlow(UUID.randomUUID(), TEST_FLOW, 0L, SWITCH_ID_1, null, null, false);
    private static final CommandData NON_FLOW_COMMAND =
            new DeallocateFlowResourcesRequest(TEST_FLOW, 0, new PathId(UUID.randomUUID().toString()),
                    FlowEncapsulationType.TRANSIT_VLAN);

    @Rule
    public ExpectedException thrown = ExpectedException.none();

    @Test
    public void shouldNotPollBeforeRegistration() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(currentGroup, empty());
    }

    @Test
    public void failToLocateTransactionBeforeRegistration() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownTransactionException.class);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
    }

    @Test
    public void failToLocateBatchBeforeRegistration() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownBatchException.class);
        registry.isBatchEmpty(FAKE_BATCH_ID);
    }

    @Test
    public void failToRemoveBatchBeforeRegistration() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownBatchException.class);
        registry.removeBatch(FAKE_BATCH_ID);
    }

    @Test
    public void failToObtainOnSuccessCommandsBeforeRegistration() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownBatchException.class);
        registry.getOnSuccessCommands(FAKE_BATCH_ID);
    }

    @Test
    public void failToObtainOnFailureCommandsBeforeRegistration() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        thrown.expect(UnknownBatchException.class);
        registry.getOnFailureCommands(FAKE_BATCH_ID);
    }

    @Test
    public void failToRegisterTransactionWithWrongFlowId() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<CommandGroup> groups = asList(
                new CommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new CommandGroup(singletonList(FLOW_COMMAND_2), FailureReaction.IGNORE));

        thrown.expect(IllegalArgumentException.class);
        registry.registerBatch(TEST_FLOW + "_fake", groups, emptyList(), emptyList());
    }

    @Test
    public void failToRegisterDuplicationTransaction() {
        FlowCommandRegistry registry = new FlowCommandRegistry();

        List<CommandGroup> groups = asList(
                new CommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new CommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE));

        thrown.expect(IllegalArgumentException.class);
        registry.registerBatch(TEST_FLOW, groups, emptyList(), emptyList());
    }

    @Test
    public void shouldObtainFailureReaction() {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        assertEquals(FailureReaction.IGNORE,
                registry.getFailureReaction(TEST_FLOW, FLOW_COMMAND_1.getTransactionId()).get());
        assertEquals(FailureReaction.ABORT_BATCH,
                registry.getFailureReaction(TEST_FLOW, FLOW_COMMAND_2.getTransactionId()).get());
    }

    @Test
    public void shouldNotPollNextGroupBeforeCommandsRemoved() {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_1, currentGroup.get(0));

        List<CommandData> nextGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(nextGroup, empty());
    }

    @Test
    public void shouldPollNextGroupWhenCurrentGroupEmpty() throws UnknownTransactionException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());

        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_2, currentGroup.get(0));
    }

    @Test
    public void shouldLocateCommandOnActiveBatch() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        assertFalse(registry.isBatchEmpty(batchId));
        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(FLOW_COMMAND_1, currentGroup.get(0));
        assertFalse(registry.isBatchEmpty(batchId));
    }

    @Test
    public void shouldLocateBatchAfterRegistration() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        assertFalse(registry.isBatchEmpty(batchId));
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
    public void failToLocateBatchByWrongTransaction() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        thrown.expect(UnknownBatchException.class);
        registry.removeBatch(UUID.randomUUID());
    }

    @Test
    public void shouldRemoveBatchWhenAllGroupEmpty() throws UnknownTransactionException, UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        assertTrue(registry.isBatchEmpty(batchId));
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

        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(currentGroup, empty());
    }

    @Test
    public void shouldNotRemoveBatchWhenAllGroupEmpty() throws UnknownTransactionException, UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());
        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_2.getTransactionId());

        assertTrue(registry.isBatchEmpty(batchId));
    }

    @Test
    public void shouldProcessNonFlowCommand() throws UnknownTransactionException, UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWithNonFlowCommand(registry);

        registry.pollNextGroup(TEST_FLOW);
        registry.removeCommand(TEST_FLOW, FLOW_COMMAND_1.getTransactionId());

        assertFalse(registry.isBatchEmpty(batchId));

        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertEquals(NON_FLOW_COMMAND, currentGroup.get(0));

        assertTrue(registry.isBatchEmpty(batchId));
    }

    @Test
    public void shouldNotPollFromRemovedBatch() throws UnknownBatchException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        assertEquals(batchId, registry.getCurrentBatch(TEST_FLOW).get());
        registry.removeBatch(batchId);

        assertFalse(registry.getCurrentBatch(TEST_FLOW).isPresent());
        List<CommandData> currentGroup = registry.pollNextGroup(TEST_FLOW);
        assertThat(currentGroup, empty());
    }

    @Test
    public void shouldReturnFlowForExpiredBatch() throws InterruptedException {
        FlowCommandRegistry registry = new FlowCommandRegistry();
        UUID batchId = registerBatchWith2Groups(registry);

        registry.pollNextGroup(TEST_FLOW);

        Set<UUID> expiredBatches = Failsafe.with(new RetryPolicy()
                .withDelay(10, TimeUnit.MILLISECONDS)
                .withMaxDuration(100, TimeUnit.MILLISECONDS)
                .retryIf(result -> ((Set) result).isEmpty()))
                .get(() -> registry.getExpiredBatches(Duration.ZERO));
        assertThat(expiredBatches, hasItem(batchId));
    }

    private UUID registerBatchWith2Groups(FlowCommandRegistry registry) {
        List<CommandGroup> groups = asList(
                new CommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.IGNORE),
                new CommandGroup(singletonList(FLOW_COMMAND_2), FailureReaction.ABORT_BATCH));
        return registry.registerBatch(TEST_FLOW, groups, emptyList(), emptyList());
    }

    private UUID registerBatchWithNonFlowCommand(FlowCommandRegistry registry) {
        List<CommandGroup> groups = asList(
                new CommandGroup(singletonList(FLOW_COMMAND_1), FailureReaction.ABORT_BATCH),
                new CommandGroup(singletonList(NON_FLOW_COMMAND), FailureReaction.IGNORE));
        return registry.registerBatch(TEST_FLOW, groups, emptyList(), emptyList());
    }
}
