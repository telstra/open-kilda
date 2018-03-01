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

import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;
import static org.openkilda.messaging.Utils.MAPPER;

import org.openkilda.floodlight.switchmanager.ISwitchManager;
import org.openkilda.messaging.error.ErrorType;
import org.openkilda.messaging.error.MessageError;

import org.apache.commons.httpclient.HttpStatus;
import org.projectfloodlight.openflow.protocol.OFActionType;
import org.projectfloodlight.openflow.protocol.OFFlowStatsEntry;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
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
import org.restlet.resource.Get;
import org.restlet.resource.ServerResource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FlowsResource extends ServerResource {
    private static final Logger logger = LoggerFactory.getLogger(FlowsResource.class);

    private Map<String, Object> buildFlowMatch(final Match match) {
        Map<String, Object> data = new HashMap<>();
        for (MatchField field : match.getMatchFields()) {
            data.put(field.getName(), field.getPrerequisites());
        }
        return data;
    }

    private Map<String, Object> buildFlowInstructions(final List<OFInstruction> instructions) {
        Map<String, Object> data = new HashMap<>();
        for (OFInstruction instruction : instructions) {
            Map<String, Object> iData = new HashMap<>();
            OFInstructionType instructionType = instruction.getType();
            switch (instructionType) {
                case APPLY_ACTIONS:
                    for (OFAction action : ((OFInstructionApplyActions) instruction).getActions()) {
                        OFActionType actionType = action.getType();
                        switch (actionType) {
                            case METER: // ver1.5
                                iData.put(actionType.toString(), ((OFActionMeter) action).getMeterId());
                                break;
                            case OUTPUT:
                                iData.put(actionType.toString(), ((OFActionOutput) action).getPort());
                                break;
                            case POP_VLAN:
                                iData.put(actionType.toString(), null);
                                break;
                            case PUSH_VLAN:
                                iData.put(actionType.toString(), ((OFActionPushVlan) action).getEthertype());
                                break;
                            case SET_FIELD:
                                OFOxm<?> setFieldAction = ((OFActionSetField) action).getField();
                                iData.put(actionType.toString(), String.format("%s->%s",
                                        setFieldAction.getValue(), setFieldAction.getMatchField().getName()));
                                break;
                            default:
                                iData.put(actionType.toString(), "could not parse");
                                break;
                        }
                    }
                    break;
                case METER:
                    OFInstructionMeter action = ((OFInstructionMeter) instruction);
                    iData.put(instructionType.toString(), action.getMeterId());
                    break;
                default:
                    iData.put(instructionType.toString(), "could not parse");
                    break;
            }
            data.put(instruction.getType().name(), iData);
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

    @Get("json")
    @SuppressWarnings("unchecked")
    public Map<String, Object> getFlows() {
        Map<String, Object> response = new HashMap<>();
        String switchId = (String) this.getRequestAttributes().get("switch_id");
        logger.debug("Get flows for switch: {}", switchId);
        ISwitchManager switchManager = (ISwitchManager) getContext().getAttributes()
                .get(ISwitchManager.class.getCanonicalName());

        try {
            OFFlowStatsReply replay = switchManager.dumpFlowTable(DatapathId.of(switchId));
            logger.debug("OF_STATS: {}", replay);

            if (replay != null) {
                for (OFFlowStatsEntry entry : replay.getEntries()) {
                    String key = String.format("flow-0x%s",
                            Long.toHexString(entry.getCookie().getValue()).toUpperCase());
                    response.put(key, buildFlowStat(entry));
                }
            }
        } catch (IllegalArgumentException exception) {
            String messageString = "No such switch";
            logger.error("{}: {}", messageString, switchId, exception);
            MessageError responseMessage = new MessageError(DEFAULT_CORRELATION_ID, System.currentTimeMillis(),
                    ErrorType.PARAMETERS_INVALID.toString(), messageString, exception.getMessage());
            response.putAll(MAPPER.convertValue(responseMessage, Map.class));
        }
        return response;
    }
}
