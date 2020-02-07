/* Copyright 2019 Telstra Open Source
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

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.api.response.SpeakerErrorCode;
import org.openkilda.floodlight.api.response.SpeakerErrorResponse;
import org.openkilda.floodlight.api.response.SpeakerResponse;
import org.openkilda.floodlight.error.SwitchNotFoundException;
import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public abstract class SpeakerCommandRemoteReport extends SpeakerCommandReport {
    public SpeakerCommandRemoteReport(SpeakerCommand<?> command, Exception error) {
        super(command, error);
    }

    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        kafkaProducerService.sendMessageAndTrack(getReplyTopic(kafkaChannel), requestKey, assembleResponse());
    }

    protected SpeakerResponse assembleResponse() {
        SpeakerResponse reply;
        try {
            reply = translateError();
        } catch (Exception e) {
            log.error(String.format("Unhandled exception while processing command %s", command), e);
            reply = makeErrorReply(SpeakerErrorCode.UNKNOWN);
        }

        if (reply == null) {
            log.debug("Command {} successfully completed", command);
            reply = makeSuccessReply();
        }
        return reply;
    }

    protected SpeakerResponse translateError() throws Exception {
        try {
            raiseError();
            return null;
        } catch (SwitchNotFoundException e) {
            return makeErrorReply(SpeakerErrorCode.SWITCH_UNAVAILABLE);
        } catch (SwitchOperationException e) {
            return makeErrorReply(SpeakerErrorCode.UNKNOWN, e.getMessage());
        }
    }

    protected abstract String getReplyTopic(KafkaChannel kafkaChannel);

    protected abstract SpeakerResponse makeSuccessReply();

    protected SpeakerResponse makeErrorReply(SpeakerErrorCode errorCode) {
        return makeErrorReply(errorCode, null);
    }

    protected SpeakerResponse makeErrorReply(SpeakerErrorCode errorCode, String details) {
        log.error("Command {} have failed - {}:{}", command, errorCode, details);

        return SpeakerErrorResponse.builder()
                .messageContext(command.getMessageContext())
                .commandId(command.getCommandId())
                .switchId(command.getSwitchId())
                .errorCode(errorCode)
                .details(details)
                .build();
    }
}
