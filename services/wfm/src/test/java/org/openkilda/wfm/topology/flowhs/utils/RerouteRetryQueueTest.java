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

package org.openkilda.wfm.topology.flowhs.utils;

import org.openkilda.model.IslEndpoint;
import org.openkilda.model.SwitchId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collections;
import java.util.HashSet;
import java.util.Optional;
import java.util.Set;

public class RerouteRetryQueueTest {
    private final CommandContext context = new CommandContext();
    private final SwitchId switchId = new SwitchId(1);

    private final String flowId = "flowA";
    private final FlowRerouteFact rerouteEmpty = new FlowRerouteFact(
            "empty", context, flowId, null, false, false, "reason 1", 0);
    private final FlowRerouteFact reroutePathA = new FlowRerouteFact(
            "pathA", context, flowId, Collections.singleton(new IslEndpoint(switchId, 1)), false, false, "reason 2", 0);
    private final FlowRerouteFact reroutePathB = new FlowRerouteFact(
            "pathB", context, flowId, Collections.singleton(new IslEndpoint(switchId, 2)), false, false, "reason 3", 0);
    private final FlowRerouteFact rerouteForced = new FlowRerouteFact(
            "forced", context, flowId, null, true, true, "reason 4", 0);

    @Test
    public void addAndSizeOperations() {
        RerouteRetryQueue queue = new RerouteRetryQueue();

        Assert.assertEquals(0, queue.size());
        Assert.assertTrue(queue.isEmpty());

        queue.add(rerouteEmpty);
        Assert.assertEquals(1, queue.size());
        Assert.assertFalse(queue.isEmpty());

        queue.add(reroutePathA);
        Assert.assertEquals(2, queue.size());
        Assert.assertFalse(queue.isEmpty());

        queue.add(reroutePathB);
        Assert.assertEquals(2, queue.size());
        Assert.assertFalse(queue.isEmpty());
    }

    @Test
    public void removeAndSizeOperations() {
        RerouteRetryQueue queue = new RerouteRetryQueue();

        // empty
        Assert.assertFalse(queue.remove().isPresent());

        Optional<FlowRerouteFact> reroute;

        // one entry
        queue.add(rerouteEmpty);
        reroute = queue.remove();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(rerouteEmpty, reroute.get());
        Assert.assertFalse(queue.remove().isPresent());
        Assert.assertEquals(0, queue.size());

        // two entry
        queue.add(rerouteEmpty);
        queue.add(reroutePathA);
        reroute = queue.remove();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(rerouteEmpty, reroute.get());
        Assert.assertEquals(1, queue.size());

        reroute = queue.remove();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(reroutePathA, reroute.get());
        Assert.assertFalse(queue.remove().isPresent());
        Assert.assertEquals(0, queue.size());

        Assert.assertFalse(queue.remove().isPresent());

        // more than 2 entry
        queue.add(rerouteEmpty);
        queue.add(reroutePathA);
        queue.add(reroutePathB);

        reroute = queue.remove();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(rerouteEmpty, reroute.get());
        Assert.assertEquals(1, queue.size());

        reroute = queue.remove();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertNotEquals(reroutePathA, reroute.get());  // merged entry
        Assert.assertNotEquals(reroutePathB, reroute.get());  // merged entry
        Assert.assertFalse(queue.remove().isPresent());
        Assert.assertEquals(0, queue.size());

        Assert.assertFalse(queue.remove().isPresent());
    }

    @Test
    public void getOperations() {
        RerouteRetryQueue queue = new RerouteRetryQueue();
        Assert.assertFalse(queue.get().isPresent());

        Optional<FlowRerouteFact> reroute;
        queue.add(rerouteEmpty);
        reroute = queue.get();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(rerouteEmpty, reroute.get());

        queue.add(reroutePathA);
        reroute = queue.get();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(rerouteEmpty, reroute.get());

        queue.remove();
        reroute = queue.get();
        Assert.assertTrue(reroute.isPresent());
        Assert.assertSame(reroutePathA, reroute.get());
    }

    @Test
    public void mergeOperation() {
        RerouteRetryQueue queue = new RerouteRetryQueue();
        queue.add(rerouteEmpty);
        queue.add(reroutePathA);
        queue.add(reroutePathB);

        Optional<FlowRerouteFact> potential;
        queue.remove();
        potential = queue.get();
        Assert.assertTrue(potential.isPresent());

        FlowRerouteFact reroute = potential.get();
        Assert.assertEquals(reroutePathB.getKey(), reroute.getKey());
        Assert.assertSame(reroutePathB.getCommandContext(), reroute.getCommandContext());
        Assert.assertEquals(reroutePathB.getFlowId(), reroute.getFlowId());

        Set<IslEndpoint> expectedIslEndpoints = new HashSet<>();
        expectedIslEndpoints.addAll(reroutePathA.getAffectedIsl());
        expectedIslEndpoints.addAll(reroutePathB.getAffectedIsl());

        Assert.assertEquals(expectedIslEndpoints, reroute.getAffectedIsl());
        Assert.assertEquals(reroutePathB.getRerouteReason(), reroute.getRerouteReason());
    }

    @Test
    public void mergeOperationWhenAffectedIslsEmpty() {
        RerouteRetryQueue queue = new RerouteRetryQueue();
        queue.add(rerouteEmpty);
        queue.add(reroutePathA);
        queue.add(rerouteForced);
        queue.add(reroutePathB);

        Optional<FlowRerouteFact> potential;
        queue.remove();
        potential = queue.get();
        Assert.assertTrue(potential.isPresent());

        FlowRerouteFact reroute = potential.get();
        Assert.assertEquals(reroutePathB.getKey(), reroute.getKey());
        Assert.assertSame(reroutePathB.getCommandContext(), reroute.getCommandContext());
        Assert.assertEquals(reroutePathB.getFlowId(), reroute.getFlowId());

        Set<IslEndpoint> expectedIslEndpoints = Collections.emptySet();

        Assert.assertEquals(expectedIslEndpoints, reroute.getAffectedIsl());
        Assert.assertTrue(reroute.isForceReroute());
        Assert.assertEquals(reroutePathB.getRerouteReason(), reroute.getRerouteReason());
    }
}
