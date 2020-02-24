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
import static java.util.stream.Collectors.toMap;

import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.CommandGroup;
import org.openkilda.messaging.command.CommandGroup.FailureReaction;
import org.openkilda.messaging.command.flow.BaseFlow;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import lombok.NonNull;
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
     *
     * @param flowId            the flow to which the commands belongs to.
     * @param batchCommands     grouped commands to be registered.
     * @param onSuccessCommands commands to be bound to the batch as on success actions.
     * @param onFailureCommands commands to be bound to the batch as on failure actions.
     * @return the ID of the registered batch.
     */
    public UUID registerBatch(String flowId, List<CommandGroup> batchCommands,
                              @NonNull List<? extends CommandData> onSuccessCommands,
                              @NonNull List<? extends CommandData> onFailureCommands) {
        UUID batchId = batchIdGenerator.generate();
        if (batches.containsKey(batchId)) {
            throw new IllegalStateException(format("Batch %s is already registered", batchId));
        }

        Set<UUID> batchTransactions = new HashSet<>();

        log.debug("Registering commands as batch {}: {}", batchId, batchCommands);

        Queue<Group> flowGroups = groups.computeIfAbsent(flowId, k -> new LinkedList<>());
        for (CommandGroup group : batchCommands) {
            for (CommandData command : group.getCommands()) {
                if (command instanceof BaseFlow) {
                    BaseFlow flowCommand = (BaseFlow) command;
                    if (!flowCommand.getId().equals(flowId)) {
                        throw new IllegalArgumentException(
                                format("Command '%s' doesn't correspond to specified flow %s", command, flowId));
                    }

                    UUID transactionId = flowCommand.getTransactionId();
                    if (!batchTransactions.add(transactionId)) {
                        throw new IllegalArgumentException(
                                format("Command '%s' has transactionId which already registered", command));
                    }
                    transactionToBatch.put(transactionId, batchId);
                }
            }

            flowGroups.add(new Group(batchId, new ArrayList<>(group.getCommands()), group.getReactionOnError()));
        }

        batches.put(batchId, new Batch(flowId, batchTransactions,
                new ArrayList<>(onSuccessCommands), new ArrayList<>(onFailureCommands)));

        return batchId;
    }

    /**
     * Checks whether there's non-empty group in the batch.
     */
    public boolean isBatchEmpty(UUID batchId) throws UnknownBatchException {
        Batch batch = Optional.ofNullable(batches.get(batchId))
                .orElseThrow(() -> new UnknownBatchException(batchId));

        Queue<Group> flowGroups = groups.get(batch.flowId);
        if (flowGroups != null) {
            // Clean up groups associated with the batch.
            return flowGroups.stream()
                    .filter(group -> group.batchId.equals(batchId))
                    .allMatch(Group::isEmpty);
        }

        return true;
    }

    /**
     * Returns batchId of the current group (if set).
     */
    public Optional<UUID> getCurrentBatch(String flowId) {
        return Optional.ofNullable(groups.get(flowId))
                .map(Queue::peek)
                .map(group -> group.batchId);
    }

    /**
     * Polls a group of commands to be processed. The group becomes the current.
     * The method returns a group only once and moves to the next only when the current group becomes empty.
     */
    public List<CommandData> pollNextGroup(String flowId) {
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

                    List<CommandData> result = new ArrayList<>(currentGroup.commands);

                    // No need to track commands without transactionId.
                    currentGroup.commands.removeIf(command -> !(command instanceof BaseFlow));

                    return result;
                }
            }
        }

        return emptyList();
    }

    /**
     * Removes the command (identified by the flow and transaction IDs) from the current group.
     * Cleans up groups if they are empty.
     *
     * @param flowId        the flow to which the commands belongs to.
     * @param transactionId command's transaction ID.
     * @return the batch ID to which the transaction belongs to.
     */
    public UUID removeCommand(String flowId, UUID transactionId) throws UnknownTransactionException {
        log.info("Removing the command by flowId {} and transactionId {}", flowId, transactionId);

        Queue<Group> flowGroups = groups.get(flowId);
        if (flowGroups == null || flowGroups.isEmpty()) {
            throw new UnknownTransactionException(format("Trying to remove transaction %s for unknown flow %s",
                    transactionId, flowId));
        }

        Group currentGroup = flowGroups.peek();
        if (currentGroup == null || !currentGroup.remove(transactionId)) {
            throw new UnknownTransactionException(format("Transaction %s is not in the current group", transactionId));
        }

        UUID batchId = transactionToBatch.remove(transactionId);
        Batch batch = Optional.ofNullable(batchId).map(batches::get)
                .orElseThrow(() -> new IllegalStateException(
                        format("Transaction %s has no batch associated", transactionId)));
        if (!batch.remove(transactionId)) {
            throw new IllegalStateException(format("Transaction %s is not in the batch", transactionId));
        }

        return batchId;
    }

    /**
     * Removes all commands belong to the batch.
     * Cleans up groups and batches if they are empty.
     *
     * @param batchId the batch to be removed.
     */
    public void removeBatch(UUID batchId) throws UnknownBatchException {
        log.info("Removing the batch by batchId {}", batchId);

        // Remove the batch and relations.
        Batch batch = Optional.ofNullable(batchId).map(batches::remove)
                .orElseThrow(() -> new UnknownBatchException(batchId));
        batch.transactions.forEach(transactionToBatch::remove);

        Queue<Group> flowGroups = groups.get(batch.flowId);
        if (flowGroups != null) {
            // Clean up groups associated with the batch.
            flowGroups.removeIf(group -> group.batchId.equals(batchId));
        }
    }

    /**
     * Finds and removes expired batches and all commands belong to them.
     */
    public Set<UUID> getExpiredBatches(Duration expirationTime) {
        return batches.entrySet().stream()
                .filter(e -> Duration.between(e.getValue().createdAt, Instant.now()).compareTo(expirationTime) > 0)
                .map(Entry::getKey)
                .collect(Collectors.toSet());
    }

    /**
     * Gathers and groups all active transactions by a flow.
     */
    public Map<String, Set<UUID>> getTransactions() {
        return groups.entrySet().stream()
                .collect(toMap(Entry::getKey, e -> e.getValue().stream()
                        .flatMap(group -> group.commands.stream())
                        .filter(command -> command instanceof BaseFlow)
                        .map(command -> ((BaseFlow) command).getTransactionId())
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

    /**
     * Return registered on-failure commands of the batch.
     */
    public List<CommandData> getOnFailureCommands(UUID batchId) throws UnknownBatchException {
        Batch batch = Optional.ofNullable(batchId).map(batches::get)
                .orElseThrow(() -> new UnknownBatchException(batchId));

        return batch.onFailureCommands;
    }

    /**
     * Return registered on-success commands of the batch.
     */
    public List<CommandData> getOnSuccessCommands(UUID batchId) throws UnknownBatchException {
        Batch batch = Optional.ofNullable(batchId).map(batches::get)
                .orElseThrow(() -> new UnknownBatchException(batchId));

        return batch.onSuccessCommands;
    }

    class Batch {
        final String flowId;
        final Set<UUID> transactions;
        final List<CommandData> onSuccessCommands;
        final List<CommandData> onFailureCommands;
        final Instant createdAt = Instant.now();

        Batch(String flowId, Set<UUID> transactions,
                List<CommandData> onSuccessCommands, List<CommandData> onFailureCommands) {
            this.flowId = flowId;
            this.transactions = transactions;
            this.onSuccessCommands = onSuccessCommands;
            this.onFailureCommands = onFailureCommands;
        }

        boolean isEmpty() {
            return transactions.isEmpty();
        }

        boolean remove(UUID transactionId) {
            return transactions.remove(transactionId);
        }
    }

    class Group {
        final UUID batchId;
        final List<CommandData> commands;
        final FailureReaction reactionOnFailure;
        boolean polled = false;

        Group(UUID batchId, List<CommandData> commands, FailureReaction reactionOnFailure) {
            this.batchId = batchId;
            this.commands = commands;
            this.reactionOnFailure = reactionOnFailure;
        }

        boolean isEmpty() {
            return commands.isEmpty();
        }

        boolean contains(UUID transactionId) {
            return commands.stream()
                    .filter(command -> command instanceof BaseFlow)
                    .anyMatch(flow -> ((BaseFlow) flow).getTransactionId().equals(transactionId));
        }

        boolean remove(UUID transactionId) {
            return commands.removeIf(command -> command instanceof BaseFlow
                    && ((BaseFlow) command).getTransactionId().equals(transactionId));
        }

        FailureReaction getReactionOnFailure() {
            return reactionOnFailure;
        }
    }
}
