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

package org.openkilda.atdd.staging.tests.monkeysuite.checktraffic;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import org.openkilda.atdd.staging.cucumber.CucumberWithSpringProfile;
import org.openkilda.testing.service.traffexam.OperationalException;
import org.openkilda.testing.service.traffexam.TraffExamService;

import cucumber.api.CucumberOptions;
import cucumber.api.java.After;
import org.junit.Ignore;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ActiveProfiles;

@Ignore
@RunWith(CucumberWithSpringProfile.class)
@CucumberOptions(features = {"classpath:features/monkey_suite.feature"},
        glue = {"org.openkilda.atdd.staging.tests.monkeysuite.checktraffic", "org.openkilda.atdd.staging.steps"},
        tags = {"@CheckTraffic"},
        plugin = {"json:target/cucumber-reports/monkey_suite_check_traffic_report.json"})
@ActiveProfiles("mock")
public class MonkeySuiteCheckTrafficTest {

    public static class MonkeySuiteCheckTrafficHook {

        @Autowired
        private TraffExamService traffExamService;

        @After
        public void assertsAndVerifyMocks() throws OperationalException {
            // 3 flows = 3 times
            verify(traffExamService, times(3)).startExam(any());
            verify(traffExamService, times(3)).waitExam(any());
        }
    }
}
