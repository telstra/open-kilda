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

import org.openkilda.messaging.model.grpc.SwitchInfoStatus;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus.SwitchBuildInfoStatus;
import org.openkilda.messaging.model.grpc.SwitchInfoStatus.SwitchEthLinkInfoStatus;

import io.grpc.noviflow.LogicalPort;
import io.grpc.noviflow.RemoteLogServer;
import io.grpc.noviflow.StatusSwitch;
import io.grpc.noviflow.StatusSwitchBuild;
import io.grpc.noviflow.StatusSwitchEthLink;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface NoviflowResponseMapper {

    @Mapping(source = "logicalportno", target = "logicalPortNumber")
    @Mapping(source = "portnoList", target = "portNumbers")
    org.openkilda.messaging.model.grpc.LogicalPort toLogicalPort(LogicalPort port);

    @Mapping(source = "ethLinksList", target = "ethLinks")
    @Mapping(source = "buildsList", target = "builds")
    SwitchInfoStatus toSwitchInfo(StatusSwitch statusSwitch);

    @Mapping(source = "ipaddr", target = "ipAddress")
    org.openkilda.messaging.model.grpc.RemoteLogServer toRemoteLogServer(RemoteLogServer remoteLogServer);

    SwitchEthLinkInfoStatus toSwithcEthLink(StatusSwitchEthLink statusSwitchEthLink);

    SwitchBuildInfoStatus toSwitchBuildInfo(StatusSwitchBuild statusSwitchBuild);
}
