/* Copyright 2021 Telstra Open Source
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

import static java.lang.String.format;

import org.openkilda.messaging.command.flow.RemoveFlow;
import org.openkilda.messaging.command.grpc.CreateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.command.switches.DeleteRulesCriteria;
import org.openkilda.messaging.info.rule.FlowApplyActions;
import org.openkilda.messaging.info.rule.FlowEntry;
import org.openkilda.messaging.info.rule.FlowInstructions;
import org.openkilda.messaging.info.rule.FlowMatchField;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.model.FlowEncapsulationType;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import com.fasterxml.uuid.Generators;
import com.fasterxml.uuid.NoArgGenerator;
import com.google.common.annotations.VisibleForTesting;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.math.NumberUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private final NoArgGenerator transactionIdGenerator = Generators.timeBasedGenerator();

    private final SwitchRepository switchRepository;

    public CommandBuilderImpl(PersistenceManager persistenceManager) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    public List<RemoveFlow> buildCommandsToRemoveExcessRules(SwitchId switchId,
                                                             List<FlowEntry> flows,
                                                             Set<Long> excessRulesCookies) {
        return flows.stream()
                .filter(flow -> excessRulesCookies.contains(flow.getCookie()))
                .map(entry -> buildRemoveFlowWithoutMeterFromFlowEntry(switchId, entry))
                .collect(Collectors.toList());
    }

    @Override
    public List<CreateLogicalPortRequest> buildLogicalPortInstallCommands(
            SwitchId switchId, List<LogicalPortInfoEntry> missingLogicalPorts) {
        String ipAddress = getSwitchIpAddress(switchId);

        List<CreateLogicalPortRequest> requests = new ArrayList<>();
        for (LogicalPortInfoEntry port : missingLogicalPorts) {
            requests.add(new CreateLogicalPortRequest(
                    ipAddress, port.getPhysicalPorts(), port.getLogicalPortNumber(),
                    LogicalPortMapper.INSTANCE.map(port.getType())));
        }
        return requests;
    }

    @Override
    public List<DeleteLogicalPortRequest> buildLogicalPortDeleteCommands(
            SwitchId switchId, List<Integer> excessLogicalPorts) {
        String ipAddress = getSwitchIpAddress(switchId);

        return excessLogicalPorts.stream()
                .map(port -> new DeleteLogicalPortRequest(ipAddress, port))
                .collect(Collectors.toList());
    }

    private String getSwitchIpAddress(SwitchId switchId) {
        Switch sw = switchRepository.findById(switchId).orElseThrow(() -> new SwitchNotFoundException(switchId));
        return Optional.ofNullable(sw.getSocketAddress()).map(IpSocketAddress::getAddress).orElseThrow(
                () -> new IllegalStateException(format("Unable to get IP address of switch %s", switchId)));
    }

    @VisibleForTesting
    RemoveFlow buildRemoveFlowWithoutMeterFromFlowEntry(SwitchId switchId, FlowEntry entry) {
        Optional<FlowMatchField> entryMatch = Optional.ofNullable(entry.getMatch());

        Integer inPort = entryMatch.map(FlowMatchField::getInPort).map(Integer::valueOf).orElse(null);

        FlowEncapsulationType encapsulationType = FlowEncapsulationType.TRANSIT_VLAN;
        Integer encapsulationId = null;
        Integer vlan = entryMatch.map(FlowMatchField::getVlanVid).map(Integer::valueOf).orElse(null);
        if (vlan != null) {
            encapsulationId = vlan;
        } else {
            Integer tunnelId = entryMatch.map(FlowMatchField::getTunnelId).map(Integer::decode).orElse(null);

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
        Long metadataValue = entryMatch.map(FlowMatchField::getMetadataValue).map(Long::decode).orElse(null);
        Long metadataMask = entryMatch.map(FlowMatchField::getMetadataMask).map(Long::decode).orElse(null);

        DeleteRulesCriteria criteria = new DeleteRulesCriteria(entry.getCookie(), inPort, encapsulationId,
                0, outPort, encapsulationType, ingressSwitchId, metadataValue, metadataMask);

        return RemoveFlow.builder()
                .transactionId(transactionIdGenerator.generate())
                .flowId("SWMANAGER_BATCH_REMOVE")
                .cookie(entry.getCookie())
                .switchId(switchId)
                .criteria(criteria)
                .build();
    }
}
