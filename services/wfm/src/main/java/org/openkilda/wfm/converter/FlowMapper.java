/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.converter;

import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPair;
import org.openkilda.model.Node;
import org.openkilda.model.Path;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(uses = SwitchIdMapper.class)
public interface FlowMapper {

    Node pathNodeFromDto(PathNode p);

    PathNode pathToDto(Node p);

    @Mapping(source = "nodes", target = "path")
    PathInfoData pathToDto(Path p);

    @Mapping(source = "path", target = "nodes")
    Path pathInfoDataFromDto(PathInfoData p);

    @Mapping(source = "srcPort", target = "sourcePort")
    @Mapping(source = "srcVlan", target = "sourceVlan")
    @Mapping(source = "destPort", target = "destinationPort")
    @Mapping(source = "destVlan", target = "destinationVlan")
    @Mapping(source = "srcSwitchId", target = "sourceSwitch")
    @Mapping(source = "destSwitchId", target = "destinationSwitch")
    org.openkilda.messaging.model.Flow flowToDto(Flow flow);

    @Mapping(source = "sourcePort", target = "srcPort")
    @Mapping(source = "sourceVlan", target = "srcVlan")
    @Mapping(source = "destinationPort", target = "destPort")
    @Mapping(source = "destinationVlan", target = "destVlan")
    @Mapping(source = "sourceSwitch", target = "srcSwitchId")
    @Mapping(source = "destinationSwitch", target = "destSwitchId")
    Flow flowFromDto(org.openkilda.messaging.model.Flow flow);


    default org.openkilda.messaging.model.FlowPair flowPairToDto(FlowPair flowPair) {
        return new org.openkilda.messaging.model.FlowPair(flowToDto(flowPair.getForward()),
                flowToDto(flowPair.getReverse()));
    }

    default FlowPair flowPairFromDto(org.openkilda.messaging.model.FlowPair<org.openkilda.messaging.model.Flow,
            org.openkilda.messaging.model.Flow> flowPair) {
        return new FlowPair(flowFromDto(flowPair.getLeft()), flowFromDto(flowPair.getRight()));
    }
}
