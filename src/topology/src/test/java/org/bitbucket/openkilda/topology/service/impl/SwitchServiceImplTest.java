package org.bitbucket.openkilda.topology.service.impl;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

import org.bitbucket.openkilda.messaging.error.MessageException;
import org.bitbucket.openkilda.messaging.info.event.SwitchEventType;
import org.bitbucket.openkilda.messaging.info.event.SwitchInfoData;
import org.bitbucket.openkilda.topology.TestConfig;
import org.bitbucket.openkilda.topology.domain.Switch;
import org.bitbucket.openkilda.topology.domain.SwitchStateType;
import org.bitbucket.openkilda.topology.service.SwitchService;

import com.google.common.collect.Iterables;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ActiveProfiles("test")
@ContextConfiguration(classes = TestConfig.class)
public class SwitchServiceImplTest {
    private static final String switchId = "00:00:00:00:00:01";
    private static final String address = "127.0.0.1";
    private static final String name = "localhost";
    private static final String description = "Unknown";

    @Autowired
    SwitchService switchService;

    @Test
    @Transactional
    public void add() throws Exception {
        SwitchInfoData data = new SwitchInfoData(switchId, SwitchEventType.ADDED, address, name, description);

        Switch sw = switchService.add(data);

        assertEquals(sw.getName(), switchId);
        assertEquals(sw.getState(), SwitchStateType.INACTIVE.toString().toLowerCase());
    }

    @Test
    @Transactional
    public void remove() throws Exception {
        SwitchInfoData data = new SwitchInfoData(switchId, SwitchEventType.REMOVED, address, name, description);

        switchService.add(data);
        assertNotNull(switchService.get(switchId));

        switchService.remove(data);
        assertNull(switchService.get(switchId));
    }

    @Test
    @Transactional
    public void activate() throws Exception {
        SwitchInfoData data = new SwitchInfoData(switchId, SwitchEventType.ACTIVATED, address, name, description);

        switchService.add(data);
        assertNotNull(switchService.get(switchId));

        switchService.activate(data);

        Switch sw = switchService.get(switchId);
        assertEquals(SwitchStateType.ACTIVE.toString().toLowerCase(), sw.getState());

        Iterable<Switch> switches = switchService.dump();
        assertEquals(1, Iterables.size(switches));
    }

    @Test
    @Transactional
    public void deactivate() throws Exception {
        SwitchInfoData data = new SwitchInfoData(switchId, SwitchEventType.DEACTIVATED, address, name, description);

        switchService.add(data);
        assertNotNull(switchService.get(switchId));

        switchService.deactivate(data);

        Switch sw = switchService.get(switchId);
        assertEquals(SwitchStateType.INACTIVE.toString().toLowerCase(), sw.getState());

        Iterable<Switch> switches = switchService.dump();
        assertEquals(1, Iterables.size(switches));
    }

    @Test(expected = MessageException.class)
    @Transactional
    public void change() throws Exception {
        SwitchInfoData data = new SwitchInfoData(switchId, SwitchEventType.CHANGED, address, name, description);

        switchService.change(data);
    }
}