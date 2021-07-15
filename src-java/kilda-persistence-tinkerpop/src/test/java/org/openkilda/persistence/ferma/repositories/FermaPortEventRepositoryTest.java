/* Copyright 2020 Telstra Open Source
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

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.openkilda.model.SwitchId;
import org.openkilda.model.history.PortEvent;
import org.openkilda.persistence.inmemory.InMemoryGraphBasedTest;
import org.openkilda.persistence.repositories.history.PortEventRepository;

import org.junit.Before;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.Date;

public class FermaPortEventRepositoryTest extends InMemoryGraphBasedTest {
    static final SwitchId SWITCH_ID = new SwitchId(1L);
    static final int PORT_NUMBER = 2;

    PortEventRepository repository;

    @Before
    public void setUp() {
        repository = repositoryFactory.createPortEventRepository();
    }

    @Test
    public void shouldFindHistoryRecordsBySwitchByIdAndPortNumber() {
        Instant start = new Date().toInstant();
        Instant end = new Date().toInstant().plus(1, ChronoUnit.DAYS);
        PortEvent portUp = createPortHistory(SWITCH_ID, PORT_NUMBER, "PORT_UP", start.plus(1, ChronoUnit.HOURS));
        PortEvent portDown = createPortHistory(SWITCH_ID, PORT_NUMBER, "PORT_DOWN", end);
        createPortHistory(new SwitchId(2L), PORT_NUMBER, "TEST1", end);
        createPortHistory(SWITCH_ID, 3, "TEST2", end);
        createPortHistory(SWITCH_ID, PORT_NUMBER, "TEST3", start.minus(1, ChronoUnit.SECONDS));
        createPortHistory(SWITCH_ID, PORT_NUMBER, "TEST4", end.plus(1, ChronoUnit.HOURS));

        Collection<PortEvent> portEvent =
                repository.findBySwitchIdAndPortNumber(SWITCH_ID, PORT_NUMBER, start, end);
        assertEquals(2, portEvent.size());
        assertTrue(portEvent.contains(portUp));
        assertTrue(portEvent.contains(portDown));
    }

    private PortEvent createPortHistory(SwitchId switchId, int portNumber, String event, Instant time) {
        PortEvent portEvent = new PortEvent();
        portEvent.setSwitchId(switchId);
        portEvent.setPortNumber(portNumber);
        portEvent.setEvent(event);
        portEvent.setTime(time);
        repository.add(portEvent);
        return portEvent;
    }
}
