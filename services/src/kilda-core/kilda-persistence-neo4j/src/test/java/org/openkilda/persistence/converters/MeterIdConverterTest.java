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

import org.openkilda.model.MeterId;

import org.junit.Test;

public class MeterIdConverterTest {
    @Test
    public void shouldConvertIdToString() {
        // given
        MeterId meterId = new MeterId(0x123);

        // when
        Long graphObject = new MeterIdConverter().toGraphProperty(meterId);

        // then
        assertEquals(meterId.getValue(), (long) graphObject);
    }

    @Test
    public void shouldConvertStringToId() {
        // given
        MeterId meterId = new MeterId(0x123);

        // when
        MeterId actualEntity = new MeterIdConverter().toEntityAttribute(meterId.getValue());

        // then
        assertEquals(meterId, actualEntity);
    }
}
