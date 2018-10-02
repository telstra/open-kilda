package org.openkilda.functionaltests

import org.openkilda.functionaltests.extension.spring.PreparesSpringContextDummy

import spock.lang.Specification

class SpringSpecification extends Specification {

    /**
     * This is a dummy test which is ran as the first ever test to init Spring context.
     * @see org.openkilda.functionaltests.extension.spring.SpringContextExtension
     */
    @PreparesSpringContextDummy
    def "Prepare spring context.."() {
        expect: true
    }
}
