package org.openkilda.northbound.dto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;

import static org.hamcrest.core.Is.is;
import static org.junit.Assert.*;

/**
 * Simple unit tests to validate the marshalling / unmarshalling of data
 */
public class LinkPropsDtoTest {

    private static final String lpd_1 = "{" +
            "        \"dst_pt\": \"2\"," +
            "        \"dst_sw\": \"de:ad:be:ef:02:11:22:02\"," +
            "        \"props\": {" +
            "            \"cost\": \"1\"," +
            "            \"popularity\": \"5\"" +
            "        }," +
            "        \"src_pt\": \"1\"," +
            "        \"src_sw\": \"de:ad:be:ef:01:11:22:01\"" +
            "    }";

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Verify we can unmarshall the json text into an object of this kind.
     */
    @Test
    public void getProperty() throws IOException {
        LinkPropsDto lpd = mapper.readValue(lpd_1, LinkPropsDto.class);
        assertThat(lpd.getProperty("cost"), is("1"));
    }

}