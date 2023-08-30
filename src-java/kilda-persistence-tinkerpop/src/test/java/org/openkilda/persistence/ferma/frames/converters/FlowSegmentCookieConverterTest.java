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

package org.openkilda.persistence.ferma.frames.converters;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.model.cookie.FlowSegmentCookie;

import org.junit.jupiter.api.Test;

public class FlowSegmentCookieConverterTest {
    @Test
    public void shouldConvertIdToLong() {
        // given
        FlowSegmentCookie cookie = new FlowSegmentCookie((long) 0x123);

        // when
        Long graphObject = FlowSegmentCookieConverter.INSTANCE.toGraphProperty(cookie);

        // then
        assertEquals(cookie.getValue(), (long) graphObject);
    }

    @Test
    public void shouldConvertLongToId() {
        // given
        FlowSegmentCookie cookie = new FlowSegmentCookie((long) 0x123);

        // when
        FlowSegmentCookie actualEntity = FlowSegmentCookieConverter.INSTANCE.toEntityAttribute(cookie.getValue());

        // then
        assertEquals(cookie, actualEntity);
    }
}
