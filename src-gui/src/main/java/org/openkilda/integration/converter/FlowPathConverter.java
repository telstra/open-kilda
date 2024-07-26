/* Copyright 2024 Telstra Open Source
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

package org.openkilda.integration.converter;

import static org.openkilda.utility.SwitchUtil.customSwitchName;

import org.openkilda.integration.model.response.FlowPathNode;
import org.openkilda.integration.model.response.FlowPayload;
import org.openkilda.integration.model.response.OtherFlows;

import java.util.List;
import java.util.Map;


public final class FlowPathConverter {

    private FlowPathConverter() {
    }

    /**
     * Gets the flow path.
     *
     * @param flowPayload the Flow Payload
     * @return the flow path
     */
    public static FlowPayload getFlowPath(final FlowPayload flowPayload, Map<String, String> csNames) {
        setSwitchName(flowPayload.getForward(), csNames);
        setSwitchName(flowPayload.getReverse(), csNames);
        if (flowPayload.getProtectedPath() != null) {
            setSwitchName(flowPayload.getProtectedPath().getForward(), csNames);
            setSwitchName(flowPayload.getProtectedPath().getReverse(), csNames);
        }
        if (flowPayload.getDiversePath() != null) {
            List<OtherFlows> otherFlows = flowPayload.getDiversePath().getOtherFlows();
            if (otherFlows != null) {
                otherFlows.parallelStream().forEach((otherFlow) -> {
                    setSwitchName(otherFlow.getForward(), csNames);
                    setSwitchName(otherFlow.getReverse(), csNames);
                });
            }
        }
        return flowPayload;
    }

    /**
     * Sets the switch name.
     *
     * @param pathNodes the path nodes
     * @param csNames   the cs names
     */
    private static void setSwitchName(List<FlowPathNode> pathNodes, Map<String, String> csNames) {
        pathNodes.parallelStream().forEach((pathNode)
                -> pathNode.setSwitchName(customSwitchName(csNames, pathNode.getSwitchId())));
    }

}
