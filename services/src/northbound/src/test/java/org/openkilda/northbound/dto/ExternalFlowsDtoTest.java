package org.openkilda.northbound.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

public class ExternalFlowsDtoTest {


    private static final String json = "    {" +
            "         \"flow_id\": \"flow1\"," +
            "         \"src_node\": \"DEADBEEF12345677-p.3-v.45\"," +
            "         \"dst_node\": \"DEADBEEF12345677-p.1-v.23\"," +
            "         \"max_bandwidth\": 1000," +
            "         \"forward_path\": [{" +
            "             \"switch_name\": \"my.big.switch\"," +
            "             \"switch_id\": \"DEADBEEF12345677\"," +
            "             \"cookie\": \"0x10400000005dc8f\"," +
            "             \"cookie_int\": 73183493945154703" +
            "         }]," +
            "         \"reverse_path\": [{" +
            "             \"switch_name\": \"my.big.switch\"," +
            "             \"switch_id\": \"DEADBEEF12345677\"," +
            "             \"cookie\": \"0x18400000005dc8f\"," +
            "             \"cookie_int\": 109212290964118671" +
            "         }]" +
            "    }";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verify we can unmarshall the json text into an object of this kind.
     */
    @Test
    public void unmarshall() throws IOException {
        ExternalFlowsDto obj = mapper.readValue(json, ExternalFlowsDto.class);
        assertThat(obj.getFlow_id(), is("flow1"));
        assertThat(obj.getForward_path().size(), is(1));
        assertThat(obj.getForward_path().get(0).getSwitch_id(), is("DEADBEEF12345677"));
    }
    
}