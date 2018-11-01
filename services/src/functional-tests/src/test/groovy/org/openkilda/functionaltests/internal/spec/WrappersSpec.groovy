package org.openkilda.functionaltests.internal.spec

import org.openkilda.functionaltests.helpers.Wrappers
import org.openkilda.functionaltests.helpers.Wrappers.WaitTimeoutException

import spock.lang.Specification
import spock.lang.Unroll

@Unroll
class WrappersSpec extends Specification {

    def timeout = 0.001

    def "Wrappers.wait throws when condition fails"() {
        when: "Wait is fed with a false condition"
        Wrappers.wait(timeout, condition)

        then: "WaitTimeoutException is thrown"
        thrown(WaitTimeoutException)

        where:
        condition << [{ false }, { assert false }, { throw new Exception() }, { [] }]
    }

    def "Wrappers.wait returns true if condition passes"() {
        expect: "Wait returns true on OK conditions"
        Wrappers.wait(timeout, condition)

        where:
        condition << [{ true }, { assert true }, { }, { new Object() }]
    }
}
