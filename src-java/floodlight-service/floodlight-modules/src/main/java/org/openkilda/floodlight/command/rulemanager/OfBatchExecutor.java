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
import static java.util.function.Function.identity;
import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.request.rulemanager.Origin;
import org.openkilda.floodlight.converter.rulemanager.OfFlowConverter;
import org.openkilda.floodlight.converter.rulemanager.OfGroupConverter;
import org.openkilda.floodlight.converter.rulemanager.OfMeterConverter;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.Session;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;

import com.google.common.base.Joiner;
import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFErrorMsg;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFMessage;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Slf4j
public class OfBatchExecutor {

    private final IOFSwitch iofSwitch;
    private final KafkaUtilityService kafkaUtilityService;
    private final IKafkaProducerService kafkaProducerService;
    private final SessionService sessionService;
    private final MessageContext messageContext;
    private final OfBatchHolder holder;
    private final Set<SwitchFeature> switchFeatures;
    private final String kafkaKey;
    private final Origin origin;

    private boolean hasMeters;
    private boolean hasGroups;
    private boolean hasFlows;

    private CompletableFuture<List<OFMeterConfigStatsReply>> meterStats = CompletableFuture.completedFuture(null);
    private CompletableFuture<List<OFGroupDescStatsReply>> groupStats = CompletableFuture.completedFuture(null);
    private CompletableFuture<List<OFFlowStatsReply>> flowStats = CompletableFuture.completedFuture(null);

    @Builder
    public OfBatchExecutor(IOFSwitch iofSwitch, KafkaUtilityService kafkaUtilityService,
                           IKafkaProducerService kafkaProducerService,
                           SessionService sessionService, MessageContext messageContext, OfBatchHolder holder,
                           Set<SwitchFeature> switchFeatures, String kafkaKey, Origin origin) {
        this.iofSwitch = iofSwitch;
        this.kafkaUtilityService = kafkaUtilityService;
        this.kafkaProducerService = kafkaProducerService;
        this.sessionService = sessionService;
        this.messageContext = messageContext;
        this.holder = holder;
        this.switchFeatures = switchFeatures;
        this.kafkaKey = kafkaKey;
        this.origin = origin;
    }

    /**
     * Execute current batch of commands.
     */
    public void executeBatch() {
        log.debug("Execute batch start (key={})", kafkaKey);
        List<UUID> stageCommandsUuids = holder.getCurrentStage();
        List<OFMessage> ofMessages = new ArrayList<>();
        for (UUID uuid : stageCommandsUuids) {
            if (holder.canExecute(uuid)) {
                BatchData batchData = holder.getByUUid(uuid);
                OFMessage ofMessage = batchData.getMessage();
                log.debug("Start processing UUID: {} (key={}, xid={})", uuid, kafkaKey, ofMessage.getXid());

                hasFlows |= batchData.isFlow();
                hasMeters |= batchData.isMeter();
                hasGroups |= batchData.isGroup();
                ofMessages.add(ofMessage);
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
                .exceptionally(ignore -> {
                    checkOfResponses();
                    return null;
                });
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
            meterStats = OfUtils.verifyMeters(messageContext, iofSwitch).whenComplete((res, ex) -> {
                log.debug("Get meter stats: {} (key={})", res, kafkaKey);
                if (ex != null) {
                    log.error("Received error {}", ex.getMessage(), ex);
                }
            });
        }
        if (hasGroups) {
            groupStats = OfUtils.verifyGroups(messageContext, iofSwitch).whenComplete((res, ex) -> {
                log.debug("Get group stats: {} (key={})", res, kafkaKey);
                if (ex != null) {
                    log.error("Received error {}", ex.getMessage(), ex);
                }
            });
        }
        if (hasFlows) {
            flowStats = OfUtils.verifyFlows(messageContext, iofSwitch).whenComplete((res, ex) -> {
                log.debug("Get flow stats: {} (key={})", res, kafkaKey);
                if (ex != null) {
                    log.error("Received error {}", ex.getMessage(), ex);
                }
            });
        }
        CompletableFuture.allOf(meterStats, groupStats, flowStats)
                .thenAccept(ignore -> runVerify());
    }

    private void runVerify() {
        log.debug("Verify entities (key={})", kafkaKey);
        verifyFlows();
        verifyMeters();
        verifyGroups();
        if (holder.nextStage()) {
            log.debug("Proceed next stage (key={})", kafkaKey);
            meterStats = CompletableFuture.completedFuture(null);
            groupStats = CompletableFuture.completedFuture(null);
            flowStats = CompletableFuture.completedFuture(null);
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
            List<OFFlowStatsReply> replies = flowStats.get();
            List<FlowSpeakerData> switchFlows = new ArrayList<>();
            replies.forEach(reply -> switchFlows.addAll(
                    OfFlowConverter.INSTANCE.convertToFlowSpeakerData(reply,
                            new SwitchId(iofSwitch.getId().getLong()))));
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
            log.error("Failed to verify flows for message", e);
        }
    }

    private void verifyMeters() {
        log.debug("Verify meters with key: {} (hasMeters={})", kafkaKey, hasMeters);
        if (!hasMeters) {
            return;
        }
        boolean inaccurate = switchFeatures.contains(SwitchFeature.INACCURATE_METER);
        try {
            List<OFMeterConfigStatsReply> replies = meterStats.get();
            List<MeterSpeakerData> switchMeters = new ArrayList<>();
            replies.forEach(reply -> switchMeters.addAll(
                    OfMeterConverter.INSTANCE.convertToMeterSpeakerData(reply, inaccurate)));

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
            log.error("Failed to verify meters for message", e);
        }

    }

    private void verifyGroups() {
        log.debug("Verify groups with key: {} (hasGroups={})", kafkaKey, hasGroups);
        if (!hasGroups) {
            return;
        }
        try {
            List<OFGroupDescStatsReply> replies = groupStats.get();
            List<GroupSpeakerData> switchGroups = new ArrayList<>();
            replies.forEach(reply -> switchGroups.addAll(
                    OfGroupConverter.INSTANCE.convertToGroupSpeakerData(reply,
                            new SwitchId(iofSwitch.getId().getLong()))));
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
            log.error("Failed to verify groups for message", e);
        }

    }

    private void sendResponse() {
        KafkaChannel kafkaChannel = kafkaUtilityService.getKafkaChannel();
        String topic = getTopic(kafkaChannel);
        log.debug("Send response to {} (key={})", topic, kafkaKey);
        kafkaProducerService.sendMessageAndTrack(topic, kafkaKey, holder.getResult());
    }

    private String getTopic(KafkaChannel kafkaChannel) {
        switch (origin) {
            case FLOW_HS:
                return kafkaChannel.getSpeakerFlowHsTopic();
            case SW_MANAGER:
                return kafkaChannel.getSpeakerSwitchManagerResponseTopic();
            default:
                throw new IllegalStateException(format("Unknown message origin %s", origin));
        }
    }
}
