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

package org.openkilda.northbound.utils;

import static org.hamcrest.core.IsEqual.equalTo;
import static org.hamcrest.core.IsNot.not;
import static org.junit.Assert.assertThat;
import static org.openkilda.messaging.Utils.DEFAULT_CORRELATION_ID;

import org.junit.Test;
import org.openkilda.northbound.utils.RequestCorrelationId.RequestCorrelationClosable;

import java.util.UUID;

public class RequestCorrelationIdTest {

    @Test
    public void shouldResetCorrelationIdOnClose() {
        // given
        RequestCorrelationId.create(String.format("test-%s", UUID.randomUUID()));
        String before = RequestCorrelationId.getId();

        // when
        try (RequestCorrelationClosable closable = RequestCorrelationId
                .create(String.format("test-%s", UUID.randomUUID()))) {

            //then
            assertThat(RequestCorrelationId.getId(), not(equalTo(before)));
        }

        assertThat(RequestCorrelationId.getId(), equalTo(DEFAULT_CORRELATION_ID));
    }
}