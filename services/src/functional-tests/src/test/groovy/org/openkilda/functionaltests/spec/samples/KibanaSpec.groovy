package org.openkilda.functionaltests.spec.samples

import org.openkilda.functionaltests.BaseSpecification
import org.openkilda.functionaltests.helpers.KibanaHelper
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.Ignore

@Ignore("This is an example specification")
class KibanaSpec extends BaseSpecification {
    @Autowired
    KibanaHelper kibanaHelper;

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