package org.openkilda.northbound.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Test basic aspects of LinksDto ... primarily marshalling and/or unmarshalling.
 *
 * LinksDto has an "other" field, which is used to capture any "other properties"
 */
public class LinksDtoTest {

    private static final String standardResponse = "{" +
            "        \"clazz\": \"org.openkilda.messaging.info.event.IslInfoData\"," +
            "        \"latency_ns\": 10," +
            "        \"path\": [{\"switch_id\": \"0001\"," +
            "                  \"port_no\": 1," +
            "                  \"seq_id\": 0," +
            "                  \"segment_latency\": 10}," +
            "                 {\"switch_id\": \"0002\"," +
            "                  \"port_no\": 2," +
            "                  \"seq_id\": 1," +
            "                  \"segment_latency\": 0}]," +
            "        \"speed\": 101,\n" +
            "        \"state\": \"DISCOVERED\"," +
            "        \"available_bandwidth\": 102" +
            "}";

    private static final String standardCostResponse = "{" +
            "        \"clazz\": \"org.openkilda.messaging.info.event.IslInfoData\"," +
            "        \"latency_ns\": 10," +
            "        \"path\": [{\"switch_id\": \"0001\"," +
            "                  \"port_no\": 1," +
            "                  \"seq_id\": 0," +
            "                  \"segment_latency\": 10}," +
            "                 {\"switch_id\": \"0002\"," +
            "                  \"port_no\": 2," +
            "                  \"seq_id\": 1," +
            "                  \"segment_latency\": 0}]," +
            "        \"speed\": 101,\n" +
            "        \"state\": \"DISCOVERED\"," +
            "        \"cost\": \"10\"," +
            "        \"available_bandwidth\": 102" +
            "}";

    private static final ObjectMapper mapper = new ObjectMapper();

    @Test
    public void unserializeStandard() throws IOException {
        LinksDto dto = mapper.readValue(standardResponse, LinksDto.class);
        assertThat(dto.getSpeed(), is(101L));
    }

    @Test
    public void unserializeCost() throws IOException {
        LinksDto dto = mapper.readValue(standardCostResponse, LinksDto.class);
        assertThat(dto.getSpeed(), is(101L));
        assertThat(dto.otherFields.get("cost"), is("10"));
    }

}