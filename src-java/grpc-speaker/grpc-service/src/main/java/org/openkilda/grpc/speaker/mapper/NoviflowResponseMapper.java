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

package org.openkilda.grpc.speaker.mapper;

import org.openkilda.grpc.speaker.model.PacketInOutStatsResponse;
import org.openkilda.messaging.model.grpc.LogicalPort;
import org.openkilda.messaging.model.grpc.LogicalPortType;
import org.openkilda.messaging.model.grpc.PacketInOutStatsDto;
import org.openkilda.messaging.model.grpc.RemoteLogServer;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus;

import io.grpc.noviflow.PacketInOutStats;
import io.grpc.noviflow.StatusSwitch;
import io.grpc.noviflow.YesNo;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring", imports = {LogicalPortType.class})
public abstract class NoviflowResponseMapper {

    @Mapping(source = "logicalportno", target = "logicalPortNumber")
    @Mapping(source = "portnoList", target = "portNumbers")
    @Mapping(source = "logicalporttype", target = "type")
    @Mapping(ignore = true, target = "portNumber")
    public abstract LogicalPort map(io.grpc.noviflow.LogicalPort port);

    @Mapping(source = "ethLinksList", target = "ethLinks")
    @Mapping(source = "buildsList", target = "builds")
    public abstract SwitchInfoStatus map(StatusSwitch statusSwitch);

    @Mapping(source = "ipaddr", target = "ipAddress")
    public abstract RemoteLogServer map(io.grpc.noviflow.RemoteLogServer remoteLogServer);

    public abstract PacketInOutStatsDto map(PacketInOutStatsResponse stats);

    public abstract PacketInOutStatsResponse map(PacketInOutStats stats);

    /**
     * Convert known values of {@link io.grpc.noviflow.LogicalPortType} into {@link LogicalPortType}.
     */
    public LogicalPortType map(io.grpc.noviflow.LogicalPortType type) {
        if (type == null) {
            return null;
        }

        LogicalPortType result;
        switch (type) {
            case LOGICAL_PORT_TYPE_RESERVED:
                result = LogicalPortType.RESERVED;
                break;
            case LOGICAL_PORT_TYPE_LAG:
                result = LogicalPortType.LAG;
                break;
            case LOGICAL_PORT_TYPE_BFD:
                result = LogicalPortType.BFD;
                break;

            default:
                throw new IllegalArgumentException(
                        makeInvalidEnumValueMappingMessage(type, LogicalPortType.class));
        }
        return result;
    }

    /**
     * Convert values of {@link LogicalPortType} into {@link io.grpc.noviflow.LogicalPortType}.
     */
    public io.grpc.noviflow.LogicalPortType map(LogicalPortType type) {
        if (type == null) {
            return null;
        }

        io.grpc.noviflow.LogicalPortType result;
        switch (type) {
            case LAG:
                result = io.grpc.noviflow.LogicalPortType.LOGICAL_PORT_TYPE_LAG;
                break;
            case BFD:
                result = io.grpc.noviflow.LogicalPortType.LOGICAL_PORT_TYPE_BFD;
                break;
            case RESERVED:
                result = io.grpc.noviflow.LogicalPortType.LOGICAL_PORT_TYPE_RESERVED;
                break;
            default:
                throw new IllegalArgumentException(
                        makeInvalidEnumValueMappingMessage(type, io.grpc.noviflow.LogicalPortType.class));
        }
        return result;
    }

    /**
     * Maps YesNo enum to Boolean value.
     */
    public Boolean toBoolean(YesNo yesNo) {
        if (yesNo == null) {
            return null;
        }
        switch (yesNo) {
            case YES:
                return true;
            case NO:
                return false;
            default:
                return null;
        }
    }

    private String makeInvalidEnumValueMappingMessage(Object value, Class<?> targetClass) {
        return String.format("There is no mapping of %s.%s into %s",
                value.getClass().getName(), value, targetClass.getName());
    }
}
