package org.openkilda.functionaltests.spec.grpc

import static org.openkilda.functionaltests.extension.tags.Tag.SMOKE_SWITCHES
import static org.openkilda.testing.ConstantsGrpc.GRPC_STUB_CONTAINER_NAME
import static org.openkilda.testing.Constants.NON_EXISTENT_SWITCH_ID

import org.openkilda.functionaltests.HealthCheckSpecification
import org.openkilda.functionaltests.extension.tags.Tags
import org.openkilda.functionaltests.helpers.DockerHelper
import org.openkilda.messaging.info.event.SwitchChangeType
import org.openkilda.northbound.dto.v1.switches.SwitchDto
import org.openkilda.testing.service.grpc.GrpcService

import groovy.transform.Memoized
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Value
import spock.lang.See

@See("https://github.com/telstra/open-kilda/tree/develop/docs/design/grpc-client")
@Tags(SMOKE_SWITCHES)
class GrpcBaseSpecification extends HealthCheckSpecification {
    @Value('${spring.profiles.active}')
    String profile
    @Autowired
    GrpcService grpc
    @Value('${docker.host}')
    String dockerHost

    @Memoized
    List<SwitchDto> getNoviflowSwitches() {
        if (profile == "virtual") {
            /* Create fake switch for running test using grpc-stub
            NOTE: The grpc-stub service covers positive test cases only */
            def grpcStubIp = new DockerHelper(dockerHost).getContainerIp(GRPC_STUB_CONTAINER_NAME)
            new SwitchDto(NON_EXISTENT_SWITCH_ID, grpcStubIp,37040, "host", "desc",  SwitchChangeType.ACTIVATED,
                    false, "of_version", "manufacturer", "hardware", "software", "serial_number") as List
        } else {
            northbound.activeSwitches.findAll {
                def matcher = it.description =~ /NW[0-9]+.([0-9].[0-9])/
                return matcher && matcher[0][1] >= "2.7"
            }.unique { [it.hardware, it.software] }
        }
    }
}
