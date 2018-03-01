package org.openkilda.topo.builders;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import org.junit.Test;
import org.openkilda.topo.Link;
import org.openkilda.topo.Topology;
import org.openkilda.topo.exceptions.TopologyProcessingException;

public class TeTopologyParserTest {

    @Test
    public void shouldParseJson() {
        Topology result = TeTopologyParser.parseTopologyEngineJson("{\"nodes\":["
                + "{\"name\":\"sw1\", \"outgoing_relationships\": [\"sw2\"]}, "
                + "{\"name\":\"sw2\", \"outgoing_relationships\": []}"
                + "]}");

        assertEquals("Expected 2 switches to be parsed", 2, result.getSwitches().size());
        assertEquals("Expected 1 link to be parsed", 1, result.getLinks().size());
        assertTrue("Expected 'sw1' switch to be parsed", result.getSwitches().containsKey("SW1"));
        assertTrue("Expected 'sw2' switch to be parsed", result.getSwitches().containsKey("SW2"));
        Link link = result.getLinks().values().iterator().next();
        assertEquals("Expected a link from sw1 to be parsed", "SW1", link.getSrc().getTopoSwitch().getId());
        assertEquals("Expected a link to sw2 to be parsed", "SW2", link.getDst().getTopoSwitch().getId());
    }

    @Test
    public void shouldParseJsonWithoutRelations() {
        Topology result = TeTopologyParser.parseTopologyEngineJson("{\"nodes\":["
                + "{\"name\":\"sw1\"}, "
                + "{\"name\":\"sw2\"}"
                + "]}");

        assertEquals("Expected 2 switches to be parsed", 2, result.getSwitches().size());
        assertEquals("Expected 0 link to be parsed", 0, result.getLinks().size());
    }

    @Test(expected = TopologyProcessingException.class)
    public void shouldFailIfNodeHasNoName() {
        TeTopologyParser.parseTopologyEngineJson("{\"nodes\":["
                + "{\"outgoing_relationships\": [\"sw2\"]}, "
                + "{\"name\":\"sw2\", \"outgoing_relationships\": []}"
                + "]}");

        fail();
    }

    @Test(expected = TopologyProcessingException.class)
    public void shouldFailIfNodeHasEmptyName() {
        TeTopologyParser.parseTopologyEngineJson("{\"nodes\":["
                + "{\"name\":\"\", \"outgoing_relationships\": [\"sw2\"]}, "
                + "{\"name\":\"sw2\", \"outgoing_relationships\": []}"
                + "]}");

        fail();
    }
}