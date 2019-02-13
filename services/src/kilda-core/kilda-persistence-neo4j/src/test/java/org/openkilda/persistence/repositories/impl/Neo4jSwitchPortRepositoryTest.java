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

import org.openkilda.model.Port;
import org.openkilda.model.PortStatus;
import org.openkilda.model.Switch;
import org.openkilda.persistence.Neo4jBasedTest;
import org.openkilda.persistence.repositories.SwitchPortRepository;
import org.openkilda.persistence.repositories.SwitchRepository;

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.Collection;

public class Neo4jSwitchPortRepositoryTest extends Neo4jBasedTest {
    static SwitchPortRepository switchPortRepository;
    static SwitchRepository switchRepository;

    private Switch theSwitch;

    @BeforeClass
    public static void setUp() {
        switchPortRepository = new Neo4jSwitchPortRepository(neo4jSessionFactory, txManager);
        switchRepository = new Neo4jSwitchRepository(neo4jSessionFactory, txManager);
    }

    @Before
    public void createSwitches() {
        theSwitch = buildTestSwitch(1);
        switchRepository.createOrUpdate(theSwitch);
    }

    @Test
    public void shouldCreateSwitchPort() {
        Port port = Port.builder()
                .theSwitch(theSwitch)
                .portNo(1)
                .status(PortStatus.UP)
                .build();
        switchPortRepository.createOrUpdate(port);

        Collection<Port> allPorts = switchPortRepository.findAll();
        Port foundPort = allPorts.iterator().next();

        assertEquals(theSwitch.getSwitchId(), foundPort.getTheSwitch().getSwitchId());
        assertEquals(port.getPortNo(), foundPort.getPortNo());
    }

    @Test
    public void shouldDeleteFlowMeter() {
        Port port = Port.builder()
                .theSwitch(theSwitch)
                .portNo(1)
                .status(PortStatus.UP)
                .build();
        switchPortRepository.createOrUpdate(port);

        switchPortRepository.delete(port);

        assertEquals(0, switchPortRepository.findAll().size());
    }

    @Test
    public void shouldDeleteFoundFlowMeter() {
        Port port = Port.builder()
                .theSwitch(theSwitch)
                .portNo(1)
                .status(PortStatus.UP)
                .build();
        switchPortRepository.createOrUpdate(port);

        Collection<Port> allPorts = switchPortRepository.findAll();
        Port foundPort = allPorts.iterator().next();
        switchPortRepository.delete(foundPort);

        assertEquals(0, switchPortRepository.findAll().size());
    }
}
