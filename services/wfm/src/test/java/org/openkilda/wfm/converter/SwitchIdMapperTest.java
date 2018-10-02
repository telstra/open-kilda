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

import static org.junit.Assert.assertEquals;

import org.openkilda.model.SwitchId;

import org.junit.Test;
import org.mapstruct.factory.Mappers;

public class SwitchIdMapperTest {


    private static final SwitchIdMapper Switch_ID_MAPPER = Mappers.getMapper(SwitchIdMapper.class);

    @Test
    public void testSwitchIdToDto() {
        SwitchId switchId = new SwitchId(1L);
        org.openkilda.messaging.model.SwitchId dtoSwitchId = Switch_ID_MAPPER.toDto(switchId);
        assertEquals(switchId.getId(), dtoSwitchId.toLong());
    }

    @Test
    public void testSwitchIdFromDto() {
        org.openkilda.messaging.model.SwitchId switchId = new org.openkilda.messaging.model.SwitchId(1L);
        SwitchId dtoSwitchId = Switch_ID_MAPPER.fromDto(switchId);
        assertEquals(switchId.toLong(), dtoSwitchId.getId());
    }

}
