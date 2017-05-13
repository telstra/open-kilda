package org.bitbucket.openkilda.floodlight.message.command;

import org.bitbucket.openkilda.messaging.command.flow.DefaultFlowsCommandData;
import org.junit.Test;

import static org.bitbucket.openkilda.floodlight.Constants.switchId;
import static org.junit.Assert.*;

/**
 * Created by atopilin on 10/04/2017.
 */
public class DefaultFlowsCommandDataTest {
    @Test
    public void switchId() throws Exception {
        DefaultFlowsCommandData defaultFlowsCommandData = new DefaultFlowsCommandData();
        defaultFlowsCommandData.setSwitchId(switchId);
        assertEquals(switchId, defaultFlowsCommandData.getSwitchId());
    }
}
