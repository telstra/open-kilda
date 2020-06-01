/* Copyright 2020 Telstra Open Source
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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.KafkaChannel;
import org.openkilda.floodlight.command.SpeakerCommandRemoteReport;
import org.openkilda.floodlight.service.kafka.IKafkaProducerService;
import org.openkilda.messaging.Message;

import lombok.extern.slf4j.Slf4j;

import java.util.Optional;

@Slf4j
public class FlowSegmentWrapperReport extends SpeakerCommandRemoteReport {
    private final FlowSegmentResponseFactory responseFactory;

    public FlowSegmentWrapperReport(FlowSegmentWrapperCommand command, FlowSegmentResponseFactory responseFactory) {
        this(command, responseFactory, null);
    }

    public FlowSegmentWrapperReport(
            FlowSegmentWrapperCommand command, FlowSegmentResponseFactory responseFactory, Exception error) {
        super(command, error);
        this.responseFactory = responseFactory;
    }

    @Override
    public void reply(KafkaChannel kafkaChannel, IKafkaProducerService kafkaProducerService, String requestKey) {
        // FIXME(surabujin): do we really need to set kafka-key to correlation id here?
        makeResponse()
                .ifPresent(response -> kafkaProducerService.sendMessageAndTrack(
                        responseFactory.getTargetKafkaTopic(), responseFactory.makeKafkaKey(requestKey), response));
    }

    private Optional<Message> makeResponse() {
        try {
            raiseError();
            return responseFactory.makeSuccessResponse();
        } catch (Exception e) {
            log.error("Command {} have failed - {}", command, e);
            return responseFactory.makeFailResponse(e);
        }
    }
}
