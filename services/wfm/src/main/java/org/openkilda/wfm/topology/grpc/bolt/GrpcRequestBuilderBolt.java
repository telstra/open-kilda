/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.grpc.bolt;

import static org.openkilda.wfm.topology.AbstractTopology.MESSAGE_FIELD;
import static org.openkilda.wfm.topology.AbstractTopology.fieldMessage;

import org.openkilda.messaging.BaseMessage;
import org.openkilda.messaging.command.CommandData;
import org.openkilda.messaging.command.bfd.CreateLogicalPortRequest;
import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.error.AbstractException;
import org.openkilda.wfm.topology.grpc.service.GrpcRequestBuilderService;

import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

public class GrpcRequestBuilderBolt extends AbstractBolt {

    private GrpcRequestBuilderService service;

    public GrpcRequestBuilderBolt(String username, String password) {
        this.service = new GrpcRequestBuilderService(username, password);
    }

    @Override
    protected void handleInput(Tuple input) throws AbstractException {
        BaseMessage message = (BaseMessage) input.getValueByField(MESSAGE_FIELD);

        if (message instanceof CommandData) {
            CommandData data = (CommandData) message;
            if (data instanceof CreateLogicalPortRequest) {
                CreateLogicalPortRequest request = (CreateLogicalPortRequest) data;
                service.buildCreatePortRequest(request);
            }
        } else {
            log.warn("Received unsupported message: {}", message);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(fieldMessage);
    }
}
