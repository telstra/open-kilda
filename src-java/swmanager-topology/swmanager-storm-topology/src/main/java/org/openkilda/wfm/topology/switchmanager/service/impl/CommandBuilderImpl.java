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

package org.openkilda.wfm.topology.switchmanager.service.impl;

import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    @Override
    public List<RemoveFlow> buildCommandsToRemoveExcessRules(SwitchId switchId,
                                                             List<FlowEntry> flows,
                                                             List<Long> excessRulesCookies) {
        Set<Long> excess = new HashSet<>(excessRulesCookies);
        Iterator<FlowEntry> iter = flows.iterator();
        List<RemoveFlow> results = new ArrayList<>(excess.size());
        while (! excess.isEmpty() && iter.hasNext()) {
            FlowEntry entry = iter.next();
            if (excess.remove(entry.getCookie())) {
                results.add(buildRemoveFlowWithoutMeterFromFlowEntry(switchId, entry));
            }
        }
        return results;
    }

    // FIXME(surabujin): removed this ugly attempt to "restore" segment view from OF flow, only match field and priority
    //  are required for remove request (or event only cookie, because they are unique now)
    @VisibleForTesting
    RemoveFlow buildRemoveFlowWithoutMeterFromFlowEntry(SwitchId switchId, FlowEntry entry) {
        Optional<FlowMatchField> entryMatch = Optional.ofNullable(entry.getMatch());
        Optional<FlowInstructions> instructions = Optional.ofNullable(entry.getInstructions());
        Optional<FlowApplyActions> applyActions = instructions.map(FlowInstructions::getApplyActions);

        Integer inPort = entryMatch.map(FlowMatchField::getInPort).map(Integer::valueOf).orElse(null);

        FlowEncapsulationType encapsulationType = FlowEncapsulationType.TRANSIT_VLAN;
        Integer encapsulationId = null;
        Integer vlan = entryMatch.map(FlowMatchField::getVlanVid).map(Integer::valueOf).orElse(null);
        if (vlan != null) {
            encapsulationId = vlan;
        } else {
            Integer tunnelId = entryMatch.map(FlowMatchField::getTunnelId).map(Integer::valueOf).orElse(null);
            if (tunnelId == null) {
                tunnelId = applyActions.map(FlowApplyActions::getPushVxlan).map(Integer::valueOf).orElse(null);
            }

            if (tunnelId != null) {
                encapsulationId = tunnelId;
                encapsulationType = FlowEncapsulationType.VXLAN;
            }
        }

        Optional<FlowApplyActions> actions = Optional.ofNullable(entry.getInstructions())
                .map(FlowInstructions::getApplyActions);

        Integer outPort = actions
                .map(FlowApplyActions::getFlowOutput)
                .filter(NumberUtils::isNumber)
                .map(Integer::valueOf)
                .orElse(null);

        SwitchId ingressSwitchId = entryMatch.map(FlowMatchField::getEthSrc).map(SwitchId::new).orElse(null);

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(entry.getCookie(), inPort, encapsulationId,
                0, outPort, encapsulationType, ingressSwitchId);

        return RemoveFlow.builder()
                .transactionId(transactionIdGenerator.generate())
                .flowId("SWMANAGER_BATCH_REMOVE")
                .cookie(entry.getCookie())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }
}
