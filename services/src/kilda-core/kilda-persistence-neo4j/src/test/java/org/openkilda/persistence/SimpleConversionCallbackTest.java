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

package org.openkilda.persistence;

import static org.junit.Assert.assertEquals;

import org.openkilda.model.SwitchId;

import org.junit.Test;

import java.io.IOException;

public class SimpleConversionCallbackTest {
    @Test
    public void shouldConvertEntityToGraph() throws IOException {
        // given
        SimpleConversionCallback conversionCallback =
                new SimpleConversionCallback("org.openkilda.persistence.converters");
        SwitchId entity = new SwitchId(0);

        // when
        String graphObject = conversionCallback.convert(String.class, entity);

        // then
        String expectedObject = entity.toString();
        assertEquals(expectedObject, graphObject);
    }

    @Test
    public void shouldConvertGraphToEntity() throws IOException {
        // given
        SimpleConversionCallback conversionCallback =
                new SimpleConversionCallback("org.openkilda.persistence.converters");
        SwitchId entity = new SwitchId(0);
        String graphObject = entity.toString();

        // when
        SwitchId actualEntity = conversionCallback.convert(SwitchId.class, graphObject);

        // then
        assertEquals(entity, actualEntity);
    }
}

