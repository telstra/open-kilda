package org.openkilda.functionaltests.helpers.model


import groovy.transform.AutoClone
import groovy.transform.EqualsAndHashCode
import groovy.transform.ToString
import groovy.transform.builder.Builder
import groovy.util.logging.Slf4j
import org.openkilda.model.FlowStatus
import org.openkilda.model.SwitchId

import java.time.Instant

/* This class represents any kind of interactions with HA flow
 */

@Slf4j
@EqualsAndHashCode(excludes = "status")
@Builder
@AutoClone
@ToString
class HaSubFlowExtended {
    String haSubFlowId
    FlowStatus status

    SwitchId endpointSwitchId
    int endpointPort
    int endpointVlan
    int endpointInnerVlan
    String description

    String timeCreate
    String timeModify
}
