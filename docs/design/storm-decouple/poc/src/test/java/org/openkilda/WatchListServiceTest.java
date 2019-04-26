package org.openkilda;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WatchListServiceTest {

    @Mock
    IWatchListServiceCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }


    @org.junit.Test
    public void addWatch() {

        WatchListService s = new WatchListService(carrier, 10);

        s.addWatch(new SwitchId(1), 1, 1);
        s.addWatch(new SwitchId(1), 2, 1);
        s.addWatch(new SwitchId(2), 1, 2);
        s.addWatch(new SwitchId(2), 1, 2);
        s.addWatch(new SwitchId(2), 2, 3);

        assertThat(s.getEndpoints().size(), is(4));
        assertThat(s.getTimeouts().size(), is(3));

        verify(carrier, times(4)).discoveryRequest(any(SwitchId.class), anyInt(), anyLong());
    }


    @org.junit.Test
    public void removeWatch() {
        WatchListService s = new WatchListService(carrier, 10);

        s.addWatch(new SwitchId(1), 1, 1);
        s.addWatch(new SwitchId(1), 2, 1);
        s.addWatch(new SwitchId(2), 1, 11);


        assertThat(s.getEndpoints().size(), is(3));

        s.removeWatch(new SwitchId(1), 1, 12);
        s.removeWatch(new SwitchId(1), 2, 12);
        s.removeWatch(new SwitchId(2), 1, 12);

        assertThat(s.getEndpoints().size(), is(0));
        assertThat(s.getTimeouts().size(), is(2));

        s.tick(100);

        assertThat(s.getTimeouts().size(), is(0));

        verify(carrier, times(3)).discoveryRequest(any(SwitchId.class), anyInt(), anyLong());
    }

    @org.junit.Test
    public void tick() {
        WatchListService s = new WatchListService(carrier, 10);

        s.addWatch(new SwitchId(1), 1, 1);
        s.addWatch(new SwitchId(1), 2, 1);
        s.addWatch(new SwitchId(2), 1, 5);
        s.addWatch(new SwitchId(2), 2, 10);

        for (int i = 0; i <= 100; i++) {
            s.tick(i);
        }
        verify(carrier, times(10)).discoveryRequest(eq(new SwitchId(1)), eq(1), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(new SwitchId(1)), eq(2), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(new SwitchId(2)), eq(1), anyLong());
        verify(carrier, times(10)).discoveryRequest(eq(new SwitchId(2)), eq(2), anyLong());
    }
}