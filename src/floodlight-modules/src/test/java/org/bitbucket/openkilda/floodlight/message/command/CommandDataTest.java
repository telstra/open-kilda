package org.bitbucket.openkilda.floodlight.message.command;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class CommandDataTest {
    private CommandData commandData;

    @Before
    public void setUp() {
        commandData = new AbstractInstallFlow();
    }

    @Test
    public void destination() throws Exception {
        commandData.setDestination(CommandData.Destination.CONTROLLER);
        assertEquals(CommandData.Destination.CONTROLLER, commandData.getDestination());
    }

    @Test
    public void withDestination() throws Exception {
        commandData.withDestination(CommandData.Destination.TOPOLOGY_ENGINE);
        assertEquals(CommandData.Destination.TOPOLOGY_ENGINE, commandData.getDestination());
    }
}
