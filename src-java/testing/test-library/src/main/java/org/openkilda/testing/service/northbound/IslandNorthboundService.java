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

package org.openkilda.testing.service.northbound;

import static java.util.stream.Collectors.toList;
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.links.LinkPropsDto;
import org.openkilda.northbound.dto.v1.switches.SwitchDto;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.Switch;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Service;

import java.util.List;

@Service("islandNb")
@Slf4j
@Scope(SCOPE_PROTOTYPE)
public class IslandNorthboundService extends NorthboundServiceImpl {
    @Autowired
    TopologyDefinition topology;
    List<SwitchId> switchIds;

    @Autowired
    IslandNorthboundService(TopologyDefinition topology) {
        switchIds = topology.getSwitches().stream().map(Switch::getDpId).collect(toList());
    }

    @Override
    public List<IslInfoData> getLinks(SwitchId srcSwitch, Integer srcPort, SwitchId dstSwitch, Integer dstPort) {
        List<IslInfoData> result = super.getLinks(srcSwitch, srcPort, dstSwitch, dstPort);
        return result.stream().filter(isl -> switchIds.contains(isl.getSource().getSwitchId())).collect(toList());
    }

    @Override
    public List<SwitchDto> getAllSwitches() {
        return super.getAllSwitches().stream().filter(sw -> switchIds.contains(sw.getSwitchId())).collect(toList());
    }

    @Override
    public List<LinkPropsDto> getLinkProps(SwitchId srcSwitch, Integer srcPort, SwitchId dstSwitch, Integer dstPort) {
        return super.getLinkProps(srcSwitch, srcPort, dstSwitch, dstPort).stream().filter(prop ->
                switchIds.contains(new SwitchId(prop.getSrcSwitch()))).collect(toList());
    }
}
