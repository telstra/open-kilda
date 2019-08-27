package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.HARDWARE
import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.northbound.dto.v1.switches.SwitchDto
import org.openkilda.testing.service.grpc.GrpcService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/grpc-client")
@Tags([HARDWARE, SMOKE_SWITCHES])
class GrpcBaseSpecification extends HealthCheckSpecification {
    @Autowired
    GrpcService grpc

    @Memoized
    List<SwitchDto> getNoviflowSwitches() {
        northbound.activeSwitches.findAll {
            // it is not working properly if version <= 6.4
            def matcher = it.description =~ /NW[0-9]+.([0-9].[0-9])/
            return matcher && matcher[0][1] > "6.4"
        }
    }
}
