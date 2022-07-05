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

package org.openkilda.floodlight.command.rulemanager;

import static java.lang.String.format;
import static java.util.Collections.emptyList;
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchFeature;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;
import org.openkilda.rulemanager.SpeakerData;

import com.google.common.base.Joiner;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFMessage;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.stream.Collectors;

@Slf4j
public class OfBatchExecutor {

    private final IOFSwitch iofSwitch;
    private final KafkaUtilityService kafkaUtilityService;
    private final IKafkaProducerService kafkaProducerService;
    private final SessionService sessionService;
    private final SwitchDataProvider switchDataProvider;
    private final MessageContext messageContext;
    private final OfBatchHolder holder;
    private final Set<SwitchFeature> switchFeatures;
    private final String kafkaKey;
    private final String replyTo;
    private final boolean failIfExists;

    private boolean hasMeters;
    private boolean hasGroups;
    private boolean hasFlows;

    private CompletableFuture<List<MeterSpeakerData>> meterStats = CompletableFuture.completedFuture(emptyList());
    private CompletableFuture<List<GroupSpeakerData>> groupStats = CompletableFuture.completedFuture(emptyList());
    private CompletableFuture<List<FlowSpeakerData>> flowStats = CompletableFuture.completedFuture(emptyList());

    @Builder
    public OfBatchExecutor(IOFSwitch iofSwitch, KafkaUtilityService kafkaUtilityService,
                           IKafkaProducerService kafkaProducerService, SessionService sessionService,
                           MessageContext messageContext, OfBatchHolder holder,
                           Set<SwitchFeature> switchFeatures, String kafkaKey, String replyTo, Boolean failIfExists) {
        this.iofSwitch = iofSwitch;
        this.kafkaUtilityService = kafkaUtilityService;
        this.kafkaProducerService = kafkaProducerService;
        this.sessionService = sessionService;
        this.switchDataProvider = SwitchDataProvider.builder()
                .iofSwitch(iofSwitch)
                .messageContext(messageContext)
                .switchFeatures(switchFeatures)
                .kafkaKey(kafkaKey)
                .build();
        this.messageContext = messageContext;
        this.holder = holder;
        this.switchFeatures = switchFeatures;
        this.kafkaKey = kafkaKey;
        this.replyTo = replyTo;
        this.failIfExists = failIfExists == null || failIfExists;
    }

    /**
     * Execute current batch of commands.
     */
    public void executeBatch() {
        log.debug("Execute batch start (key={})", kafkaKey);
        List<UUID> stageCommandsUuids = holder.getCurrentStage();
        List<BatchData> stageMessages = new ArrayList<>();
        for (UUID uuid : stageCommandsUuids) {
            if (holder.canExecute(uuid)) {
                BatchData batchData = holder.getByUUid(uuid);
                OFMessage ofMessage = batchData.getMessage();
                log.debug("Start processing UUID: {} (key={}, xid={})", uuid, kafkaKey, ofMessage.getXid());

                hasFlows |= batchData.isFlow();
                hasMeters |= batchData.isMeter();
                hasGroups |= batchData.isGroup();
                stageMessages.add(batchData);
            } else {
                Map<UUID, String> blockingDependencies = holder.getBlockingDependencies(uuid);
                if (!blockingDependencies.isEmpty()) {
                    String errorMessage = Joiner.on(",").withKeyValueSeparator("=").join(blockingDependencies);
                    holder.recordFailedUuid(uuid, "Not all dependencies are satisfied: " + errorMessage);
                } else if (holder.getByUUid(uuid) == null) {
                    holder.recordFailedUuid(uuid, "Missing in the batch");
                } else {
                    holder.recordFailedUuid(uuid, "Can't execute");
                }
            }
        }

        if (!failIfExists) {
            removeAlreadyExists(stageMessages);
        }

        Collection<OFMessage> ofMessages = stageMessages.stream()
                .map(BatchData::getMessage)
                .collect(Collectors.toList());
        List<CompletableFuture<Optional<OFMessage>>> requests = new ArrayList<>();
        try (Session session = sessionService.open(messageContext, iofSwitch)) {
            for (OFMessage message : ofMessages) {
                requests.add(session.write(message).whenComplete((res, ex) -> {
                    log.debug("Check responses (key={}, xid={}, res={}, ex={})", kafkaKey, message.getXid(), res, ex);
                    if (ex == null) {
                        res.ifPresent(ofMessage -> {
                            if (ofMessage instanceof OFErrorMsg) {
                                UUID uuid = holder.popAwaitingXid(ofMessage.getXid());
                                OFErrorMsg errorMsg = (OFErrorMsg) ofMessage;
                                holder.recordFailedUuid(uuid, errorMsg.getErrType().toString());
                            } else {
                                onSuccessfulOfMessage(ofMessage);
                            }
                        });
                        // session.write() completes successfully with no result.
                        if (!res.isPresent()) {
                            onSuccessfulOfMessage(message);
                        }
                    } else {
                        log.error("Received error {}", ex.getMessage(), ex);
                        UUID uuid = holder.popAwaitingXid(message.getXid());
                        holder.recordFailedUuid(uuid, ex.getMessage());
                    }
                }));
            }
        }

        CompletableFuture.allOf(requests.toArray(new CompletableFuture<?>[0]))
                .thenAccept(ignore -> checkOfResponses())
                .exceptionally(ex -> {
                    holder.otherFail("Failed to process OpenFlow messages.", ex);
                    sendResponse();
                    return null;
                });
    }

