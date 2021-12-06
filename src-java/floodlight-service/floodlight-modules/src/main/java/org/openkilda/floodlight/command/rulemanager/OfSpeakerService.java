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

import org.openkilda.floodlight.api.BatchCommandProcessor;
import org.openkilda.floodlight.api.request.rulemanager.DeleteSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.InstallSpeakerCommandsRequest;
import org.openkilda.floodlight.api.request.rulemanager.OfCommand;
import org.openkilda.floodlight.service.FeatureDetectorService;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.floodlight.service.kafka.KafkaUtilityService;
import org.openkilda.floodlight.service.session.SessionService;
import org.openkilda.model.SwitchId;

import edu.umd.cs.findbugs.annotations.NonNull;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.IOFSwitchService;
import net.floodlightcontroller.core.module.FloodlightModuleContext;
import org.projectfloodlight.openflow.types.DatapathId;

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
        SwitchId switchId = request.getSwitchId();
        DatapathId dpId = DatapathId.of(switchId.toLong());
        IOFSwitch sw = iofSwitchService.getSwitch(dpId);
        OfBatchHolder holder = new OfBatchHolder(iofSwitchService, request.getMessageContext());
        for (OfCommand data : request.getCommands()) {
            data.buildInstall(holder, switchId);
        }
        OfBatchExecutor executor = OfBatchExecutor.builder()
                .iofSwitch(sw)
                .kafkaUtilityService(kafkaUtilityService)
                .kafkaProducerService(kafkaProducerService)
                .sessionService(sessionService)
                .messageContext(request.getMessageContext())
                .holder(holder)
                .switchFeatures(featureDetectorService.detectSwitch(sw))
                .kafkaKey(key)
                .build();
        executor.executeBatch();
    }

    @Override
    public void processBatchDelete(DeleteSpeakerCommandsRequest request, String key) {
        SwitchId switchId = request.getSwitchId();
        DatapathId dpId = DatapathId.of(switchId.toLong());
        IOFSwitch sw = iofSwitchService.getSwitch(dpId);
        OfBatchHolder holder = new OfBatchHolder(iofSwitchService, request.getMessageContext());
        for (OfCommand data : request.getCommands()) {
            data.buildDelete(holder, switchId);
        }
        OfBatchExecutor executor = OfBatchExecutor.builder()
                .iofSwitch(sw)
                .kafkaUtilityService(kafkaUtilityService)
                .kafkaProducerService(kafkaProducerService)
                .sessionService(sessionService)
                .messageContext(request.getMessageContext())
                .holder(holder)
                .switchFeatures(featureDetectorService.detectSwitch(sw))
                .kafkaKey(key)
                .build();
        executor.executeBatch();
    }
}
