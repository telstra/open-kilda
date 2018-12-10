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

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.Collections.unmodifiableList;
import static java.util.stream.Collectors.toMap;

import org.openkilda.messaging.command.flow.BaseFlow;
import org.openkilda.messaging.command.flow.FlowCommandGroup;
import org.openkilda.messaging.command.flow.FlowCommandGroup.FailureReaction;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.extern.slf4j.Slf4j;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A registry for batches of grouped {@link BaseFlow} commands.
 * <p/>
 * NOTE: the implementation is NOT thread-safe.
 */
@Slf4j
public class FlowCommandRegistry {
    // The default timeBasedGenerator() utilizes SecureRandom for the location part and time+sequence for the time part.
    private final NoArgGenerator batchIdGenerator = Generators.timeBasedGenerator();

    private final Map<String, Queue<Group>> groups = new HashMap<>();
    private final Map<UUID, Batch> batches = new HashMap<>();
    private final Map<UUID, UUID> transactionToBatch = new HashMap<>();

    /**
     * Registers the grouped commands as a batch for the flow.
     * Puts the commands to the back of the queue.
     */
    public void registerBatch(String flowId, List<FlowCommandGroup> batchCommands) {
        UUID batchId = batchIdGenerator.generate();
        Set<UUID> batchTransactions = new HashSet<>();

        log.debug("Registering commands as batch {}: {}", batchId, batchCommands);

        Queue<Group> flowGroups = groups.computeIfAbsent(flowId, k -> new LinkedList<>());
        for (FlowCommandGroup group : batchCommands) {
            for (BaseFlow command : group.getFlowCommands()) {
                if (!command.getId().equals(flowId)) {
                    throw new IllegalArgumentException(
                            format("Command '%s' doesn't correspond to specified flow %s", command, flowId));
                }

                UUID transactionId = command.getTransactionId();
                if (!batchTransactions.add(transactionId)) {
                    throw new IllegalArgumentException(
                            format("Command '%s' has transactionId which already registered", command));
                }
                transactionToBatch.put(transactionId, batchId);
            }

            flowGroups.add(new Group(batchId, new ArrayList<>(group.getFlowCommands()), group.getReactionOnError()));
        }

        batches.put(batchId, new Batch(flowId, batchTransactions));
    }

    /**
     * Removes the command (identified by the flow and transaction IDs) from the current group.
     * Cleans up groups and batches if they are empty.
     */
    public void removeCommand(String flowId, UUID transactionId) throws UnknownTransactionException {
        log.info("Removing the command by flowId={} and transactionId={}", flowId, transactionId);

        Queue<Group> flowGroups = groups.get(flowId);
        if (flowGroups == null || flowGroups.isEmpty()) {
            throw new UnknownTransactionException(format("Trying to complete transaction %s for unknown flow %s",
                    transactionId, flowId));
        }

        Group currentGroup = flowGroups.peek();
        if (currentGroup == null || !currentGroup.remove(transactionId)) {
            throw new UnknownTransactionException(format("Transaction %s is not in the current group", transactionId));
        }
        if (currentGroup.isEmpty()) {
            log.info("Removing the current group for flowId={}, {} groups left", flowId, flowGroups.size());
            // The current group has been processed, so remove it from the queue.
            flowGroups.poll();
        }

        UUID batchId = transactionToBatch.get(transactionId);
        Batch batch = Optional.ofNullable(batchId).map(batches::get)
                .orElseThrow(() -> new IllegalStateException(
                        format("Transaction %s has no batch associated", transactionId)));
        if (!batch.remove(transactionId)) {
            throw new IllegalStateException(format("Transaction %s is not in the batch", transactionId));
        }
        if (batch.isEmpty()) {
            log.info("Removing the batch by flowId={} and batchId={}", flowId, batchId);
            // The batch has been completed, so remove it.
            batches.remove(batchId);
        }
    }

    /**
     * Removes all commands belong to the batch (identified by the flow and transaction).
     * Cleans up groups and batches if they are empty.
     */
    public void removeBatch(String flowId, UUID transactionId) throws UnknownTransactionException {
        Queue<Group> flowGroups = groups.get(flowId);
        if (flowGroups == null || flowGroups.isEmpty()) {
            throw new UnknownTransactionException(format("Trying to cancel transaction %s for unknown flow %s",
                    transactionId, flowId));
        }

        // Remove the batch and relations.
        UUID batchId = transactionToBatch.get(transactionId);
        log.info("Removing the batch by flowId={} and batchId={}", flowId, batchId);
        Batch batch = Optional.ofNullable(batchId).map(batches::remove)
                .orElseThrow(() -> new UnknownTransactionException(
                        format("Transaction %s has no batch associated", transactionId)));
        batch.transactions.forEach(transactionToBatch::remove);

        // Clean up groups associated with the batch.
        flowGroups.removeIf(group -> group.batchId.equals(batchId));
    }

