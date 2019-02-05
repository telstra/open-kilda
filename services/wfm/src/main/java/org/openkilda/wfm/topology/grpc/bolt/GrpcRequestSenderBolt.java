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

import org.openkilda.wfm.AbstractBolt;
import org.openkilda.wfm.topology.AbstractTopology;
import org.openkilda.wfm.topology.grpc.model.BaseGrpcRequest;
import org.openkilda.wfm.topology.grpc.model.CreatePortRequest;
import org.openkilda.wfm.topology.grpc.model.GetAllPortsRequest;
import org.openkilda.wfm.topology.grpc.service.GrpcSenderService;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import lombok.extern.slf4j.Slf4j;
import org.apache.storm.topology.OutputFieldsDeclarer;
import org.apache.storm.tuple.Tuple;

@Slf4j
public class GrpcRequestSenderBolt extends AbstractBolt {

    private static final int PORT = 50051;

    @Override
    protected void handleInput(Tuple input) {
        BaseGrpcRequest baseGrpcRequest = (BaseGrpcRequest) input.getValueByField(AbstractTopology.MESSAGE_FIELD);
        ManagedChannel channel = ManagedChannelBuilder.forAddress(baseGrpcRequest.getAddress(), PORT).build();
        GrpcSenderService service = new GrpcSenderService(channel);

        if (baseGrpcRequest instanceof CreatePortRequest) {
            CreatePortRequest request = (CreatePortRequest) baseGrpcRequest;
            service.createPort(request);
        } else if (baseGrpcRequest instanceof GetAllPortsRequest) {
            GetAllPortsRequest request = (GetAllPortsRequest) baseGrpcRequest;
            service.loadAllPorts(request);
        } else {
            log.warn("Received unexpected request {}", baseGrpcRequest);
        }
    }

    @Override
    public void declareOutputFields(OutputFieldsDeclarer declarer) {
        declarer.declare(AbstractTopology.fieldMessage);
    }
}
