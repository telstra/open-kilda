package org.openkilda;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

@RunWith(MockitoJUnitRunner.class)
public class DecisionMakerServiceTest {

    @Mock
    IDecisionMakerCarrier carrier;

    @Before
    public void setup() {
        reset(carrier);
    }

    @Test
    public void discovered() {
        DecisionMakerService w = new DecisionMakerService(carrier, 10, 5);
        w.discovered(new SwitchId(1), 10, new SwitchId(2), 20, 1L);
        w.discovered(new SwitchId(2), 20, new SwitchId(3), 30, 2L);
        verify(carrier).discovered(eq(new SwitchId(1)), eq(10), eq(new SwitchId(2)), eq(20), anyLong());
        verify(carrier).discovered(eq(new SwitchId(2)), eq(20), eq(new SwitchId(3)), eq(30), anyLong());
    }

    @Test
    public void failed() {
        DecisionMakerService w = new DecisionMakerService(carrier, 10, 5);
        w.failed(new SwitchId(1), 10, 0);
        w.failed(new SwitchId(1), 10, 1);
        w.failed(new SwitchId(1), 10, 11);
        w.failed(new SwitchId(1), 10, 12);
        verify(carrier, times(2)).failed(eq(new SwitchId(1)), eq(10), anyLong());
        w.discovered(new SwitchId(1), 10, new SwitchId(2), 20, 20);
        verify(carrier).discovered(new SwitchId(1), 10, new SwitchId(2), 20, 20);
        reset(carrier);
        w.failed(new SwitchId(1), 10, 21);
        w.failed(new SwitchId(1), 10, 23);
        w.failed(new SwitchId(1), 10, 24);
        verify(carrier, never()).failed(any(SwitchId.class), anyInt(), anyInt());
        w.failed(new SwitchId(1), 10, 31);
        verify(carrier).failed(new SwitchId(1), 10, 31);
    }
}