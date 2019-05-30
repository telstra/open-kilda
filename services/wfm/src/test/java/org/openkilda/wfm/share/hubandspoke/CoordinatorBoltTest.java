/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.share.hubandspoke;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.apache.storm.task.OutputCollector;
import org.apache.storm.task.TopologyContext;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.Set;
import java.util.stream.Collectors;

@RunWith(MockitoJUnitRunner.class)
public class CoordinatorBoltTest {

    private CoordinatorBolt target = new CoordinatorBolt();

    @Mock
    private OutputCollector collector;

    @Mock
    private TopologyContext topologyContext;

    @Before
    public void setup() {
        when(topologyContext.getThisTaskId()).thenReturn(1);

        target.prepare(null, topologyContext, collector);

        reset(collector);
    }

    @Test
    public void shouldAddAndDeleteCallback() {
        final String key = "request";
        final String context = "some context";
        final int timeout = 3600;
        final int taskId = 101;
        target.registerCallback(key, context, timeout, taskId);

        assertThat(target.getCallbacks().size(), is(1));
        assertThat(target.getTimeouts().size(), is(1));

        target.cancelCallback(key);
        assertTrue(target.getCallbacks().isEmpty());
    }

    @Test
    public void shouldEmitCallbackInCaseOfTimeout() throws Exception {
        final int timeout = 1;
        final int firstTask = 101;
        target.registerCallback("request1", "some context", timeout, firstTask);

        final int secondTask = 102;
        target.registerCallback("request2", "some context", timeout, secondTask);

        assertThat(target.getCallbacks().size(), is(2));

        // check that we have stored timeouts for out tasks
        Set<String> tasks = target.getTimeouts()
                .values()
                .stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());
        assertThat(tasks.size(), is(2));

        long afterTimeout = System.currentTimeMillis() + timeout + 1L;
        target.tick(afterTimeout);
        verify(collector).emitDirect(eq(firstTask), anyList());
        verify(collector).emitDirect(eq(secondTask), anyList());
    }
}
