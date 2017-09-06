package org.bitbucket.openkilda.pce.cache;

import static org.junit.Assert.assertEquals;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

public class ResourceCacheTest {
    private static final String SWITCH_ID = "switch-id";
    private ResourceCache resourceCache;

    @After
    public void tearDown() throws Exception {
        resourceCache.clear();
    }

    @Before
    public void setUp() throws Exception {
        resourceCache = new ResourceCache();
    }

    @Test
    public void allocateAll() throws Exception {
        // TODO
    }

    @Test
    public void cookiePool() throws Exception {
        resourceCache.allocateCookie(4);

        int first = resourceCache.allocateCookie();
        assertEquals(1, first);

        int second = resourceCache.allocateCookie();
        assertEquals(2, second);

        int third = resourceCache.allocateCookie();
        assertEquals(3, third);

        resourceCache.deallocateCookie(second);
        int fourth = resourceCache.allocateCookie();
        assertEquals(2, fourth);

        assertEquals(4, resourceCache.getAllCookies().size());

        int fifth = resourceCache.allocateCookie();
        assertEquals(5, fifth);
    }

    @Test
    public void vlanIdPool() throws Exception {
        resourceCache.allocateVlanId(5);

        int first = resourceCache.allocateVlanId();
        assertEquals(2, first);

        int second = resourceCache.allocateVlanId();
        assertEquals(3, second);

        int third = resourceCache.allocateVlanId();
        assertEquals(4, third);

        resourceCache.deallocateVlanId(second);
        int fourth = resourceCache.allocateVlanId();
        assertEquals(3, fourth);

        assertEquals(4, resourceCache.getAllVlanIds().size());

        int fifth = resourceCache.allocateVlanId();
        assertEquals(6, fifth);
    }

    @Test
    public void meterIdPool() throws Exception {
        resourceCache.allocateMeterId(SWITCH_ID, 4);

        int first = resourceCache.allocateMeterId(SWITCH_ID);
        assertEquals(1, first);

        int second = resourceCache.allocateMeterId(SWITCH_ID);
        assertEquals(2, second);

        int third = resourceCache.allocateMeterId(SWITCH_ID);
        assertEquals(3, third);

        resourceCache.deallocateMeterId(SWITCH_ID, second);
        int fourth = resourceCache.allocateMeterId(SWITCH_ID);
        assertEquals(2, fourth);

        assertEquals(4, resourceCache.getAllMeterIds(SWITCH_ID).size());

        int fifth = resourceCache.allocateMeterId(SWITCH_ID);
        assertEquals(5, fifth);

        assertEquals(5, resourceCache.deallocateMeterId(SWITCH_ID).size());
        assertEquals(0, resourceCache.getAllMeterIds(SWITCH_ID).size());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void vlanPoolFullTest() {
        resourceCache.allocateVlanId();
        int i = ResourceCache.MIN_VLAN_ID;
        while (i++ <= ResourceCache.MAX_VLAN_ID) {
            resourceCache.allocateVlanId();
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void cookiePoolFullTest() {
        resourceCache.allocateCookie();
        int i = ResourceCache.MIN_COOKIE;
        while (i++ <= ResourceCache.MAX_COOKIE) {
            resourceCache.allocateCookie();
        }
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void meterIdPoolFullTest() {
        resourceCache.allocateMeterId(SWITCH_ID);
        int i = ResourceCache.MIN_METER_ID;
        while (i++ <= ResourceCache.MAX_METER_ID) {
            resourceCache.allocateMeterId(SWITCH_ID);
        }
    }
}
