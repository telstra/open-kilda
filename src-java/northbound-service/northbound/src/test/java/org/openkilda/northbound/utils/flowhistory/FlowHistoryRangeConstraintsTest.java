/* Copyright 2023 Telstra Open Source
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

package org.openkilda.northbound.utils.flowhistory;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.openkilda.messaging.error.MessageException;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;

class FlowHistoryRangeConstraintsTest {
    @Test
    void validateTimeParametersTest() {
        assertThrows(MessageException.class, () ->
                new FlowHistoryRangeConstraints(Optional.of(300L), Optional.of(20L), Optional.empty()));

        assertDoesNotThrow(() ->
                new FlowHistoryRangeConstraints(Optional.of(20L), Optional.of(300L), Optional.empty()));

        assertDoesNotThrow(() ->
                new FlowHistoryRangeConstraints(Optional.empty(), Optional.of(300L), Optional.empty()));

        assertDoesNotThrow(() ->
                new FlowHistoryRangeConstraints(Optional.of(20L), Optional.empty(), Optional.empty()));
    }

    @Test
    void whenNoTimeParameterProvided_defaultIsSet() {
        FlowHistoryRangeConstraints f1 = new FlowHistoryRangeConstraints(
                Optional.empty(), Optional.of(300L), Optional.empty());
        assertEquals(Instant.ofEpochSecond(0L), f1.getTimeFrom());

        FlowHistoryRangeConstraints f2 = new FlowHistoryRangeConstraints(
                Optional.of(300L), Optional.empty(), Optional.empty());
        assertTrue(f2.getTimeTo().isBefore(Instant.now().plusNanos(1)));

        FlowHistoryRangeConstraints f3 = new FlowHistoryRangeConstraints(
                Optional.empty(), Optional.empty(), Optional.empty());
        assertEquals(Instant.ofEpochSecond(0L), f3.getTimeFrom());
        assertTrue(f3.getTimeTo().isBefore(Instant.now().plusNanos(1)));
    }

    @Test
    void maxCountTest() {
        assertThrows(MessageException.class, () ->
                new FlowHistoryRangeConstraints(Optional.empty(), Optional.empty(), Optional.of(-5)));

        assertDoesNotThrow(() ->
                new FlowHistoryRangeConstraints(Optional.empty(), Optional.empty(), Optional.empty()));

        assertDoesNotThrow(() ->
                new FlowHistoryRangeConstraints(Optional.empty(), Optional.empty(), Optional.of(12)));
    }

    @Test
    void contentRangeRequiredTest() {
        FlowHistoryRangeConstraints f1 = new FlowHistoryRangeConstraints(
                Optional.empty(), Optional.of(300L), Optional.empty());
        assertFalse(f1.isContentRangeRequired(142));
        FlowHistoryRangeConstraints f2 = new FlowHistoryRangeConstraints(
                Optional.of(300L), Optional.empty(),  Optional.empty());
        assertFalse(f2.isContentRangeRequired(142));
        FlowHistoryRangeConstraints f3 = new FlowHistoryRangeConstraints(
                Optional.empty(), Optional.empty(), Optional.of(1));
        assertFalse(f3.isContentRangeRequired(142));
        FlowHistoryRangeConstraints f4 = new FlowHistoryRangeConstraints(
                Optional.empty(), Optional.empty(), Optional.empty());
        assertTrue(f4.isContentRangeRequired(142));
    }
}
