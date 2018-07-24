package org.openkilda.functionaltests

import org.springframework.test.context.ContextConfiguration
import spock.lang.Shared
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends Specification {
    @Shared boolean setupRun

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass
     */
    def setupOnce() {
    }

    def setup() {
        if(!setupRun) {
            setupOnce()
            setupRun = true
        }
    }
}
