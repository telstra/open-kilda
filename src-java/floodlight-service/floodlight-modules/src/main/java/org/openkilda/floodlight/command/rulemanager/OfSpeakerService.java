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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.BatchCommandProcessor;
import org.openkilda.floodlight.api.request.rulemanager.BaseSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.ModifySpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.api.request.rulemanager.Origin;
import org.openkilda.floodlight.api.response.rulemanager.SpeakerCommandResponse;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.model.SwitchId;

import edu.umd.cs.findbugs.annotations.NonNull;
import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;

@Slf4j
public class OfSpeakerService implements BatchCommandProcessor {
    private final IOFSwitchService iofSwitchService;
    private final SessionService sessionService;
    private final KafkaUtilityService kafkaUtilityService;
    private final IKafkaProducerService kafkaProducerService;
    private final FeatureDetectorService featureDetectorService;

    public OfSpeakerService(@NonNull FloodlightModuleContext moduleContext) {
        this.iofSwitchService = moduleContext.getServiceImpl(IOFSwitchService.class);
        this.sessionService = moduleContext.getServiceImpl(SessionService.class);
        this.kafkaUtilityService = moduleContext.getServiceImpl(KafkaUtilityService.class);
        this.kafkaProducerService = moduleContext.getServiceImpl(IKafkaProducerService.class);
        this.featureDetectorService = moduleContext.getServiceImpl(FeatureDetectorService.class);
    }

    @Override
    public void processBatchInstall(InstallSpeakerCommandsRequest request, String key) {
        processBatchRequest(request, key, this::buildInstallOfCommand, request.isFailIfExists());
    }

    @Override
    public void processBatchModify(ModifySpeakerCommandsRequest request, String key) {
        processBatchRequest(request, key, this::buildModifyOfCommand, true);
    }

    @Override
    public void processBatchDelete(DeleteSpeakerCommandsRequest request, String key) {
        processBatchRequest(request, key, this::buildDeleteOfCommand, true);
    }

    private void processBatchRequest(BaseSpeakerCommandsRequest request, String key,
                                     OfCommandProcessor processor, boolean failIfExists) {
        SwitchId switchId = request.getSwitchId();
        DatapathId dpId = DatapathId.of(switchId.toLong());
        IOFSwitch sw = iofSwitchService.getSwitch(dpId);
        OfBatchHolder holder = new OfBatchHolder(iofSwitchService, request.getMessageContext(),
                request.getCommandId(), request.getSwitchId());
        for (OfCommand data : request.getCommands()) {
            processor.process(data, holder, switchId);
        }
        if (sw == null) {
            log.warn("Switch {} not found. Can't process request {}.", switchId, request);
            processResponse(holder.getResult(), key, request.getOrigin());
            return;
        }
        OfBatchExecutor executor = OfBatchExecutor.builder()
                .iofSwitch(sw)
                .commandProcessor(this)
                .sessionService(sessionService)
                .messageContext(request.getMessageContext())
                .holder(holder)
                .switchFeatures(featureDetectorService.detectSwitch(sw))
                .kafkaKey(key)
                .origin(request.getOrigin())
                .failIfExists(failIfExists)
                .build();
        executor.executeBatch();
    }

    private void buildInstallOfCommand(OfCommand ofCommand, OfBatchHolder holder, SwitchId switchId) {
        ofCommand.buildInstall(holder, switchId);
    }

    private void buildModifyOfCommand(OfCommand ofCommand, OfBatchHolder holder, SwitchId switchId) {
        ofCommand.buildModify(holder, switchId);
    }

    private void buildDeleteOfCommand(OfCommand ofCommand, OfBatchHolder holder, SwitchId switchId) {
        ofCommand.buildDelete(holder, switchId);
    }

    @Override
    public void processResponse(SpeakerCommandResponse response, String kafkaKey, Origin origin) {
        KafkaChannel kafkaChannel = kafkaUtilityService.getKafkaChannel();
        String topic = getTopic(kafkaChannel, origin);
        log.debug("Send response to {} (key={})", topic, kafkaKey);
        kafkaProducerService.sendMessageAndTrack(topic, kafkaKey, response);
    }

    private String getTopic(KafkaChannel kafkaChannel, Origin origin) {
        //TODO: remove origin and detect response topic by income topic
        switch (origin) {
            case FLOW_HS:
                return kafkaChannel.getSpeakerFlowHsTopic();
            case SW_MANAGER:
                return kafkaChannel.getSpeakerSwitchManagerResponseTopic();
            case NETWORK:
                return kafkaChannel.getNetworkControlResponseTopic();
            default:
                throw new IllegalStateException(format("Unknown message origin %s", origin));
        }
    }

    @FunctionalInterface
    private interface OfCommandProcessor {
        void process(OfCommand ofCommand, OfBatchHolder holder, SwitchId switchId);
    }
}
