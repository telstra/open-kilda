package org.openkilda.functionaltests

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.springframework.test.context.ActiveProfiles
import org.springframework.test.context.ContextConfiguration
import spock.lang.Specification

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends Specification implements SetupOnce {
    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    def setupOnce() {
    }

    def setup() {
    }
}
