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

package org.openkilda.floodlight.command.flow;

import org.openkilda.floodlight.FloodlightResponse;
import org.openkilda.floodlight.flow.response.FlowResponse;
import org.openkilda.messaging.MessageContext;
import org.openkilda.model.SwitchId;

import net.floodlightcontroller.util.FlowModUtils;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowAdd;
import org.projectfloodlight.openflow.types.OFBufferId;
import org.projectfloodlight.openflow.types.U64;

public abstract class FlowInstallCommand extends FlowCommand {

    final Integer inputPort;
    final Integer outputPort;

    FlowInstallCommand(String commandId, String flowId, MessageContext messageContext, Long cookie, SwitchId switchId,
                       Integer inputPort, Integer outputPort) {
        super(commandId, flowId, messageContext, cookie, switchId);
        this.inputPort = inputPort;
        this.outputPort = outputPort;
    }

    @Override
    protected FloodlightResponse buildResponse() {
        return FlowResponse.builder()
                .commandId(commandId)
                .flowId(flowId)
                .messageContext(messageContext)
                .success(true)
                .switchId(switchId)
                .build();
    }

    final OFFlowAdd.Builder prepareFlowModBuilder(OFFactory ofFactory, long cookie, int priority) {
        return ofFactory.buildFlowAdd()
                .setIdleTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setHardTimeout(FlowModUtils.INFINITE_TIMEOUT)
                .setBufferId(OFBufferId.NO_BUFFER)
                .setCookie(U64.of(cookie))
                .setPriority(priority);
    }
}
