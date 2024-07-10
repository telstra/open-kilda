package org.openkilda.functionaltests.helpers

import static org.openkilda.functionaltests.model.cleanup.CleanupActionType.DELETE_FLOW
import static org.openkilda.testing.Constants.FLOW_CRUD_TIMEOUT
import static org.springframework.beans.factory.config.ConfigurableBeanFactory.SCOPE_PROTOTYPE

import org.openkilda.functionaltests.helpers.model.SwitchPair
import org.openkilda.functionaltests.model.cleanup.CleanupAfter
import org.openkilda.functionaltests.model.cleanup.CleanupManager
import org.openkilda.messaging.payload.flow.DetectConnectedDevicesPayload
import org.openkilda.messaging.payload.flow.FlowCreatePayload
import org.openkilda.messaging.payload.flow.FlowEndpointPayload
import org.openkilda.messaging.payload.flow.FlowPayload
import org.openkilda.messaging.payload.flow.FlowState
import org.openkilda.testing.model.topology.TopologyDefinition
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.database.Database
import org.openkilda.testing.service.northbound.NorthboundService

import com.github.javafaker.Faker
import groovy.util.logging.Slf4j
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.beans.factory.annotation.Qualifier
import org.springframework.context.annotation.Scope
import org.springframework.stereotype.Component

import java.text.SimpleDateFormat

/**
 * Holds utility methods for manipulating flows.
 */
@Component
@Slf4j
@Scope(SCOPE_PROTOTYPE)
class FlowHelper {
    @Autowired
    TopologyDefinition topology
    @Autowired @Qualifier("islandNb")
    NorthboundService northbound
    @Autowired
    Database db
    @Autowired
    FlowHelperV2 flowHelperV2
    @Autowired
    CleanupManager cleanupManager

    def random = new Random()
    def faker = new Faker()
    //Kilda allows user to pass reserved VLAN IDs 1 and 4095 if they want.
    static final IntRange KILDA_ALLOWED_VLANS = 1..4095