    private void removeAlreadyExists(List<BatchData> stageMessages) {
        if (hasMeters) {
            meterStats = switchDataProvider.getMeters();
        }
        if (hasGroups) {
            groupStats = switchDataProvider.getGroups();
        }

        try {
            List<SpeakerData> speakerData = new ArrayList<>(meterStats.get());
            speakerData.addAll(groupStats.get());
            Set<BatchData> toRemove = new HashSet<>();
            for (BatchData batchData : stageMessages) {
                if (speakerData.contains(batchData.getOrigin())) {
                    toRemove.add(batchData);
                }
            }
            toRemove.forEach(data -> {
                log.debug("OpenFlow entry is already exist. Skipping command {}", data);
                holder.recordSuccessUuid(data.getOrigin().getUuid());
                stageMessages.remove(data);
            });
        } catch (ExecutionException | InterruptedException e) {
            holder.otherFail("Failed to verify OpenFlow elements already exists.", e);
        }

        meterStats = CompletableFuture.completedFuture(emptyList());
        groupStats = CompletableFuture.completedFuture(emptyList());
        flowStats = CompletableFuture.completedFuture(emptyList());
    }

    private void onSuccessfulOfMessage(OFMessage ofMessage) {
        UUID uuid = holder.popAwaitingXid(ofMessage.getXid());
        BatchData batchData = holder.getByUUid(uuid);
        if (batchData != null && !batchData.isPresenceBeVerified()) {
            holder.recordSuccessUuid(uuid);
        } else {
            log.debug("Received a response for {} / {}. Batch is to be verified...",
                    ofMessage.getXid(), uuid);
        }
    }

    private void checkOfResponses() {
        if (hasMeters) {
            meterStats = switchDataProvider.getMeters();
        }
        if (hasGroups) {
            groupStats = switchDataProvider.getGroups();
        }
        if (hasFlows) {
            flowStats = switchDataProvider.getFlows();
        }
        CompletableFuture.allOf(meterStats, groupStats, flowStats)
                .thenAccept(ignore -> runVerify())
                .exceptionally(ex -> {
                    holder.otherFail("Failed to get data from switch.", ex);
                    sendResponse();
                    return null;
                });
    }

    private void runVerify() {
        log.debug("Verify entities (key={})", kafkaKey);
        verifyFlows();
        verifyMeters();
        verifyGroups();
        if (holder.nextStage()) {
            log.debug("Proceed next stage (key={})", kafkaKey);
            meterStats = CompletableFuture.completedFuture(emptyList());
            groupStats = CompletableFuture.completedFuture(emptyList());
            flowStats = CompletableFuture.completedFuture(emptyList());
            hasMeters = false;
            hasGroups = false;
            hasFlows = false;
            executeBatch();
        } else {
            sendResponse();
        }
    }

