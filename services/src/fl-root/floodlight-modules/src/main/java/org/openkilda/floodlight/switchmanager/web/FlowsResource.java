/* Copyright 2017 Telstra Open Source
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

package org.openkilda.floodlight.switchmanager.web;

import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.error.SwitchOperationException;
import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.floodlight.utils.CorrelationContext;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;

import com.google.common.collect.ImmutableList;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFInstructionType;
import org.projectfloodlight.openflow.protocol.action.OFAction;
import org.projectfloodlight.openflow.protocol.action.OFActionMeter;
import org.projectfloodlight.openflow.protocol.action.OFActionOutput;
import org.projectfloodlight.openflow.protocol.action.OFActionPushVlan;
import org.projectfloodlight.openflow.protocol.action.OFActionSetField;
import org.projectfloodlight.openflow.protocol.instruction.OFInstruction;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionApplyActions;
import org.projectfloodlight.openflow.protocol.instruction.OFInstructionMeter;
import org.projectfloodlight.openflow.protocol.match.Match;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.protocol.oxm.OFOxm;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.OFValueType;
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

public class FlowsResource extends ServerResource {
    private static final Logger LOGGER = LoggerFactory.getLogger(FlowsResource.class);
    //should be extended once we find match field that should be shown in the response
    private static final List<MatchField<? extends OFValueType>> KNOWN_MATCHES = ImmutableList.of(
            MatchField.ETH_SRC, MatchField.ETH_DST, MatchField.IN_PORT, MatchField.VLAN_VID,
            MatchField.IP_PROTO
    );

    private Map<String, Object> buildFlowMatch(final Match match) {
        final Map<String, Object> data = new HashMap<>();
        //There is only one way to receive match by type
        for (MatchField<? extends OFValueType> field : KNOWN_MATCHES) {
            OFValueType value = match.get(field);
            if (Objects.nonNull(value)) {
                data.put(field.getName(), value.toString());
            }
        }
        return data;
    }

    private Map<String, Object> buildFlowInstructions(final List<OFInstruction> instructions) {
        Map<String, Object> data = new HashMap<>();
        for (OFInstruction instruction : instructions) {
            Map<String, Object> instructionData = new HashMap<>();
            OFInstructionType instructionType = instruction.getType();
            switch (instructionType) {
                case APPLY_ACTIONS:
                    for (OFAction action : ((OFInstructionApplyActions) instruction).getActions()) {
                        OFActionType actionType = action.getType();
                        switch (actionType) {
                            case METER: // ver1.5
                                instructionData.put(actionType.toString(), ((OFActionMeter) action).getMeterId());
                                break;
                            case OUTPUT:
                                Optional.ofNullable(((OFActionOutput) action).getPort())
                                        .ifPresent(port -> instructionData.put(actionType.toString(), port.toString()));
                                break;
                            case POP_VLAN:
                                instructionData.put(actionType.toString(), null);
                                break;
                            case PUSH_VLAN:
                                Optional.ofNullable(((OFActionPushVlan) action).getEthertype())
                                        .ifPresent(ethType -> instructionData.put(
                                                actionType.toString(), ethType.toString()));
                                break;
                            case SET_FIELD:
                                OFOxm<?> setFieldAction = ((OFActionSetField) action).getField();
                                instructionData.put(actionType.toString(), String.format("%s->%s",
                                        setFieldAction.getValue(), setFieldAction.getMatchField().getName()));
                                break;
                            default:
                                instructionData.put(actionType.toString(), "could not parse");
                                break;
                        }
                    }
                    break;
                case METER:
                    OFInstructionMeter action = ((OFInstructionMeter) instruction);
                    instructionData.put(instructionType.toString(), action.getMeterId());
                    break;
                default:
                    instructionData.put(instructionType.toString(), "could not parse");
                    break;
            }
            data.put(instruction.getType().name(), instructionData);
        }
        return data;
    }

    private Map<String, Object> buildFlowStat(final OFFlowStatsEntry entry) {
        Map<String, Object> data = new HashMap<>();
        data.put("version", entry.getVersion());
        data.put("duration-nsec", entry.getDurationNsec());
        data.put("duration-sec", entry.getDurationSec());
        data.put("hard-timeout", entry.getHardTimeout());
        data.put("idle-timeout", entry.getIdleTimeout());
        data.put("priority", entry.getPriority());
        data.put("byte-count", entry.getByteCount().getValue());
        data.put("packet-count", entry.getPacketCount().getValue());
        data.put("flags", entry.getFlags());
        data.put("cookie", Long.toHexString(entry.getCookie().getValue()));
        data.put("table-id", entry.getTableId().getValue());
        data.put("match", buildFlowMatch(entry.getMatch()));
        data.put("instructions", buildFlowInstructions(entry.getInstructions()));
        return data;
    }

    /**
     * The method returns the flow for the switch from the request.
     */
    @Get("json")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFlows() throws SwitchOperationException {
        Map<String, Object> response = new HashMap<>();
        String switchId = (String) this.getRequestAttributes().get("switch_id");
        LOGGER.debug("Get flows for switch: {}", switchId);
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        try {
            List<OFFlowStatsEntry> flowEntries = switchManager.dumpFlowTable(DatapathId.of(switchId));
            LOGGER.debug("OF_STATS: {}", flowEntries);

            if (flowEntries != null) {
                for (OFFlowStatsEntry entry : flowEntries) {
                    String key = String.format("flow-0x%s",
                            Long.toHexString(entry.getCookie().getValue()).toUpperCase());
                    response.put(key, buildFlowStat(entry));
                }
            }
        } catch (IllegalArgumentException exception) {
            String messageString = "No such switch";
            LOGGER.error("{}: {}", messageString, switchId, exception);
            MessageError responseMessage = new MessageError(CorrelationContext.getId(), System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID.toString(), messageString, exception.getMessage());
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));
        }
        return response;
    }
}
