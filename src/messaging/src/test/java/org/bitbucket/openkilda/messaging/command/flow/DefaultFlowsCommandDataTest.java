package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.command.Constants.switchId;
import static org.junit.Assert.assertEquals;

import org.junit.Test;

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