    private void verifyFlows() {
        log.debug("Verify flows with key: {} (hasFlows={})", kafkaKey, hasFlows);
        if (!hasFlows) {
            return;
        }
        try {
            List<FlowSpeakerData> switchFlows = flowStats.get();
            Map<Long, Long> cookieCounts = switchFlows.stream()
                    .map(v -> v.getCookie().getValue())
                    .collect(groupingBy(identity(), counting()));

            for (FlowSpeakerData switchFlow : switchFlows) {
                FlowSpeakerData expectedFlow = holder.getByCookie(switchFlow.getCookie());
                if (expectedFlow != null) {
                    BatchData batchData = holder.getByUUid(expectedFlow.getUuid());
                    if (batchData != null && batchData.isPresenceBeVerified()) {
                        if (switchFlow.equals(expectedFlow)) {
                            holder.recordSuccessUuid(expectedFlow.getUuid());
                        } else {
                            long cookie = switchFlow.getCookie().getValue();
                            // Go through all duplicate cookies, and fail on the last one.
                            if (cookieCounts.get(cookie) > 1) {
                                log.debug("Detected duplicate cookies {} on switch {}, skipping...",
                                        switchFlow.getCookie(), switchFlow.getSwitchId());
                                cookieCounts.compute(cookie, (k, v) -> v - 1);
                            } else {
                                holder.recordFailedUuid(expectedFlow.getUuid(),
                                        format("Failed to validate flow on a switch. Expected: %s, actual: %s",
                                                expectedFlow, switchFlow));
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            holder.otherFail("Failed to verify flows for message", e);
        }
    }

    private void verifyMeters() {
        log.debug("Verify meters with key: {} (hasMeters={})", kafkaKey, hasMeters);
        if (!hasMeters) {
            return;
        }
        try {
            List<MeterSpeakerData> switchMeters = meterStats.get();

            for (MeterSpeakerData switchMeter : switchMeters) {
                MeterSpeakerData expectedMeter = holder.getByMeterId(switchMeter.getMeterId());
                if (expectedMeter != null) {
                    BatchData batchData = holder.getByUUid(expectedMeter.getUuid());
                    if (batchData != null && batchData.isPresenceBeVerified()) {
                        if (switchMeter.equals(expectedMeter)) {
                            holder.recordSuccessUuid(expectedMeter.getUuid());
                        } else {
                            holder.recordFailedUuid(expectedMeter.getUuid(),
                                    format("Failed to validate meter on a switch. Expected: %s, actual: %s. "
                                            + "Switch features: %s.", expectedMeter, switchMeter, switchFeatures));
                        }
                    }
                }
            }
        } catch (Exception e) {
            holder.otherFail("Failed to verify meters for message", e);
        }
    }

    private void verifyGroups() {
        log.debug("Verify groups with key: {} (hasGroups={})", kafkaKey, hasGroups);
        if (!hasGroups) {
            return;
        }
        try {
            List<GroupSpeakerData> switchGroups = groupStats.get();
            for (GroupSpeakerData switchGroup : switchGroups) {
                GroupSpeakerData expectedGroup = holder.getByGroupId(switchGroup.getGroupId());
                if (expectedGroup != null) {
                    BatchData batchData = holder.getByUUid(expectedGroup.getUuid());
                    if (batchData != null && batchData.isPresenceBeVerified()) {
                        if (switchGroup.equals(expectedGroup)) {
                            holder.recordSuccessUuid(expectedGroup.getUuid());
                        } else {
                            holder.recordFailedUuid(expectedGroup.getUuid(),
                                    format("Failed to validate group on a switch. Expected: %s, actual: %s",
                                            expectedGroup, switchGroup));
                        }
                    }
                }
            }
        } catch (Exception e) {
            holder.otherFail("Failed to verify groups for message", e);
        }
    }

    private void sendResponse() {
        KafkaChannel kafkaChannel = kafkaUtilityService.getKafkaChannel();
        log.debug("Send response to {} (key={})", replyTo, kafkaKey);
        kafkaProducerService.sendMessageAndTrack(replyTo, kafkaKey, holder.getResult());
    }
}
