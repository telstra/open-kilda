/* Copyright 2022 Telstra Open Source
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

import org.openkilda.messaging.model.ExcludeFilter;
import org.openkilda.messaging.model.IncludeFilter;

import org.mapstruct.Mapper;

import java.util.List;
import java.util.stream.Collectors;

@Mapper(componentModel = "spring")
public abstract class ValidationFilterMapper {

    private IncludeFilter stringToIncludeFilter(String value) {
        switch (value) {
            case("meters"):
                return IncludeFilter.METERS;
            case("groups"):
                return IncludeFilter.GROUPS;
            case("logical_ports"):
                return IncludeFilter.LOGICAL_PORTS;
            case("rules"):
                return IncludeFilter.RULES;
            default:
                throw new IllegalArgumentException("Unexpected include filter caught");
        }
    }

    private ExcludeFilter stringToExcludeFilter(String value) {
        switch (value) {
            case("flow_info"):
                return ExcludeFilter.FLOW_INFO;
            default:
                throw new IllegalArgumentException("Unexpected exclude filter caught");
        }
    }

    /**
     * Convert list of {@link String} into list of {@link IncludeFilter}.
     */
    public List<IncludeFilter> toIncludeFilters(List<String> value) {
        return value.stream().map(this::stringToIncludeFilter).collect(Collectors.toList());
    }

    public List<ExcludeFilter> toExcludeFilters(List<String> value) {
        return value.stream().map(this::stringToExcludeFilter).collect(Collectors.toList());
    }

}
