package org.openkilda.functionaltests

import org.openkilda.functionaltests.extension.fixture.SetupOnce
import org.springframework.beans.factory.annotation.Value
import org.springframework.test.context.ContextConfiguration

import static org.junit.Assume.assumeTrue

@ContextConfiguration(locations = ["classpath:/spring-context.xml"])
class BaseSpecification extends SpringSpecification implements SetupOnce {
    @Value('${spring.profiles.active}')
    String profile

    /**
     * Use this instead of setupSpec in order to have access to Spring Context and do actions BeforeClass
     * @see {@link org.openkilda.functionaltests.extension.fixture.SetupOnceExtension}
     */
    def setupOnce() {
    }

    def setup() {
    }

    def requireProfiles(String[] profiles) {
        assumeTrue("This test required one of these profiles: ${profiles.join(',')}; " +
                "but current active profile is '${this.profile}'", this.profile in profiles)
    }
}
