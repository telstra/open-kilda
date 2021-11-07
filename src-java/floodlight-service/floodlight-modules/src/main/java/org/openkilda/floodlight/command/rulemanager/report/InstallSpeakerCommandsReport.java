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

package org.openkilda.floodlight.command.rulemanager.report;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.response.SpeakerErrorCode;
import org.openkilda.floodlight.api.response.SpeakerErrorResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.api.response.rulemanager.InstallSpeakerCommandsResponse;
import org.openkilda.floodlight.command.SpeakerCommand;
import org.openkilda.floodlight.command.SpeakerCommandRemoteReport;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class InstallSpeakerCommandsReport extends SpeakerCommandRemoteReport {

    public InstallSpeakerCommandsReport(SpeakerCommand<?> command, Exception error) {
        super(command, error);
    }

    public InstallSpeakerCommandsReport(SpeakerCommand<?> command) {
        super(command, null);
    }

    private SpeakerResponse assembleResponse() {
        SpeakerErrorResponse errorResponse = null;
        try {
            raiseError();
            return makeSuccessReply();
        } catch (Exception e) {
            errorResponse = SpeakerErrorResponse.builder()
                    .commandId(command.getCommandId())
                    .messageContext(command.getMessageContext())
                    .switchId(command.getSwitchId())
                    .errorCode(SpeakerErrorCode.UNKNOWN)
                    .details(e.getMessage())
                    .build();
            log.error("Command {} have failed", command, e);
        }

        return errorResponse;
    }

    @Override
    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        kafkaProducerService.sendMessageAndTrack(kafkaChannel.getTopoSwitchManagerTopic(), requestKey,
                assembleResponse());
    }

    private InstallSpeakerCommandsResponse makeSuccessReply() {
        return InstallSpeakerCommandsResponse.builder()
                .commandId(command.getCommandId())
                .messageContext(command.getMessageContext())
                .switchId(command.getSwitchId())
                .success(true)
                .build();
    }
}
