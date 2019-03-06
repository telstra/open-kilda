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

import static org.openkilda.messaging.Utils.ETH_TYPE;

import org.openkilda.messaging.MessageContext;
import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.types.OFPort;

import java.util.ArrayList;
import java.util.List;

public class InstallOneSwitchRuleCommand extends InstallIngressRuleCommand {

    @JsonProperty("output_vlan_id")
    private final Integer outputVlanId;

    @JsonCreator
    public InstallOneSwitchRuleCommand(@JsonProperty("command_id") String commandId,
                                       @JsonProperty("flowid") String flowid,
                                       @JsonProperty("message_context") MessageContext messageContext,
                                       @JsonProperty("cookie") Long cookie,
                                       @JsonProperty("switch_id") SwitchId switchId,
                                       @JsonProperty("input_port") Integer inputPort,
                                       @JsonProperty("output_port") Integer outputPort,
                                       @JsonProperty("transit_vlan_id") Integer transitVlanId,
                                       @JsonProperty("bandwidth") Long bandwidth,
                                       @JsonProperty("input_vlan_id") Integer inputVlanId,
                                       @JsonProperty("output_vlan_type") OutputVlanType outputVlanType,
                                       @JsonProperty("meter_id") Long meterId,
                                       @JsonProperty("output_vlan_id") Integer outputVlanId) {
        super(commandId, flowid, messageContext, cookie, switchId, inputPort, outputPort, transitVlanId, bandwidth,
                inputVlanId, outputVlanType, meterId);
        this.outputVlanId = outputVlanId;
    }

    @Override
    OFPort getOutputPort() {
        return outputPort.equals(inputPort) ? OFPort.IN_PORT : OFPort.of(outputPort);
    }

    @Override
    List<OFAction> getOutputAction(OFFactory ofFactory) {
        return pushSchemeOutputVlanTypeToOfActionList(ofFactory);
    }

    private List<OFAction> pushSchemeOutputVlanTypeToOfActionList(OFFactory ofFactory) {
        List<OFAction> actionList = new ArrayList<>(2);

        switch (outputVlanType) {
            case PUSH:      // No VLAN on packet so push a new one
                actionList.add(actionPushVlan(ofFactory, ETH_TYPE));
                actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
                break;
            case REPLACE:   // VLAN on packet but needs to be replaced
                actionList.add(actionReplaceVlan(ofFactory, outputVlanId));
                break;
            case POP:       // VLAN on packet, so remove it
                actionList.add(ofFactory.actions().popVlan());
                break;
            case NONE:
                break;
            default:
                throw new UnsupportedOperationException(String.format("Incorrect output vlan type: %s",
                        outputVlanType));
        }

        return actionList;
    }

}
