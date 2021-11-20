/* Copyright 2021 Telstra Open Source
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

import org.openkilda.floodlight.api.OfSpeaker;
import org.openkilda.floodlight.api.request.OfSpeakerBatchEntry;
import org.openkilda.floodlight.api.request.OfSpeakerCommand;
import org.openkilda.floodlight.api.response.rulemanager.InstallSpeakerCommandsResponse;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.MeterConfig;
import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;

import lombok.Getter;
import lombok.NonNull;
import lombok.Value;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.module.FloodlightModuleContext;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

@Slf4j
public class OfSpeakerService implements OfSpeaker {
    private final FloodlightModuleContext moduleContext;

    public OfSpeakerService(@NonNull FloodlightModuleContext moduleContext) {
        this.moduleContext = moduleContext;
    }

    public CompletableFuture<MessageContext> execute(OfSpeakerCommand command, String kafkaKey) {
        log.debug("Process speaker command {}", command);
        Instant timeStart = Instant.now();
        return command.execute(this)
                .whenComplete((response, error) -> handleResult(command, response, error, timeStart, kafkaKey));
    }

    @Override
    public CompletableFuture<MessageContext> commandsBatch(
            MessageContext context, SwitchId switchId, Collection<OfSpeakerBatchEntry> batch) {
        List<BatchChunk> executionChain = BatchChunk.of(batch);
        Iterator<BatchChunk> iterator = executionChain.iterator();
        CompletableFuture<Void> chain = CompletableFuture.completedFuture(null);
        while (iterator.hasNext()) {
            BatchChunk chunk = iterator.next();
            chain = chain.thenCompose(dummy -> executeBatchChunk(chunk));
        }

        return chain.thenApply(dummy -> context);
    }

    @Override
    public CompletableFuture<MessageContext> installMeter(
            MessageContext context, SwitchId switchId, MeterConfig meterConfig) {
        // TODO
        return CompletableFuture.completedFuture(context);
    }

    @Override
    public CompletableFuture<MessageContext> removeMeter(MessageContext context, SwitchId switchId, MeterId meterId) {
        // TODO
        return CompletableFuture.completedFuture(context);
    }

    private CompletableFuture<Void> executeBatchChunk(BatchChunk chunk) {
        List<CompletableFuture<MessageContext>> chunkCommands = new ArrayList<>();
        for (OfSpeakerCommand command : chunk.getChunk()) {
            chunkCommands.add(command.execute(this));
        }
        return CompletableFuture.allOf(chunkCommands.toArray(new CompletableFuture[0]));
    }

    private void handleResult(
            OfSpeakerCommand command, MessageContext result, Throwable error, Instant timeStart, String kafkaKey) {
        Duration execTime = Duration.between(timeStart, Instant.now());
        if (error == null) {
            reportSuccess(command, execTime);
            sendResponse(command, result, kafkaKey, true);
        } else {
            reportError(command, error, execTime);
            sendResponse(command, result, kafkaKey, false);
        }
    }

    private void sendResponse(OfSpeakerCommand command, MessageContext context, String kafkaKey, boolean isSuccess) {
        KafkaUtilityService kafkaUtil = moduleContext.getServiceImpl(KafkaUtilityService.class);
        IKafkaProducerService kafkaProducer = moduleContext.getServiceImpl(IKafkaProducerService.class);

        InstallSpeakerCommandsResponse response = InstallSpeakerCommandsResponse.builder()
                .commandId(command.getCommandId())
                .messageContext(context)
                .switchId(command.getSwitchId())
                .success(isSuccess)
                .build();

        // TODO qualify result kafka topic
        kafkaProducer.sendMessageAndTrack(
                kafkaUtil.getKafkaChannel().getSpeakerFlowHsTopic(), kafkaKey, response);
    }

    private void reportSuccess(OfSpeakerCommand command, Duration execTime) {
        log.info(formatExecStats(command, "have completed successfully", execTime));
    }

    private void reportError(OfSpeakerCommand command, Throwable error, Duration execTime) {
        log.error(formatExecStats(command, String.format("have failed with error: %s", error), execTime), error);
    }

    private String formatExecStats(OfSpeakerCommand command, String message, Duration execTime) {
        return String.format("Command %s on %s %s (exec time: %s)", command, command.getSwitchId(), message, execTime);
    }

    @Value
    private static class BatchChunk {
        @Getter
        List<OfSpeakerBatchEntry> chunk = new ArrayList<>();

        static List<BatchChunk> of(Collection<OfSpeakerBatchEntry> batch) {
            Set<UUID> ready = new HashSet<>();
            List<BatchChunk> result = new ArrayList<>();
            List<OfSpeakerBatchEntry> incomplete = new ArrayList<>(batch);
            while (!incomplete.isEmpty()) {
                BatchChunk chunk = new BatchChunk(ready, incomplete.iterator());

                result.add(chunk);
                ready.addAll(chunk.getSatisfiedDependencies());
            }

            return result;
        }

        BatchChunk(Set<UUID> ready, Iterator<OfSpeakerBatchEntry> batch) {
            List<OfSpeakerBatchEntry> rejected = new ArrayList<>();
            while(batch.hasNext()) {
                OfSpeakerBatchEntry entry = batch.next();

                if (ready.containsAll(entry.dependencies())) {
                    rejected.add(entry);
                    continue;
                }

                ready.add(entry.getCommandId());
                chunk.add(entry);

                batch.remove();
            }

            if (chunk.isEmpty() && ! rejected.isEmpty()) {
                throw new IllegalArgumentException(formatErrorMessage(ready, rejected));
            }
        }

        Set<UUID> getSatisfiedDependencies() {
            Set<UUID> result = new HashSet<>(chunk.size());
            for (OfSpeakerBatchEntry entry : chunk) {
                result.add(entry.getCommandId());
            }
            return result;
        }

        static String formatErrorMessage(Set<UUID> ready, List<OfSpeakerBatchEntry> scrap) {
            Map<UUID, OfSpeakerBatchEntry> commandById = new HashMap<>();
            for (OfSpeakerBatchEntry entry : scrap) {
                commandById.put(entry.getCommandId(), entry);
            }

            Map<UUID, Set<UUID>> missingDependencies = new HashMap<>();
            for (OfSpeakerBatchEntry entry : scrap) {
                Set<UUID> missing = new HashSet<>();
                for (UUID dependency : entry.dependencies()) {
                    if (ready.contains(dependency)) {
                        continue;
                    }
                    if (commandById.containsKey(dependency)) {
                        continue;
                    }
                    missing.add(dependency);
                }
                if (! missing.isEmpty()) {
                    missingDependencies.put(entry.getCommandId(), missing);
                }
            }

            List<String> missing = new ArrayList<>();
            for (Map.Entry<UUID, Set<UUID>> entry : missingDependencies.entrySet()) {
                OfSpeakerBatchEntry command = commandById.get(entry.getKey());
                missing.add(String.format(
                        "%s(%s) needs %s",
                        command == null ? null : command.getClass().getName(),
                        entry.getKey(), entry.getValue()));
            }

            String circularMessage = null;
            if (missing.size() < scrap.size()) {
                List<OfSpeakerBatchEntry> circular = new ArrayList<>();
                for (OfSpeakerBatchEntry entry : scrap) {
                    if (missingDependencies.containsKey(entry.getCommandId())) {
                        continue;
                    }
                    circular.add(entry);
                }

                circularMessage = String.format(
                        "following commands perform circular dependency chain [%s]",
                        circular.stream()
                                .map(entry -> String.format(
                                        "%s(%s) depends on %s",
                                        entry.getClass().getName(), entry.getCommandId(),
                                        entry.dependencies().removeAll(ready)))
                                .collect(Collectors.joining(", ")));
            }

            String missingMessage = null;
            if (! missing.isEmpty()) {
                missingMessage = String.format(
                        "following commands have missing dependencies [%s]",
                        String.join(", ", missing));
            }

            if (missingMessage != null && circularMessage != null) {
                return String.format("%s also %s", missingMessage, missingDependencies);
            }
            if (missingMessage != null) {
                return missingMessage;
            }
            if (circularMessage != null) {
                return circularMessage;
            }

            throw new IllegalStateException("Unable to produce illegal speaker batch content message");
        }
    }
}
