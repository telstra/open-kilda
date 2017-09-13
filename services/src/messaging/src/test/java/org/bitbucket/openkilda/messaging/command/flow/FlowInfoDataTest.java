package org.bitbucket.openkilda.messaging.command.flow;

import static org.bitbucket.openkilda.messaging.Utils.MAPPER;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;

import org.bitbucket.openkilda.messaging.info.flow.FlowInfoData;
import org.bitbucket.openkilda.messaging.info.flow.FlowOperation;
import org.bitbucket.openkilda.messaging.model.Flow;
import org.bitbucket.openkilda.messaging.model.ImmutablePair;

import org.junit.Test;

public class FlowInfoDataTest {
    @Test
    public void toStringTest() throws Exception {
        FlowInfoData data = new FlowInfoData(new ImmutablePair<>(new Flow(), new Flow()), FlowOperation.CREATE);
        String dataString = data.toString();
        assertNotNull(dataString);
        assertFalse(dataString.isEmpty());

        System.out.println(MAPPER.writeValueAsString(data.getPayload()));
    }
}
