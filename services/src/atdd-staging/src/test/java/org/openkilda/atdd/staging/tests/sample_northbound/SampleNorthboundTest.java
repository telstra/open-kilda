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

package org.openkilda.atdd.staging.tests.sample_northbound;

import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import cucumber.api.java.Before;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.service.NorthboundService;
import org.openkilda.messaging.payload.flow.FlowPayload;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

import java.util.Collections;

import static org.mockito.Mockito.*;

@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/sample_northbound.feature"},
        glue = {"org.openkilda.atdd.staging.tests.sample_northbound", "org.openkilda.atdd.staging.steps"})
@ActiveProfiles("mock")
public class SampleNorthboundTest {

    public static class SampleNorthboundHook {
        @Autowired
        private NorthboundService northboundService;

        @Before
        public void prepareMocks() {
            when(northboundService.getFlows())
                    .thenReturn(Collections.singletonList(mock(FlowPayload.class)));
        }

        @After
        public void verifyMocks() {
            verify(northboundService, times(1)).getFlows();
        }
    }
}
