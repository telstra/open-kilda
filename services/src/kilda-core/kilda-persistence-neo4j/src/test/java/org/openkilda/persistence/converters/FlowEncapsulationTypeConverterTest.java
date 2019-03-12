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

package org.openkilda.persistence.converters;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.FlowEncapsulationType;

import org.junit.Test;

public class FlowEncapsulationTypeConverterTest {
    @Test
    public void shouldConvertToGraphProperty() {
        FlowEncapsulationTypeConverter converter = new FlowEncapsulationTypeConverter();

        assertEquals("transit_vlan", converter.toGraphProperty(FlowEncapsulationType.TRANSIT_VLAN));
    }

    @Test
    public void shouldConvertToEntity() {
        FlowEncapsulationTypeConverter converter = new FlowEncapsulationTypeConverter();

        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, converter.toEntityAttribute("transit_vlan"));
        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, converter.toEntityAttribute("Transit_Vlan"));
        assertEquals(FlowEncapsulationType.TRANSIT_VLAN, converter.toEntityAttribute("TRANSIT_VLAN"));
    }
}
