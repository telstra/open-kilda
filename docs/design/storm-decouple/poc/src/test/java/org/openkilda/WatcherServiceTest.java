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
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class WatcherServiceTest {

    @Mock
    IWatcherServiceCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }


    @Test
    public void addWatch() {
        WatcherService w = new WatcherService(carrier, 10);
        w.addWatch(new SwitchId(1), 1, 1);
        w.addWatch(new SwitchId(1), 2, 1);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 2, 3);

        assertThat(w.getConfirmations().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));

        verify(carrier, times(5)).sendDiscovery(any(SwitchId.class), anyInt(), anyLong(), anyLong());
    }

    @Test
    public void removeWatch() {
        //TODO: not critical
    }

    @Test
    public void tick() {
        WatcherService w = new WatcherService(carrier, 10);
        w.addWatch(new SwitchId(1), 1, 1);
        w.addWatch(new SwitchId(1), 2, 1);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 2, 3);

        assertThat(w.getConfirmations().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(SwitchId.class), anyInt(), anyLong(), anyLong());

        w.confirmation(new SwitchId(1), 1, 0);
        w.confirmation(new SwitchId(2), 1, 2);

        assertThat(w.getConfirmations().size(), is(2));

        w.tick(100);

        assertThat(w.getConfirmations().size(), is(0));

        verify(carrier).failed(eq(new SwitchId(1)), eq(1), anyLong());
        verify(carrier).failed(eq(new SwitchId(2)), eq(1), anyLong());
        verify(carrier, times(2)).failed(any(SwitchId.class), anyInt(), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }

    @Test
    public void discovery() {
        WatcherService w = new WatcherService(carrier, 10);
        w.addWatch(new SwitchId(1), 1, 1);
        w.addWatch(new SwitchId(1), 2, 1);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 1, 2);
        w.addWatch(new SwitchId(2), 2, 3);

        assertThat(w.getConfirmations().size(), is(0));
        assertThat(w.getTimeouts().size(), is(3));
        verify(carrier, times(5)).sendDiscovery(any(SwitchId.class), anyInt(), anyLong(), anyLong());

        w.confirmation(new SwitchId(1), 1, 0);
        w.confirmation(new SwitchId(2), 1, 2);
        assertThat(w.getConfirmations().size(), is(2));

        w.discovery(new SwitchId(1), 1, new SwitchId(2), 1, 0, 4);
        w.discovery(new SwitchId(2), 1, new SwitchId(1), 1, 2, 4);

        w.tick(100);

        assertThat(w.getConfirmations().size(), is(0));

        verify(carrier).discovered(eq(new SwitchId(1)), eq(1), eq(new SwitchId(2)), eq(1), anyLong());
        verify(carrier).discovered(eq(new SwitchId(2)), eq(1), eq(new SwitchId(1)), eq(1), anyLong());
        verify(carrier, times(2)).discovered(any(SwitchId.class), anyInt(), any(SwitchId.class), anyInt(), anyLong());

        assertThat(w.getTimeouts().size(), is(0));
    }
}