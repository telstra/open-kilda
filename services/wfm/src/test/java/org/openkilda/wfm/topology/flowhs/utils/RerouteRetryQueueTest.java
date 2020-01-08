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

import org.openkilda.model.PathId;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.model.FlowRerouteFact;

import org.junit.Assert;
import org.junit.Test;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class RerouteRetryQueueTest {
    private final CommandContext context = new CommandContext();
    private final String flowId = "flowA";
    private final FlowRerouteFact rerouteEmpty = new FlowRerouteFact(
            "empty", context, flowId, null, false, "reason 1");
    private final FlowRerouteFact reroutePathA = new FlowRerouteFact(
            "pathA", context, flowId, Collections.singleton(new PathId("flowA-pathA")), false, "reason 1");
    private final FlowRerouteFact reroutePathB = new FlowRerouteFact(
            "pathB", context, flowId, Collections.singleton(new PathId("flowA-pathB")), false, "reason 1");
    private final FlowRerouteFact rerouteForced = new FlowRerouteFact(
            "forced", context, flowId, null, true, "reason 1");

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

        Set<PathId> expectedPaths = Stream.of(rerouteEmpty, reroutePathA, rerouteForced, reroutePathB)
                .map(FlowRerouteFact::getPathsToReroute)
                .filter(Objects::nonNull)
                .flatMap(Collection::stream)
                .collect(Collectors.toSet());
        Assert.assertEquals(expectedPaths, reroute.getPathsToReroute());
        Assert.assertTrue(reroute.isForceReroute());
        Assert.assertEquals(reroutePathB.getRerouteReason(), reroute.getRerouteReason());
    }
}
