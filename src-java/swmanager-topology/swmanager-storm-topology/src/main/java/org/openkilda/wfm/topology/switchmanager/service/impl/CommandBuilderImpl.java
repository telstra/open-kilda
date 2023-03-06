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

import org.openkilda.messaging.command.grpc.CreateOrUpdateLogicalPortRequest;
import org.openkilda.messaging.command.grpc.DeleteLogicalPortRequest;
import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.model.IpSocketAddress;
import org.openkilda.model.Switch;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.PersistenceManager;
import org.openkilda.persistence.repositories.SwitchRepository;
import org.openkilda.wfm.topology.switchmanager.error.SwitchNotFoundException;
import org.openkilda.wfm.topology.switchmanager.mappers.LogicalPortMapper;
import org.openkilda.wfm.topology.switchmanager.service.CommandBuilder;

import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Slf4j
public class CommandBuilderImpl implements CommandBuilder {
    private final SwitchRepository switchRepository;

    public CommandBuilderImpl(PersistenceManager persistenceManager) {
        this.switchRepository = persistenceManager.getRepositoryFactory().createSwitchRepository();
    }

    @Override
    public List<CreateOrUpdateLogicalPortRequest> buildLogicalPortInstallCommands(
            SwitchId switchId, List<LogicalPortInfoEntry> missingLogicalPorts) {
        String ipAddress = getSwitchIpAddress(switchId);

        List<CreateOrUpdateLogicalPortRequest> requests = new ArrayList<>();
        for (LogicalPortInfoEntry port : missingLogicalPorts) {
            requests.add(new CreateOrUpdateLogicalPortRequest(
                    ipAddress, Sets.newHashSet(port.getPhysicalPorts()), port.getLogicalPortNumber(),
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
}
