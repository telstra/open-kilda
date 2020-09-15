/* Copyright 2020 Telstra Open Source
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

package org.openkilda.wfm.topology.snmp.service;

import static org.mockserver.integration.ClientAndServer.startClientAndServer;

import org.openkilda.wfm.topology.snmp.model.SwitchDto;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.commons.codec.binary.Base64;
import org.apache.http.HttpHeaders;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockserver.client.server.MockServerClient;
import org.mockserver.integration.ClientAndServer;
import org.mockserver.model.Header;
import org.mockserver.model.HttpRequest;
import org.mockserver.model.HttpResponse;
import org.mockserver.verify.VerificationTimes;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

@RunWith(MockitoJUnitRunner.class)
public class TopologyUpdateServiceTest {

    private static final String PATH = "/hosts";
    private static final String HOST = "127.0.0.1";
    private static final int PORT = 18080;
    private static final String USERNAME = "snmp";
    private static final String PASSWORD = "snmp";

    private TopologyUpdateService topologyUpdateService;
    private List<SwitchDto> dtos;
    private ClientAndServer mockServer;
    private ObjectMapper mapper = new ObjectMapper();


    @Before
    public void setUp() throws Exception {
        Map<String, String> tags1 = Collections.singletonMap("switchId", "SW12345678");
        SwitchDto dto1 = new SwitchDto();
        dto1.setHostname("ofsw1.pen.amls");
        dto1.setTags(tags1);

        Map<String, String> tags2 = Collections.singletonMap("switchId", "SW87654321");
        SwitchDto dto2 = new SwitchDto();
        dto2.setHostname("ofsw3.pen.syeq");
        dto2.setTags(tags2);

        mockServer = startClientAndServer(PORT);

        dtos = Arrays.asList(dto1, dto2);
        topologyUpdateService = new TopologyUpdateService(String.format("http://%s:%d%s", HOST, PORT, PATH),
                USERNAME, PASSWORD);
    }

    @After
    public void cleanUp() {
        mockServer.stop();
    }

    @Test
    public void publishAddedUpdateTest() throws Exception {
        MockServerClient client = new MockServerClient("127.0.0.1", PORT);

        client.when(HttpRequest.request()
                .withMethod("POST")
                .withPath(String.format("%s/%s", PATH, UpdateAction.ADD.toString())))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(mapper.writeValueAsString(Collections.singletonMap("Status", "OK"))));

        topologyUpdateService.publishTopologyUpdate(UpdateAction.ADD, dtos);

        client.verify(
                HttpRequest.request()
                        .withMethod("POST").withHeader(
                        new Header(
                                HttpHeaders.AUTHORIZATION,
                                String.format("Basic %s", Base64.encodeBase64String(
                                        String.format("%s:%s", USERNAME, PASSWORD).getBytes()))))
                        .withPath(String.format("%s/%s", PATH, UpdateAction.ADD.toString()))
                        .withBody(mapper.writeValueAsString(dtos)), // better using JsonPath when it is available.
                VerificationTimes.exactly(1)
        );
    }

    @Test
    public void publishRemoveSwitchesUpdateTest() throws Exception {
        MockServerClient client = new MockServerClient("127.0.0.1", PORT);

        client.when(HttpRequest.request()
                .withMethod("POST")
                .withPath(String.format("%s/%s", PATH, UpdateAction.REMOVE.toString())))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(mapper.writeValueAsString(Collections.singletonMap("Status", "OK"))));

        topologyUpdateService.publishTopologyUpdate(UpdateAction.REMOVE, dtos);

        client.verify(
                HttpRequest.request()
                        .withMethod("POST")
                        .withPath(String.format("%s/%s", PATH, UpdateAction.REMOVE.toString())),
                VerificationTimes.exactly(1)
        );
    }

    @Test
    public void publishClearSwitchesUpdateTest() throws Exception {
        MockServerClient client = new MockServerClient("127.0.0.1", PORT);

        client.when(HttpRequest.request()
                .withMethod("POST")
                .withPath(String.format("%s/%s", PATH, UpdateAction.CLEAR.toString())))
                .respond(HttpResponse.response()
                        .withStatusCode(200)
                        .withBody(mapper.writeValueAsString(Collections.singletonMap("Status", "OK"))));
        topologyUpdateService.publishTopologyUpdate(UpdateAction.CLEAR, dtos);

        client.verify(
                HttpRequest.request()
                        .withMethod("POST")
                        .withPath(String.format("%s/%s", PATH, UpdateAction.CLEAR.toString())),
                VerificationTimes.exactly(1)
        );
    }
}
