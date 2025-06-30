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

package org.openkilda.testing.tools;

import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE;

import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.northbound.dto.v1.links.LinkPropsDto;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;

import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;

@Component
@Scope(SCOPE_PROTOTYPE)
public class IslUtils {

    /**
     * Finds certain ISL in list of 'IslInfoData' objects. Passed ISL is our internal ISL representation, while
     * IslInfoData is returned from NB.
     *
     * @param islsInfo list where to search certain ISL
     * @param isl what ISL to look for
     */
    public Optional<IslInfoData> getIslInfo(List<IslInfoData> islsInfo, Isl isl) {
        return islsInfo.stream().filter(link -> {
            PathNode src = link.getSource();
            PathNode dst = link.getDestination();
            return src.getPortNo() == isl.getSrcPort() && dst.getPortNo() == isl.getDstPort()
                    && src.getSwitchId().equals(isl.getSrcSwitch().getDpId())
                    && dst.getSwitchId().equals(isl.getDstSwitch().getDpId());
        }).findFirst();
    }

    /**
     * Converts a given Isl object to LinkPropsDto object.
     *
     * @param isl Isl object to convert
     * @param props Isl props to set when creating LinkPropsDto
     */
    public LinkPropsDto toLinkProps(Isl isl, HashMap props) {
        return new LinkPropsDto(isl.getSrcSwitch().getDpId().toString(), isl.getSrcPort(),
                isl.getDstSwitch().getDpId().toString(), isl.getDstPort(), props);
    }
}
