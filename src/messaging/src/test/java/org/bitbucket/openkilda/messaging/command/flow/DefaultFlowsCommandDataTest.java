package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class DefaultFlowsCommandDataTest {
    @Test
    public void switchId() throws Exception {
        DefaultFlowsCommandData defaultFlowsCommandData = new DefaultFlowsCommandData();
        defaultFlowsCommandData.setSwitchId(switchId);
        assertEquals(switchId, defaultFlowsCommandData.getSwitchId());
    }
}
