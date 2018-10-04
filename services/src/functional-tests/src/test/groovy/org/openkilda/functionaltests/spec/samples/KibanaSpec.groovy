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

package org.openkilda.functionaltests.spec.samples

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.ElasticHelper
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore

@Ignore("This is an example specification")
class KibanaSpec extends BaseSpecification {
    @Autowired
    ElasticHelper kibanaHelper

    def "Test framework should be able to extract logs from Storm Worker"() {
        when: "Kibana Helper is initialized"
        assert kibanaHelper

        then: "STORM would generate at least 1 INFO message per 5 minutes"
        def logs = kibanaHelper.getLogs("storm-worker_log", "", "INFO OR WARNING OR ERROR", [:],300)
        assert logs
        assert logs?.hits?.total > 0

        and: "It should be possible to read a log message"
        def hits = logs?.hits?.hits
        assert hits
        assert hits[0]._source.message
        cleanup:
        print(logs)
    }
}
