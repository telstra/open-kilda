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

package org.openkilda.converters;

import org.openkilda.model.Cookie;
import org.openkilda.model.LldpResources;
import org.openkilda.model.MeterId;

import org.neo4j.ogm.typeconversion.CompositeAttributeConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class LldpResourcesConverter implements CompositeAttributeConverter<LldpResources> {

    public static final String LLDP_METER_ID = "lldp_meter_id";
    public static final String LLDP_COOKIE = "lldp_cookie";

    @Override
    public Map<String, ?> toGraphProperties(LldpResources resources) {
        Map<String, Long> properties = new HashMap<>();
        if (resources != null)  {
            if (resources.getMeterId() != null) {
                properties.put(LLDP_METER_ID, resources.getMeterId().getValue());
            }
            if (resources.getCookie() != null) {
                properties.put(LLDP_COOKIE, resources.getCookie().getValue());
            }
        }
        return properties;
    }

    @Override
    public LldpResources toEntityAttribute(Map<String, ?> properties) {
        MeterId meterId = Optional.ofNullable(
                properties.get(LLDP_METER_ID)).map(Long.class::cast).map(MeterId::new).orElse(null);
        Cookie cookie = Optional.ofNullable(
                properties.get(LLDP_COOKIE)).map(Long.class::cast).map(Cookie::new).orElse(null);
        if (meterId != null || cookie != null) {
            return new LldpResources(meterId, cookie);
        }
        return null;
    }
}
