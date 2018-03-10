/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topo;

import static java.util.Collections.emptyMap;
import static java.util.Collections.singletonMap;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import com.google.common.collect.ImmutableMap;
import org.junit.Test;

/**
 * Created by carmine on 3/8/17.
 */
public class TopologyTests {

    @Test
    public void EmptyTopologyEquivalenceTest() {
        Topology t1 = new Topology();
        Topology t2 = new Topology();
        assertTrue("Empty topologies are equivalent", t1.equivalent(t2));
        assertTrue("Empty topologies are equal", t1.equals(t2));
    }

    @Test
    public void JustSwitchEquivalenceTest(){
        Switch s1 = new Switch("one");
        Topology t1 = new Topology(singletonMap(s1.getId(),s1), emptyMap());
        assertTrue("Single Switch topologies, same object are equivalent", t1.equivalent(t1));
        assertTrue("Single Switch topologies, same object are equal", t1.equals(t1));

        Switch s2 = new Switch("one");
        Topology t2 = new Topology(singletonMap(s2.getId(),s2), emptyMap());
        assertTrue("Single Switch topologies, different object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch topologies, different object are equal", t1.equals(t2));

        Switch s3 = new Switch("three");
        t1 = new Topology(ImmutableMap.of(s1.getId(),s1, s3.getId(), s3), emptyMap());
        t2 = new Topology(ImmutableMap.of(s2.getId(),s2, s3.getId(), s3), emptyMap());
        assertTrue("Multiple Switch topologies, mixed objects are equivalent", t1.equivalent(t2));
        assertTrue("Multiple Switch topologies, mixed objects are equal", t1.equals(t2));

        t1 = new Topology(singletonMap(s1.getId(),s1), emptyMap());
        t2 = new Topology(singletonMap(s3.getId(), s3), emptyMap());
        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));
    }

    @Test
    public void SwitchAndLinkEquivalenceTest(){
        Switch s1 = new Switch("one");
        Switch s2 = new Switch("one");
        LinkEndpoint le1 = new LinkEndpoint(s1,null,null);
        LinkEndpoint le2 = new LinkEndpoint(s2,null,null);
        Link l1a = new Link(le1, le2);
        Link l1b = new Link(le1, le2);
        Link l2a = new Link(le2, le1);
        Link l2b = new Link(le2, le1);
        Topology t1 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l1a.getSlug(),l1a));
        Topology t2 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l1a.getSlug(),l1a));
        assertTrue("Single Switch-Link topologies, same object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch-Link topologies, same object are equal", t1.equals(t2));

        t1 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l1a.getSlug(),l1a));
        t2 = new Topology(singletonMap(s2.getId(),s2), singletonMap(l1b.getSlug(),l1b));
        assertTrue("Single Switch-Link topologies, different object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch-Link topologies, different object are equal", t1.equals(t2));

        Switch s3 = new Switch("three");
        t1 = new Topology(singletonMap(s3.getId(),s3), singletonMap(l2a.getSlug(),l2a));
        t2 = new Topology(singletonMap(s3.getId(),s3), singletonMap(l2a.getSlug(),l2a));
        assertTrue("Multiple Switch topologies, mixed objects are equivalent", t1.equivalent(t2));
        assertTrue("Multiple Switch topologies, mixed objects are equal", t1.equals(t2));

        t1 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l1a.getSlug(),l1a));
        t2 = new Topology(singletonMap(s3.getId(),s3), singletonMap(l2a.getSlug(),l2a));
        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));

        LinkEndpoint le3 = new LinkEndpoint(s3,null,null);
        Link l3 = new Link(le3, le2);
        t1 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l1a.getSlug(),l1a));
        t2 = new Topology(singletonMap(s1.getId(),s1), singletonMap(l3.getSlug(),l3));
        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));
    }
}
