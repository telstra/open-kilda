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

package org.openkilda.persistence.converters;

import static java.util.Collections.singletonList;
import static org.junit.Assert.assertEquals;

import org.openkilda.model.Node;
import org.openkilda.model.Path;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

public class FlowPathConverterTest {
    @Test
    public void shouldConvertPathToString() throws IOException {
        // given
        Node node = new Node(new SwitchId(1), 2, 3, null, (long) 0x123);
        Path entity = new Path(0, singletonList(node), null);

        // when
        String graphObject = new FlowPathConverter().toGraphProperty(entity);

        // then
        String expectedObject = new ObjectMapper().writeValueAsString(entity);
        assertEquals(expectedObject, graphObject);
    }

    @Test
    public void shouldConvertStringToPath() throws IOException {
        // given
        Node node = new Node(new SwitchId(1), 2, 3, null, (long) 0x123);
        Path entity = new Path(0, singletonList(node), null);
        String graphObject = new ObjectMapper().writeValueAsString(entity);

        // when
        Path actualEntity = new FlowPathConverter().toEntityAttribute(graphObject);

        // then
        assertEquals(entity, actualEntity);
    }
}
