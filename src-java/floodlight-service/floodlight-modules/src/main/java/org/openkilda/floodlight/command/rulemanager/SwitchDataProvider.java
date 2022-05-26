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

import org.openkilda.floodlight.converter.rulemanager.OfFlowConverter;
import org.openkilda.floodlight.converter.rulemanager.OfGroupConverter;
import org.openkilda.floodlight.converter.rulemanager.OfMeterConverter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchFeature;
import org.openkilda.model.SwitchId;
import org.openkilda.rulemanager.FlowSpeakerData;
import org.openkilda.rulemanager.GroupSpeakerData;
import org.openkilda.rulemanager.MeterSpeakerData;

import lombok.Builder;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

/**
 * Provides ability to read OpenFlow flows, meters and groups from switch in RuleManager format.
 */
@Slf4j
public class SwitchDataProvider {

    private final IOFSwitch iofSwitch;
    private final MessageContext messageContext;
    private final Set<SwitchFeature> switchFeatures;
    private final String kafkaKey;

    @Builder
    public SwitchDataProvider(IOFSwitch iofSwitch, MessageContext messageContext,
                              Set<SwitchFeature> switchFeatures, String kafkaKey) {
        this.iofSwitch = iofSwitch;
        this.messageContext = messageContext;
        this.switchFeatures = switchFeatures;
        this.kafkaKey = kafkaKey;
    }

    /**
     * Request flow data from switch and convert it to RuleManager representation.
     */
    public CompletableFuture<List<FlowSpeakerData>> getFlows() {
        return OfUtils.getFlows(messageContext, iofSwitch)
                .thenCompose(replies -> {
                    log.debug("Get flow stats: {} (key={})", replies, kafkaKey);
                    List<FlowSpeakerData> switchFlows = new ArrayList<>();
                    if (replies != null) {
                        replies.forEach(reply -> switchFlows.addAll(
                                OfFlowConverter.INSTANCE.convertToFlowSpeakerData(reply,
                                        new SwitchId(iofSwitch.getId().getLong()))));
                    }
                    return CompletableFuture.completedFuture(switchFlows);
                })
                .exceptionally(ex -> {
                    log.error("Can't get flows from switch", ex);
                    return Collections.emptyList();
                });
    }

    /**
     * Request meter data from switch and convert it to RuleManager representation.
     */
    public CompletableFuture<List<MeterSpeakerData>> getMeters() {
        return OfUtils.getMeters(messageContext, iofSwitch)
                .thenCompose(replies -> {
                    log.debug("Get meter stats: {} (key={})", replies, kafkaKey);
                    boolean inaccurate = switchFeatures.contains(SwitchFeature.INACCURATE_METER);
                    List<MeterSpeakerData> switchMeters = new ArrayList<>();
                    if (replies != null) {
                        replies.forEach(reply -> switchMeters.addAll(
                                OfMeterConverter.INSTANCE.convertToMeterSpeakerData(reply, inaccurate)));
                    }
                    return CompletableFuture.completedFuture(switchMeters);
                })
                .exceptionally(ex -> {
                    log.error("Can't get meters from switch", ex);
                    return Collections.emptyList();
                });
    }

    /**
     * Request group data from switch and convert it to RuleManager representation.
     */
    public CompletableFuture<List<GroupSpeakerData>> getGroups() {
        return OfUtils.getGroups(messageContext, iofSwitch)
                .thenCompose(replies -> {
                    log.debug("Get group stats: {} (key={})", replies, kafkaKey);
                    List<GroupSpeakerData> switchGroups = new ArrayList<>();
                    if (replies != null) {
                        replies.forEach(reply -> switchGroups.addAll(
                                OfGroupConverter.INSTANCE.convertToGroupSpeakerData(reply,
                                        new SwitchId(iofSwitch.getId().getLong()))));
                    }
                    return CompletableFuture.completedFuture(switchGroups);
                })
                .exceptionally(ex -> {
                    log.error("Can't get groups from switch", ex);
                    return Collections.emptyList();
                });
    }
}
