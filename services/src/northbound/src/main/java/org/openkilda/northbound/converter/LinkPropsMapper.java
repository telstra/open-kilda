/* Copyright 2017 Telstra Open Source
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

package org.openkilda.northbound.converter;

import static java.util.Objects.requireNonNull;

import org.openkilda.messaging.model.LinkProps;
import org.openkilda.messaging.model.LinkPropsMask;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.northbound.dto.LinkPropsDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LinkPropsMapper {

    /**
     * Converts link properties to {@link LinkPropsDto}.
     */
    default LinkPropsDto toDto(LinkPropsData data) {
        requireNonNull(data.getLinkProps(), "Link props should be presented");

        NetworkEndpoint source = data.getLinkProps().getSource();
        NetworkEndpoint destination = data.getLinkProps().getDest();
        return new LinkPropsDto(
                source.getDatapath(), source.getPortNumber(),
                destination.getDatapath(), destination.getPortNumber(),
                data.getLinkProps().getProps());
    }

    /**
     * Converts {@link LinkPropsDto} into {@link LinkProps}.
     */
    default LinkProps toLinkProps(LinkPropsDto input) {
        NetworkEndpoint source = new NetworkEndpoint(input.getSrcSwitch(), input.getSrcPort());
        NetworkEndpoint dest = new NetworkEndpoint(input.getDstSwitch(), input.getDstPort());
        return new LinkProps(source, dest, input.getProps());
    }

    /**
     * Converts {@link LinkPropsDto} into {@link LinkPropsMask}.
     */
    default LinkPropsMask toLinkPropsMask(LinkPropsDto input) {
        NetworkEndpointMask source = new NetworkEndpointMask(input.getSrcSwitch(), input.getSrcPort());
        NetworkEndpointMask dest = new NetworkEndpointMask(input.getDstSwitch(), input.getDstPort());
        return new LinkPropsMask(source, dest);
    }
}