    /**
     * Creates a FlowCreatePayload instance with random vlan and flow id. Will try to build over traffgen ports or use
     * random port otherwise.
     * Since multi-switch and single-switch flows have a bit different algorithms to create a correct flow, this method
     * will delegate the job to the correct algo based on src and dst switches passed.
     */
    FlowCreatePayload randomFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true,
                                 List<FlowPayload> existingFlows = []) {
        if (srcSwitch.dpId == dstSwitch.dpId) {
            return singleSwitchFlow(srcSwitch, useTraffgenPorts, existingFlows)
        } else {
            return randomMultiSwitchFlow(srcSwitch, dstSwitch, useTraffgenPorts, existingFlows)
        }
    }

    FlowCreatePayload randomFlow(SwitchPair switchPair, boolean useTraffgenPorts = true,
                                 List<FlowPayload> existingFlows = []) {
        randomFlow(switchPair.src, switchPair.dst, useTraffgenPorts, existingFlows)
    }

    FlowCreatePayload randomMultiSwitchFlow(Switch srcSwitch, Switch dstSwitch, boolean useTraffgenPorts = true,
                                            List<FlowPayload> existingFlows = []) {
        Wrappers.retry(3, 0) {
            def newFlow = new FlowCreatePayload(generateFlowId(), getFlowEndpoint(srcSwitch, useTraffgenPorts),
                    getFlowEndpoint(dstSwitch, useTraffgenPorts), 500, false, false, false, generateDescription(),
                    null, null, null, null, null, null, false, null, null)
            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowCreatePayload
    }

    /**
     * Creates a FlowCreatePayload instance with random vlan and flow id suitable for a single-switch flow.
     * The flow will be on DIFFERENT PORTS. Will try to look for both ports to be traffgen ports.
     * But if such port is not available, will pick a random one. So in order to run a correct traffic
     * examination certain switch should have at least 2 traffgens connected to different ports.
     */
    FlowCreatePayload singleSwitchFlow(Switch sw, boolean useTraffgenPorts = true,
                                       List<FlowPayload> existingFlows = []) {
        Wrappers.retry(3, 0) {
            def srcEndpoint = getFlowEndpoint(sw, [], useTraffgenPorts)
            def dstEndpoint = getFlowEndpoint(sw, [srcEndpoint.portNumber], useTraffgenPorts)
            def newFlow = new FlowCreatePayload(generateFlowId(), srcEndpoint, dstEndpoint, 500, false, false, false,
                    generateDescription(), null, null, null, null, null, null, false, null, null)
            if (flowConflicts(newFlow, existingFlows)) {
                throw new Exception("Generated flow conflicts with existing flows. Flow: $newFlow")
            }
            return newFlow
        } as FlowCreatePayload
    }

    /**
     * Adds flow with checking flow status and rules on source and destination switches.
     * It is supposed if rules are installed on source and destination switches, the flow is completely created.
     */
    FlowPayload addFlow(FlowPayload flow) {
        log.debug("Adding flow '${flow.id}'")
        def flowId = flow.getId()
        cleanupManager.addAction(DELETE_FLOW, {flowHelperV2.safeDeleteFlow(flowId)}, CleanupAfter.TEST)
        def response = northbound.addFlow(flow)
        Wrappers.wait(FLOW_CRUD_TIMEOUT) { assert northbound.getFlowStatus(flow.id).status == FlowState.UP }
        return response
    }

    /**
     * Check whether given potential flow is conflicting with any of flows in the given list.
     * Usually used to ensure that some new flow is by accident is not conflicting with any of existing flows.
     * Verifies conflicts by flow id and by port-vlan conflict on source or destination switch.
     *
     * @param newFlow this flow will be validated against the passed list
     * @param existingFlows the passed flow will be validated against this list
     * @return true if passed flow conflicts with any of the flows in the list
     */
    static boolean flowConflicts(FlowPayload newFlow, List<FlowPayload> existingFlows) {
        List<FlowEndpointPayload> existingEndpoints = existingFlows.collectMany { [it.source, it.destination] }
        [newFlow.source, newFlow.destination].any { newEp ->
            existingEndpoints.find {
                newEp.datapath == it.datapath && newEp.portNumber == it.portNumber &&
                        (newEp.vlanId == it.vlanId || it.vlanId == 0 || newEp.vlanId == 0)
            }
        } || existingFlows*.id.contains(newFlow.id)
    }


    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param useTraffgenPorts whether to try finding a traffgen port
     */
    private FlowEndpointPayload getFlowEndpoint(Switch sw, boolean useTraffgenPorts = true) {
        getFlowEndpoint(sw, [], useTraffgenPorts)
    }

    /**
     * Returns flow endpoint with randomly chosen vlan.
     *
     * @param excludePorts list of ports that should not be picked
     * @param useTraffgenPorts if true, will try to use a port attached to a traffgen. The port must be present
     * in 'allowedPorts'
     */
    private FlowEndpointPayload getFlowEndpoint(Switch sw, List<Integer> excludePorts,
                                                boolean useTraffgenPorts = true) {
        def ports = topology.getAllowedPortsForSwitch(sw) - excludePorts
        int port = ports[random.nextInt(ports.size())]
        if (useTraffgenPorts) {
            List<Integer> tgPorts = sw.traffGens*.switchPort - excludePorts
            if (tgPorts) {
                port = tgPorts[0]
            }
        }
        return new FlowEndpointPayload(sw.dpId, port,
                KILDA_ALLOWED_VLANS.shuffled().first(),
                new DetectConnectedDevicesPayload(false, false))
    }

    /**
     * Generates a unique name for all auto-tests flows.
     */
    private String generateFlowId() {
        return new SimpleDateFormat("ddMMMHHmmss_SSS", Locale.US).format(new Date()) + "_" +
                faker.food().ingredient().toLowerCase().replaceAll(/\W/, "") + faker.number().digits(4)
    }

    private String generateDescription() {
        //The health of autotest flows is always questionable
        def descpription = [faker.shakespeare().asYouLikeItQuote(),
                            faker.shakespeare().kingRichardIIIQuote(),
                            faker.shakespeare().romeoAndJulietQuote(),
                            faker.shakespeare().hamletQuote()]
        def r = new Random()
        "autotest flow: ${descpription[r.nextInt(descpription.size())]}"
    }
}
