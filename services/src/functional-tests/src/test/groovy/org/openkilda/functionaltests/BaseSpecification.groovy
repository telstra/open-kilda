package org.openkilda.functionaltests

import static org.junit.Assume.assumeTrue

import org.openkilda.functionaltests.extension.fixture.SetupOnce

import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends SpringSpecification implements SetupOnce {
    @Value('${spring.profiles.active}')
    String profile

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass.
     * Can be overridden by inheritor specs.
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    def setupOnce() {
    }

    def setup() {
        //setup with empty body in order to trigger a SETUP invocation, which is intercepted in several extensions
        //this can have implementation if required
    }

    def requireProfiles(String[] profiles) {
        assumeTrue("This test required one of these profiles: ${profiles.join(',')}; " +
                "but current active profile is '${this.profile}'", this.profile in profiles)
    }
}
