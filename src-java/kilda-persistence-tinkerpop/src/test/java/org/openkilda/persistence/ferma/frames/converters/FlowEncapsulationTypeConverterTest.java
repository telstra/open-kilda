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

package org.openkilda.persistence.ferma.frames.converters;

import org.openkilda.model.FlowEncapsulationType;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class FlowEncapsulationTypeConverterTest {
    @Test
    public void shouldConvertToGraphProperty() {
        Assertions.assertEquals("TRANSIT_VLAN",
                FlowEncapsulationTypeConverter.INSTANCE.toGraphProperty(FlowEncapsulationType.TRANSIT_VLAN));
    }

    @Test
    public void shouldConvertToEntity() {
        Assertions.assertEquals(FlowEncapsulationType.TRANSIT_VLAN,
                FlowEncapsulationTypeConverter.INSTANCE.toEntityAttribute("TRANSIT_VLAN"));
    }
}
