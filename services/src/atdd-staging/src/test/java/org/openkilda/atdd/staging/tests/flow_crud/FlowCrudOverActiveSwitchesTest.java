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
package org.openkilda.atdd.staging.tests.flow_crud;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import org.junit.runner.RunWith;
import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.atdd.staging.service.northbound.NorthboundService;
import org.openkilda.atdd.staging.service.traffexam.OperationalException;
import org.openkilda.atdd.staging.service.traffexam.TraffExamService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;


@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/flow_crud.feature"},
        glue = {"org.openkilda.atdd.staging.tests.flow_crud", "org.openkilda.atdd.staging.steps"},
        plugin = {"json:target/cucumber-reports/flow_crud_report.json"})
@ActiveProfiles("mock")
public class FlowCrudOverActiveSwitchesTest {

    public static class FlowCrudOverActiveSwitchesHook {

        @Autowired
        private NorthboundService northboundService;

        @Autowired
        private TraffExamService traffExamService;

        @After
        public void assertsAndVerifyMocks() throws OperationalException {
            verify(northboundService, times(3)).addFlow(any());
            verify(northboundService, times(3)).updateFlow(any(), any());
            verify(northboundService, times(3)).deleteFlow(any());

            // 3 flows * 2 directions * (on create + on update) = 12 times
            verify(traffExamService, times(12)).startExam(any());
            // 3 flows * (on create + on update) = 6 times
            verify(traffExamService, times(6)).waitExam(any());
        }
    }
}
