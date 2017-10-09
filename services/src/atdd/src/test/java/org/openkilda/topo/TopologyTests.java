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

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

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
        Topology t1 = new Topology();
        Topology t2 = new Topology();
        Switch s1 = new Switch("one");
        Switch s2 = new Switch("one");
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s1.getId(),s1);
        assertTrue("Single Switch topologies, same object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch topologies, same object are equal", t1.equals(t2));

        t1.clear();
        t2.clear();
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s2.getId(),s2);
        assertTrue("Single Switch topologies, different object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch topologies, different object are not equal", t1.equals(t2));

        Switch s3 = new Switch("three");
        t1.getSwitches().put(s3.getId(),s3);
        t2.getSwitches().put(s3.getId(),s3);
        assertTrue("Multiple Switch topologies, mixed objects are equivalent", t1.equivalent(t2));
        assertTrue("Multiple Switch topologies, mixed objects are equal", t1.equals(t2));

        t1.clear();
        t2.clear();
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s3.getId(),s3);
        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));

    }

    @Test
    public void SwitchAndLinkEquivalenceTest(){
        Topology t1 = new Topology();
        Topology t2 = new Topology();
        Switch s1 = new Switch("one");
        Switch s2 = new Switch("one");
        LinkEndpoint le1 = new LinkEndpoint(s1,null,null);
        LinkEndpoint le2 = new LinkEndpoint(s2,null,null);
        Link l1a = new Link(le1, le2);
        Link l1b = new Link(le1, le2);
        Link l2a = new Link(le2, le1);
        Link l2b = new Link(le2, le1);
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s1.getId(),s1);
        t1.getLinks().put(l1a.getSlug(),l1a);
        t2.getLinks().put(l1a.getSlug(),l1a);
        assertTrue("Single Switch-Link topologies, same object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch-Link topologies, same object are equal", t1.equals(t2));

        t1.clear();
        t2.clear();
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s2.getId(),s2);
        t1.getLinks().put(l1a.getSlug(),l1a);
        t2.getLinks().put(l1b.getSlug(),l1b);
        assertTrue("Single Switch-Link topologies, different object are equivalent", t1.equivalent(t2));
        assertTrue("Single Switch-Link topologies, different object are equal", t1.equals(t2));

        Switch s3 = new Switch("three");
        t1.getSwitches().put(s3.getId(),s3);
        t2.getSwitches().put(s3.getId(),s3);
        t1.getLinks().put(l2a.getSlug(),l2a);
        t2.getLinks().put(l2a.getSlug(),l2a);
        assertTrue("Multiple Switch topologies, mixed objects are equivalent", t1.equivalent(t2));
        assertTrue("Multiple Switch topologies, mixed objects are equal", t1.equals(t2));

        t1.clear();
        t2.clear();
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s3.getId(),s3);
        t1.getLinks().put(l1a.getSlug(),l1a);
        t2.getLinks().put(l2a.getSlug(),l2a);
        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));

        LinkEndpoint le3 = new LinkEndpoint(s3,null,null);
        Link l3 = new Link(le3, le2);
        t1.clear();
        t2.clear();
        t1.getSwitches().put(s1.getId(),s1);
        t2.getSwitches().put(s1.getId(),s1); // same
        t1.getLinks().put(l1a.getSlug(),l1a);
        t2.getLinks().put(l3.getSlug(),l3); // different

        assertFalse("Single Switch topology, different switches are not equivalent", t1.equivalent(t2));
        assertFalse("Single Switch topology, different switches are not equal", t1.equals(t2));


    }


}
