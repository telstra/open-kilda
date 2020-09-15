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

package org.openkilda.snmp.collector.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.openkilda.snmp.collector.fixtures.SnmpCollectorTestFixtures;
import org.openkilda.snmp.collector.metrics.SnmpMetricCollector;
import org.openkilda.snmp.collector.metrics.SnmpMetricWriter;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.junit4.SpringRunner;
import org.springframework.test.web.servlet.MockMvc;

@RunWith(SpringRunner.class)
@WebMvcTest(SnmpTopologyController.class)
public class SnmpTopologyControllerTest {

    @MockBean
    SnmpMetricCollector collector;

    @MockBean
    SnmpMetricWriter metricWriter;

    @Autowired
    MockMvc mvc;

    @Value("${snmp.configs.username}")
    private String username;

    @Value("${snmp.configs.password}")
    private String password;

    ObjectMapper mapper = new ObjectMapper();

    @Test
    public void testGetHosts() throws Exception {
        when(collector.getHosts()).thenReturn(SnmpCollectorTestFixtures.getSnmpHostsFixture());

        mvc.perform(
                post("/hosts/add")
                        .with(httpBasic(username, password))
                        .content(mapper.writeValueAsString(SnmpCollectorTestFixtures.getSnmpHostsFixture()))
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().string(
                        mapper.writeValueAsString(SnmpCollectorTestFixtures.getSnmpHostsFixture())));

        Mockito.verify(collector).addHosts(any());
    }

    @Test
    public void testClearHosts() throws Exception {
        mvc.perform(
                post("/hosts/clear")
                        .with(httpBasic(username, password)))
                .andExpect(status().isNoContent());

        verify(collector).clearHosts();
    }

}
