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

package org.openkilda.grpc.speaker.messaging;

import org.openkilda.grpc.speaker.mapper.RequestMapper;
import org.openkilda.grpc.speaker.service.GrpcSenderService;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DumpLogicalPortsRequest;
import org.openkilda.messaging.command.grpc.GetSwitchInfoRequest;
import org.openkilda.messaging.info.InfoData;
import org.openkilda.messaging.info.grpc.CreateLogicalPortResponse;
import org.openkilda.messaging.info.grpc.DumpLogicalPortsResponse;
import org.openkilda.messaging.info.grpc.GetSwitchInfoResponse;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class MessageProcessor {

    @Autowired
    KafkaMessageProducer messageProducer;

    @Autowired
    private GrpcSenderService service;

    @Autowired
    RequestMapper requestMapper;

    // TODO error handling
    public void processRequest(CommandData command) {
        if (command instanceof CreateLogicalPortRequest) {
            CreateLogicalPortRequest req = (CreateLogicalPortRequest) command;
            service.createLogicalPort(req.getAddress(), requestMapper.toLogicalPort(req))
                    .thenAccept(e -> sendResponse(new CreateLogicalPortResponse(req.getAddress(), e, true)));

        } else if (command instanceof DumpLogicalPortsRequest) {
            DumpLogicalPortsRequest req = (DumpLogicalPortsRequest) command;
            service.dumpLogicalPorts(req.getAddress())
                    .thenAccept(e -> sendResponse(new DumpLogicalPortsResponse(req.getAddress(), e)));

        } else if (command instanceof GetSwitchInfoRequest) {
            GetSwitchInfoRequest req = (GetSwitchInfoRequest) command;
            service.getSwitchStatus(req.getAddress())
                    .thenAccept(e -> sendResponse(new GetSwitchInfoResponse(req.getAddress(), e)));

        }
    }

    private void sendResponse(InfoData message) {
        // TODO topic hardcode
        messageProducer.send("grpc.response", message);
    }
}
