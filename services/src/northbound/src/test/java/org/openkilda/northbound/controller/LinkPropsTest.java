/* Copyright 2018 Telstra Open Source
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
package org.openkilda.northbound.controller;

import static org.junit.Assert.assertThat;
import static org.hamcrest.CoreMatchers.is;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.openkilda.northbound.dto.LinkPropsDto;
import org.openkilda.northbound.service.LinkPropsResult;
import org.openkilda.northbound.service.LinkService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.ArrayList;
import java.util.List;

@RunWith(SpringJUnit4ClassRunner.class)
@WebAppConfiguration
@ContextConfiguration(classes = TestLinkPropsConfig.class)
@TestPropertySource("classpath:northbound.properties")
public class LinkPropsTest extends NorthboundBaseTest {

    private MockRestServiceServer mockServer;

    @Autowired
    private RestTemplate restTemplate;

    @Autowired
    private LinkService linkService;

    @Value("${topology.engine.rest.endpoint}")
    private String topologyEngineRest;

    private UriComponentsBuilder linkPropsBuilder;


    @Before
    public void setUp() throws Exception {
        this.mockServer = MockRestServiceServer.createServer(restTemplate);
        linkPropsBuilder = UriComponentsBuilder.fromHttpUrl(topologyEngineRest)
                .pathSegment("api", "v1", "topology", "link", "props");
    }

    /*
     * Tests for the CLIENT side - ie test the LinkService from the perspective of its ability to
     * handle the responses from the TopologyEngineRest API.
     */
    private static final String responseWithSuccess = "{" +
            "    \"failures\": 0," +
            "    \"messages\": []," +
            "    \"successes\": 3" +
            "}";

    private static final String responseWithFailures = "{" +
            "  \"failures\": 2, " +
            "  \"messages\": [" +
            "    \"RESERVED WORD VIOLATION: do not use speed\", " +
            "    \"RESERVED WORD VIOLATION: do not use speed\"" +
            "  ], " +
            "  \"successes\": 1" +
            "}";

    private static final String getResponse1 = "[{" +
            "        \"dst_port\": \"2\"," +
            "        \"dst_switch\": \"de:ad:be:ef:02:11:22:02\"," +
            "        \"props\": {" +
            "            \"cost\": \"1\"," +
            "            \"popularity\": \"5\"" +
            "        }," +
            "        \"src_port\": \"1\"," +
            "        \"src_switch\": \"de:ad:be:ef:01:11:22:01\"" +
            "    }]";

    /**
     * Test the linkService's ability to process a getLinkProps response. Mostly, this is validating
     * the transition from the json to java objects.
     */
    @Test
    public void getLinkProps() throws Exception {

        mockServer.expect(requestTo(linkPropsBuilder.build().toUriString()))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(getResponse1, MediaType.APPLICATION_JSON));

        // test with null (no) parameters
        LinkPropsDto lpdto = null;
        List<LinkPropsDto> result = linkService.getLinkProps(lpdto);

        mockServer.verify();
        assertThat(result.size(), is(1));
        lpdto = result.get(0);
        assertThat(lpdto.getDstSwitch(), is("de:ad:be:ef:02:11:22:02"));
        assertThat(lpdto.getProperty("cost"), is("1"));
    }

    /**
     * Test the linkService's ability to process a putLinkProps response.
     */
    @Test
    public void putLinkProps() throws Exception {
        // Use responseWithFailures .. which holds failure messages
        mockServer.expect(requestTo(linkPropsBuilder.build().toUriString()))
                .andExpect(method(HttpMethod.PUT))
                .andRespond(withSuccess(responseWithFailures, MediaType.APPLICATION_JSON));

        // test with null (no) parameters .. keep in mind this is just testing the linkService
        // ability to process a response, witch is putResponseFailures. The linkService.setLinkProps
        // doesn't do any validity checks at the moment .. it is just a simple pass through to
        // TER.  Once that changes, and logic from TER moves here, then the test will need to change.
        List<LinkPropsDto> request = new ArrayList<>();
        LinkPropsResult result = linkService.setLinkProps(request);

        mockServer.verify();
        assertThat(result.getFailures(), is(2));
        assertThat(result.getSuccesses(), is(1));
        assertThat(result.getMessages().length, is(2));
    }

    /**
     * Test the linkService's ability to process a delLinkProps response.
     */
    @Test
    public void delLinkProps() throws Exception {
        // Use responseWithSuccess .. which holds no failures .. testing empty array
        mockServer.expect(requestTo(linkPropsBuilder.build().toUriString()))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withSuccess(responseWithSuccess, MediaType.APPLICATION_JSON));

        // NB: similar comment as the putLinkProps Test .. read that for more information.
        List<LinkPropsDto> request = new ArrayList<>();
        LinkPropsResult result = linkService.delLinkProps(request);

        mockServer.verify();
        assertThat(result.getFailures(), is(0));
        assertThat(result.getSuccesses(), is(3));
        assertThat(result.getMessages().length, is(0));
    }

    /**
     * This method occasionally holds some code that we are trying to debug.
     */
    @Test
    public void pleaseWork() {

    }

}
