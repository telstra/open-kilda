package org.bitbucket.openkilda.pce.manager;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ResourceManagerTest {
    private static final String SWITCH_ID = "switch-id";
    private ResourceManager resourceManager;

    @After
    public void tearDown() throws Exception {
        resourceManager.clear();
    }

    @Before
    public void setUp() throws Exception {
        resourceManager = new ResourceManager();
    }

    @Test
    public void allocateAll() throws Exception {
        // TODO
    }

    @Test
    public void cookiePool() throws Exception {
        resourceManager.allocateCookie(4);

        int first = resourceManager.allocateCookie();
        assertEquals(1, first);

        int second = resourceManager.allocateCookie();
        assertEquals(2, second);

        int third = resourceManager.allocateCookie();
        assertEquals(3, third);

        resourceManager.deallocateCookie(second);
        int fourth = resourceManager.allocateCookie();
        assertEquals(2, fourth);

        assertEquals(4, resourceManager.getAllCookies().size());

        int fifth = resourceManager.allocateCookie();
        assertEquals(5, fifth);
    }

    @Test
    public void vlanIdPool() throws Exception {
        resourceManager.allocateVlanId(5);

        int first = resourceManager.allocateVlanId();
        assertEquals(2, first);

        int second = resourceManager.allocateVlanId();
        assertEquals(3, second);

        int third = resourceManager.allocateVlanId();
        assertEquals(4, third);

        resourceManager.deallocateVlanId(second);
        int fourth = resourceManager.allocateVlanId();
        assertEquals(3, fourth);

        assertEquals(4, resourceManager.getAllVlanIds().size());

        int fifth = resourceManager.allocateVlanId();
        assertEquals(6, fifth);
    }

    @Test
    public void meterIdPool() throws Exception {
        resourceManager.allocateMeterId(SWITCH_ID, 4);

        int first = resourceManager.allocateMeterId(SWITCH_ID);
        assertEquals(1, first);

        int second = resourceManager.allocateMeterId(SWITCH_ID);
        assertEquals(2, second);

        int third = resourceManager.allocateMeterId(SWITCH_ID);
        assertEquals(3, third);

        resourceManager.deallocateMeterId(SWITCH_ID, second);
        int fourth = resourceManager.allocateMeterId(SWITCH_ID);
        assertEquals(2, fourth);

        assertEquals(4, resourceManager.getAllMeterIds(SWITCH_ID).size());

        int fifth = resourceManager.allocateMeterId(SWITCH_ID);
        assertEquals(5, fifth);

        assertEquals(5, resourceManager.deallocateMeterId(SWITCH_ID).size());
        assertEquals(0, resourceManager.getAllMeterIds(SWITCH_ID).size());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void vlanPoolFullTest() {
        resourceManager.allocateVlanId();
        int i = ResourceManager.MIN_VLAN_ID;
        while (i++ <= ResourceManager.MAX_VLAN_ID) {
            resourceManager.allocateVlanId();
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void cookiePoolFullTest() {
        resourceManager.allocateCookie();
        int i = ResourceManager.MIN_COOKIE;
        while (i++ <= ResourceManager.MAX_COOKIE) {
            resourceManager.allocateCookie();
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void meterIdPoolFullTest() {
        resourceManager.allocateMeterId(SWITCH_ID);
        int i = ResourceManager.MIN_METER_ID;
        while (i++ <= ResourceManager.MAX_METER_ID) {
            resourceManager.allocateMeterId(SWITCH_ID);
        }
    }
}
