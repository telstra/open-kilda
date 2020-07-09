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

package org.openkilda.persistence.repositories.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortHistory;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.history.PortHistoryRepository;

import org.junit.BeforeClass;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public class Neo4jPortHistoryRepositoryTest extends Neo4jBasedTest {

    private static final SwitchId SWITCH_ID = new SwitchId(1L);
    private static final int PORT_NUMBER = 2;
    private static final String EVENT_1 = "EVENT_1";
    private static final String EVENT_2 = "EVENT_2";
    private static final String EVENT_3 = "EVENT_3";
    private static final Instant TIME_1 = Instant.parse("2020-07-06T11:04:41Z");
    private static final Instant TIME_2 = Instant.parse("2020-07-06T11:04:41.482Z");
    private static final Instant TIME_3 = Instant.parse("2020-07-06T11:04:42.321Z");

    private static PortHistoryRepository repository;

    @BeforeClass
    public static void setUp() {
        repository = new Neo4jPortHistoryRepository(neo4jSessionFactory, txManager);
    }

    @Test
    public void shouldFindHistoryRecordsBySwitchByIdAndPortNumber() {
        Instant start = new Date().toInstant();
        Instant end = new Date().toInstant().plus(1, ChronoUnit.DAYS);
        PortHistory portUp = getPortHistory(SWITCH_ID, PORT_NUMBER, "PORT_UP", start.plus(1, ChronoUnit.HOURS));
        repository.createOrUpdate(portUp);
        PortHistory portDown = getPortHistory(SWITCH_ID, PORT_NUMBER, "PORT_DOWN", end);
        repository.createOrUpdate(portDown);
        repository.createOrUpdate(getPortHistory(new SwitchId(2L), PORT_NUMBER, "TEST", end));
        repository.createOrUpdate(getPortHistory(SWITCH_ID, 3, "TEST", end));
        repository.createOrUpdate(getPortHistory(SWITCH_ID, PORT_NUMBER, "TEST", start));
        repository.createOrUpdate(getPortHistory(SWITCH_ID, PORT_NUMBER, "TEST", end.plus(1, ChronoUnit.HOURS)));

        Collection<PortHistory> portHistory =
                repository.findBySwitchIdAndPortNumber(SWITCH_ID, PORT_NUMBER, start, end);
        assertEquals(2, portHistory.size());
        assertTrue(portHistory.contains(portUp));
        assertTrue(portHistory.contains(portDown));
    }

    @Test
    public void findHistoryRecordsBySwitchByIdAndPortNumberOrderTest() {
        List<PortHistory> expected = new ArrayList<>();
        expected.add(getPortHistory(SWITCH_ID, PORT_NUMBER, EVENT_1, TIME_1));
        expected.add(getPortHistory(SWITCH_ID, PORT_NUMBER, EVENT_2, TIME_2));
        expected.add(getPortHistory(SWITCH_ID, PORT_NUMBER, EVENT_3, TIME_3));

        for (PortHistory portHistory : expected) {
            repository.createOrUpdate(portHistory);
        }

        List<PortHistory> actual = new ArrayList<>(repository.findBySwitchIdAndPortNumber(
                SWITCH_ID, PORT_NUMBER, TIME_1.minusSeconds(1), TIME_3.plusSeconds(1)));
        assertEquals(expected, actual);
        // result must be sorted by time
        assertTrue(actual.get(0).getTime().isBefore(actual.get(1).getTime()));
        assertTrue(actual.get(1).getTime().isBefore(actual.get(2).getTime()));
    }

    private PortHistory getPortHistory(SwitchId switchId, int portNumber, String event, Instant time) {
        return PortHistory.builder()
                .switchId(switchId)
                .portNumber(portNumber)
                .event(event)
                .time(time)
                .build();
    }
}
