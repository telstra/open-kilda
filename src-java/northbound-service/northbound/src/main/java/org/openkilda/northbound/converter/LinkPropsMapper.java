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

package org.openkilda.northbound.converter;

import static java.util.Objects.requireNonNull;

import org.openkilda.messaging.model.LinkPropsDto;
import org.openkilda.messaging.model.LinkPropsMask;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.model.NetworkEndpointMask;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;
import org.openkilda.model.LinkProps;
import org.openkilda.model.SwitchId;
import org.openkilda.northbound.dto.v1.links.LinkMaxBandwidthDto;

import org.mapstruct.Mapper;

@Mapper(componentModel = "spring")
public interface LinkPropsMapper {

    /**
     * Converts link properties to {@link org.openkilda.northbound.dto.v1.links.LinkPropsDto}.
     */
    default org.openkilda.northbound.dto.v1.links.LinkPropsDto toDto(LinkPropsData data) {
        requireNonNull(data.getLinkProps(), "Link props should be presented");

        NetworkEndpoint source = data.getLinkProps().getSource();
        NetworkEndpoint destination = data.getLinkProps().getDest();
        return new org.openkilda.northbound.dto.v1.links.LinkPropsDto(
                source.getDatapath().toString(), source.getPortNumber(),
                destination.getDatapath().toString(), destination.getPortNumber(),
                data.getLinkProps().getProps());
    }

    /**
     * Converts {@link org.openkilda.northbound.dto.v1.links.LinkPropsDto} into {@link LinkPropsDto}.
     */
    default LinkPropsDto toLinkProps(org.openkilda.northbound.dto.v1.links.LinkPropsDto input) {
        NetworkEndpoint source = new NetworkEndpoint(new SwitchId(input.getSrcSwitch()), input.getSrcPort());
        NetworkEndpoint dest = new NetworkEndpoint(new SwitchId(input.getDstSwitch()), input.getDstPort());
        return new LinkPropsDto(source, dest, input.getProps());
    }

    /**
     * Converts {@link org.openkilda.northbound.dto.v1.links.LinkPropsDto} into {@link LinkPropsMask}.
     */
    default LinkPropsMask toLinkPropsMask(org.openkilda.northbound.dto.v1.links.LinkPropsDto input) {
        NetworkEndpointMask source = new NetworkEndpointMask(new SwitchId(input.getSrcSwitch()), input.getSrcPort());
        NetworkEndpointMask dest = new NetworkEndpointMask(new SwitchId(input.getDstSwitch()), input.getDstPort());
        return new LinkPropsMask(source, dest);
    }

    /**
     * Converts {@link LinkPropsDto} to {@link LinkMaxBandwidthDto}.
     */
    default LinkMaxBandwidthDto toLinkMaxBandwidth(LinkPropsDto input) {
        NetworkEndpoint source = input.getSource();
        NetworkEndpoint dest = input.getDest();
        Long maxBandwidth = Long.valueOf(input.getProps().get(LinkProps.MAX_BANDWIDTH_PROP_NAME));
        return new LinkMaxBandwidthDto(
                source.getDatapath().toString(), source.getPortNumber(),
                dest.getDatapath().toString(), dest.getPortNumber(), maxBandwidth);
    }
}
