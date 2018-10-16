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
import org.openkilda.testing.service.elastic.ElasticQueryBuilder
import org.openkilda.testing.service.elastic.ElasticService
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore

@Ignore("This is an example specification")
class KibanaSpec extends BaseSpecification {

    @Autowired
    ElasticService elastic

    def "Test framework should be able to extract logs from Storm Worker"() {
        when: "Elastic Client is initialized"
        assert elastic

        then: "Retrieve all INFO+ level messages from Storm Worker for last 5 minutes"
        def logs = elastic.getLogs(new ElasticQueryBuilder().setAppId("storm-worker_log").setTimeRange(300).build())
        logs
        logs?.hits?.total > 0

        and: "It should be possible to read a log message"
        def hits = logs?.hits?.hits
        hits
        hits[0]._source.message
        cleanup:
        print(logs)
    }
}
