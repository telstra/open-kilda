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

package org.openkilda.persistence.ferma.repositories;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.openkilda.model.history.HaFlowEvent;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.history.HaFlowEventRepository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

public class FermaHaFlowEventRepositoryTest extends InMemoryGraphBasedTest {
    public static final String FLOW_ADDED_BY_DEFAULT = "flow ID 12";
    private HaFlowEventRepository haFlowEventRepository;

    @BeforeEach
    public void setUp() {
        haFlowEventRepository = repositoryFactory.createHaFlowEventRepository();

        haFlowEventRepository.add(HaFlowEvent.builder()
                .haFlowId(FLOW_ADDED_BY_DEFAULT)
                .taskId("task Id")
                .details("some details")
                .action("Action")
                .timestamp(Instant.now().minus(10, ChronoUnit.MINUTES))
                .build());
    }

    /**
     * Parameters are: success?, timeFrom, timeTo, maxCount.
     * @return test data
     */
    public static Object[] getParametersForFindByHaFlowIdAndTimeFrameTest() {
        return new Object[][]{
                { true, null, null, 100 },
                { true, Instant.parse("1970-01-01T00:00:00Z"), null, 100 },
                { true, Instant.parse("1970-01-01T00:00:00Z"), Instant.now(), 100 },
                { true, null, Instant.now(), 100 },
                { true, Instant.now().minus(1, ChronoUnit.HOURS),
                        Instant.now().plus(1, ChronoUnit.HOURS), 100 },

                { false, Instant.parse("1970-01-01T00:00:00Z"),
                        Instant.parse("1970-01-01T00:00:00Z").plus(1, ChronoUnit.HOURS), 100 },
                { false, Instant.now().plus(1, ChronoUnit.DAYS),
                        Instant.now().plus(3, ChronoUnit.DAYS), 100 },
                { false, Instant.now(),
                        Instant.now().minus(3, ChronoUnit.DAYS), 100 },
                { false, Instant.now(), Instant.now(), 0 },
        };
    }

    @ParameterizedTest
    @MethodSource("getParametersForFindByHaFlowIdAndTimeFrameTest")
    public void whenHaFlowExists_findByHaFlowIdAndTimeFrame(
            boolean success, Instant timeFrom, Instant timeTo, int maxCount) {
        List<HaFlowEvent> events = haFlowEventRepository.findByHaFlowIdAndTimeFrame(FLOW_ADDED_BY_DEFAULT,
                timeFrom,
                timeTo,
                maxCount);
        assertEquals(success, events.size() > 0);

        if (success) {
            assertEquals(FLOW_ADDED_BY_DEFAULT, events.get(0).getHaFlowId());
        }
    }
}
