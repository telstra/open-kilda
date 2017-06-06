package org.bitbucket.openkilda.messaging.payload;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class ResourcePoolTest {
    private static final ResourcePool pool = new ResourcePool(1, 10);

    @Test
    public void resourcePoolTest() {
        int first = pool.allocate();
        assertEquals(1, first);

        int second = pool.allocate();
        assertEquals(2, second);

        int third = pool.allocate();
        assertEquals(3, third);

        pool.deallocate(second);
        int fourth = pool.allocate();
        assertEquals(2, fourth);

        assertEquals(3, pool.dumpPool().size());
    }

    @Test(expected = ArrayIndexOutOfBoundsException.class)
    public void resourcePoolFullTest() {
        ResourcePool pool = new ResourcePool(1, 1);
        pool.allocate();
        pool.allocate();
    }
}
