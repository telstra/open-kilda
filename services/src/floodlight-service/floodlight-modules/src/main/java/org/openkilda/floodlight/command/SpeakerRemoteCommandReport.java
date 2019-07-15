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
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;

public abstract class SpeakerRemoteCommandReport extends SpeakerCommandReport {
    protected final SpeakerRemoteCommand<?> command;

    protected SpeakerRemoteCommandReport(SpeakerRemoteCommand<?> command, Exception error) {
        super(error);
        this.command = command;
    }

    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        kafkaProducerService.sendMessageAndTrack(getReplyTopic(kafkaChannel), requestKey, assembleResponse());
    }

    protected SpeakerResponse assembleResponse() {
        SpeakerErrorCode errorCode;
        try {
            errorCode = makeErrorCode();
        } catch (Exception e) {
            errorCode = SpeakerErrorCode.UNKNOWN;
        }

        SpeakerResponse reply;
        if (errorCode == null) {
            reply = makeSuccessReply();
        } else {
            reply = makeErrorReply(errorCode);
        }
        return reply;
    }

    protected SpeakerErrorCode makeErrorCode() throws Exception {
        try {
            raiseError();
            return null;
        } catch (SwitchNotFoundException e) {
            return SpeakerErrorCode.SWITCH_UNAVAILABLE;
        }
    }

    protected abstract String getReplyTopic(KafkaChannel kafkaChannel);

    protected abstract SpeakerResponse makeSuccessReply();

    protected SpeakerResponse makeErrorReply(SpeakerErrorCode errorCode) {
        return SpeakerErrorResponse.builder()
                .messageContext(command.getMessageContext())
                .commandId(command.getCommandId())
                .switchId(command.getSwitchId())
                .errorCode(errorCode)
                .build();
    }
}
