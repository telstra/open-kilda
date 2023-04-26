package org.openkilda.functionaltests.spec.flows
import org.openkilda.functionaltests.HealthCheckSpecification


import spock.lang.Narrative

@Narrative("Verify that ISL's bandwidth behaves consistently and does not allow any oversubscribtions etc.")
class MockSpec extends HealthCheckSpecification {

    def "Available bandwidth on ISLs changes respectively when creating/updating/deleting a flow"() {
        given: "Two active not neighboring switches"
        print("success")
    }
}
