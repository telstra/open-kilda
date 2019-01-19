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

package org.openkilda.northbound.service.impl;

import org.openkilda.messaging.history.FlowEventData;
import org.openkilda.messaging.history.HistoryData;
import org.openkilda.messaging.history.HistoryMessage;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.openkilda.northbound.messaging.MessagingChannel;
import org.openkilda.northbound.service.AuthService;
import org.openkilda.northbound.service.HistoryService;
import org.openkilda.northbound.utils.RequestCorrelationId;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class HistoryServiceImpl implements HistoryService {

    @Autowired
    private AuthService authService;

    @Autowired
    private MessagingChannel messagingChannel;

    @Value("#{kafkaTopicsConfig.getHistoryTopic()}")
    private String topic;

    @Override
    public void logAction(String action, FlowPayload flow, String details) {
        FlowEventData flowEventData = FlowEventData.builder()
                .flowId(flow.getId())
                .sourceSwitch(flow.getSource().getDatapath().toString())
                .destinationSwitch(flow.getDestination().getDatapath().toString())
                .actor(authService.getUserName())
                .action(action)
                .details(details)
                .build();
        send(flowEventData);
    }

    private void send(HistoryData historyData) {
        HistoryMessage message =
                new HistoryMessage(System.currentTimeMillis(), RequestCorrelationId.getId(), historyData);
        messagingChannel.send(topic, message);
    }
}