    /**
     * Checks whether there's a command for the flow in the registry.
     */
    public boolean hasCommand(String flowId) {
        Queue<Group> flowGroups = groups.get(flowId);
        return flowGroups != null && !flowGroups.isEmpty();
    }

    /**
     * Polls a group of commands to be processed. The group becomes the current.
     * The method returns a group only once and moves to the next only when the current group becomes empty.
     */
    public List<BaseFlow> pollNextGroup(String flowId) {
        Queue<Group> flowGroups = groups.get(flowId);
        if (flowGroups != null) {
            Group currentGroup;
            while ((currentGroup = flowGroups.peek()) != null) {
                if (currentGroup.isEmpty()) {
                    log.info("Removing the current group for flowId={}, {} groups left", flowId, flowGroups.size());
                    // The current group has been processed, so remove it from the queue and look for another.
                    flowGroups.poll();
                } else {
                    if (currentGroup.polled) {
                        // The current group has already been polled, but not processed completely.
                        break;
                    }

                    // The current group is a new one, so take it.
                    currentGroup.polled = true;
                    return unmodifiableList(currentGroup.commands);
                }
            }
        }

        return emptyList();
    }

    /**
     * Finds and removes expired batches and all commands belong to them.
     */
    public Set<String> removeExpiredBatch(Duration expirationTime) {
        List<UUID> expiredBatches = batches.entrySet().stream()
                .filter(e -> Duration.between(e.getValue().createdAt, Instant.now()).compareTo(expirationTime) > 0)
                .map(Entry::getKey)
                .collect(Collectors.toList());

        return expiredBatches.stream()
                .map(batchId -> {
                    // Remove the batch and relations.
                    Batch batch = batches.remove(batchId);
                    batch.transactions.forEach(transactionToBatch::remove);

                    // Clean up groups associated with the batch.
                    Queue<Group> flowGroups = groups.get(batch.flowId);
                    flowGroups.removeIf(group -> group.batchId.equals(batchId));

                    return batch.flowId;
                })
                .collect(Collectors.toSet());
    }

    /**
     * Gathers and groups all active transactions by a flow.
     */
    public Map<String, Set<UUID>> getTransactions() {
        return groups.entrySet().stream()
                .collect(toMap(Entry::getKey, e -> e.getValue().stream()
                        .flatMap(group -> group.commands.stream())
                        .map(BaseFlow::getTransactionId)
                        .collect(Collectors.toSet())));
    }

    /**
     * Return registered reaction on a command failure in the group (identified by the flow and transaction).
     */
    public Optional<FailureReaction> getFailureReaction(String flowId, UUID transactionId) {
        return Optional.ofNullable(groups.get(flowId))
                .flatMap(flowGroups -> flowGroups.stream()
                        .filter(group -> group.contains(transactionId))
                        .map(Group::getReactionOnFailure)
                        .findAny());
    }

    class Batch {
        final String flowId;
        final Set<UUID> transactions;
        final Instant createdAt = Instant.now();

        Batch(String flowId, Set<UUID> transactions) {
            this.flowId = flowId;
            this.transactions = transactions;
        }

        boolean isEmpty() {
            return transactions.isEmpty();
        }

        public boolean remove(UUID transactionId) {
            return transactions.remove(transactionId);
        }
    }

    class Group {
        final UUID batchId;
        final List<BaseFlow> commands;
        final FailureReaction reactionOnFailure;
        boolean polled = false;

        Group(UUID batchId, List<BaseFlow> commands, FailureReaction reactionOnFailure) {
            this.batchId = batchId;
            this.commands = commands;
            this.reactionOnFailure = reactionOnFailure;
        }

        boolean isEmpty() {
            return commands.isEmpty();
        }

        public boolean contains(UUID transactionId) {
            return commands.stream().anyMatch(flow -> flow.getTransactionId().equals(transactionId));
        }

        public boolean remove(UUID transactionId) {
            return commands.removeIf(flow -> flow.getTransactionId().equals(transactionId));
        }

        public FailureReaction getReactionOnFailure() {
            return reactionOnFailure;
        }
    }
}
