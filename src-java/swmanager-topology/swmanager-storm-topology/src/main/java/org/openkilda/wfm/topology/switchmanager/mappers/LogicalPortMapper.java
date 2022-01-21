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

package org.openkilda.wfm.topology.switchmanager.mappers;

import org.openkilda.messaging.info.switches.LogicalPortInfoEntry;
import org.openkilda.messaging.info.switches.LogicalPortType;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.model.LagLogicalPort;
import org.openkilda.model.PhysicalPort;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.factory.Mappers;

import java.util.Optional;

@Mapper
public abstract class LogicalPortMapper {
    public static final LogicalPortMapper INSTANCE = Mappers.getMapper(LogicalPortMapper.class);

    @Mapping(target = "physicalPorts", source = "portNumbers")
    @Mapping(target = "expected", ignore = true)
    @Mapping(target = "actual", ignore = true)
    public abstract LogicalPortInfoEntry map(LogicalPort logicalPort);

    @Mapping(target = "type", constant = "LAG")
    @Mapping(target = "expected", ignore = true)
    @Mapping(target = "actual", ignore = true)
    public abstract LogicalPortInfoEntry map(LagLogicalPort logicalPort);

    public Integer map(PhysicalPort physicalPort) {
        return Optional.ofNullable(physicalPort).map(PhysicalPort::getPortNumber).orElse(null);
    }

    public LogicalPortType map(org.openkilda.messaging.model.grpc.LogicalPortType type) {
        return Optional.ofNullable(type).map(portType -> LogicalPortType.of(
                type.name())).orElse(null);
    }

    /**
     * Maps messaging LogicalPortType to GRPC LogicalPortType.
     */
    public org.openkilda.messaging.model.grpc.LogicalPortType map(LogicalPortType type) {
        return Optional.ofNullable(type)
                .map(portType -> org.openkilda.messaging.model.grpc.LogicalPortType.valueOf(type.name()))
                .orElse(null);
    }
}
