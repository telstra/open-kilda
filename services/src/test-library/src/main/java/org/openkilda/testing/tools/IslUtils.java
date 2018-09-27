/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.testing.tools;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.hasProperty;
import static org.hamcrest.core.Every.everyItem;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.testing.model.topology.TopologyDefinition;
import org.openkilda.testing.model.topology.TopologyDefinition.ASwitch;
import org.openkilda.testing.model.topology.TopologyDefinition.Isl;
import org.openkilda.testing.service.database.Database;
import org.openkilda.testing.service.lockkeeper.LockKeeperService;
import org.openkilda.testing.service.lockkeeper.model.ASwitchFlow;
import org.openkilda.testing.service.northbound.NorthboundService;

import net.jodah.failsafe.Failsafe;
import net.jodah.failsafe.RetryPolicy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Component
public class IslUtils {

    @Autowired
    private NorthboundService northbound;

    @Autowired
    private LockKeeperService lockKeeperService;

    @Autowired
    private Database db;

    /**
     * Waits until all passed isls have the specified status. Fails after defined timeout.
     * Checks happen via Northbound API calls
     *
     * @param isls which isls should have the specified status
     * @param expectedStatus which status to wait on specified isls
     */
    public void waitForIslStatus(List<Isl> isls, IslChangeType expectedStatus, RetryPolicy retryPolicy) {
        List<IslInfoData> actualIsl = Failsafe.with(retryPolicy
                .retryIf(states -> states != null && ((List<IslInfoData>) states).stream()
                        .map(IslInfoData::getState)
                        .anyMatch(state -> !expectedStatus.equals(state))))
                .get(() -> {
                    List<IslInfoData> allLinks = northbound.getAllLinks();
                    return isls.stream().map(isl -> getIslInfo(allLinks, isl).get()).collect(Collectors.toList());
                });

        assertThat(actualIsl, everyItem(hasProperty("state", equalTo(expectedStatus))));
    }

    public void waitForIslStatus(List<Isl> isls, IslChangeType expectedStatus) {
        waitForIslStatus(isls, expectedStatus, retryPolicy());
    }

    /**
     * Gets actual Northbound represenation of the certain ISL.
     *
     * @param isl isl to search in 'getAllLinks' results
     */
    public Optional<IslInfoData> getIslInfo(Isl isl) {
        return getIslInfo(northbound.getAllLinks(), isl);
    }

    /**
     * Finds certain ISL in list of 'IslInfoData' objects. Passed ISL is our internal ISL representation, while
     * IslInfoData is returned from NB.
     *
     * @param islsInfo list where to search certain ISL
     * @param isl what isl to look for
     */
    public Optional<IslInfoData> getIslInfo(List<IslInfoData> islsInfo, Isl isl) {
        return islsInfo.stream().filter(link -> {
            PathNode src = link.getPath().get(0);
            PathNode dst = link.getPath().get(1);
            return src.getPortNo() == isl.getSrcPort() && dst.getPortNo() == isl.getDstPort()
                    && src.getSwitchId().equals(isl.getSrcSwitch().getDpId())
                    && dst.getSwitchId().equals(isl.getDstSwitch().getDpId());
        }).findFirst();
    }

    /**
     * Returns 'reverse' version of the passed ISL.
     *
     * @param isl isl to reverse
     */
    public Isl reverseIsl(Isl isl) {
        if (isl.getDstSwitch() == null) {
            return isl; //don't reverse not connected ISL
        }
        ASwitch reversedAsw = null;
        if (isl.getAswitch() != null) {
            reversedAsw = ASwitch.factory(isl.getAswitch().getOutPort(), isl.getAswitch().getInPort());
        }
        return Isl.factory(isl.getDstSwitch(), isl.getDstPort(), isl.getSrcSwitch(),
                isl.getSrcPort(), isl.getMaxBandwidth(), reversedAsw);
    }

    /**
     * Simulates a physical ISL replug from one switch-port to another switch-port. Uses a-switch.
     *
     * @param srcIsl The initial ISL which is going to be replugged. Should go through a-switch!
     * @param replugSource replug source or destination end of the ISL
     * @param dstIsl The destination 'isl'. Usually a free link, which is connected to a-switch at one end
     * @param plugIntoSource Whether to connect to src or dst end of the dstIsl. Usually src end for not-connected ISLs
     * @return New ISL which is expected to be discovered after the replug.
     */
    public TopologyDefinition.Isl replug(TopologyDefinition.Isl srcIsl, boolean replugSource,
                                         TopologyDefinition.Isl dstIsl, boolean plugIntoSource) {
        ASwitch srcASwitch = srcIsl.getAswitch();
        ASwitch dstASwitch = dstIsl.getAswitch();
        //unplug
        List<Integer> portsToUnplug = Collections.singletonList(
                replugSource ? srcASwitch.getInPort() : srcASwitch.getOutPort());
        lockKeeperService.portsDown(portsToUnplug);

        //change flow on aSwitch
        //delete old flow
        if (srcASwitch.getInPort() != null && srcASwitch.getOutPort() != null) {
            lockKeeperService.removeFlows(Arrays.asList(
                    new ASwitchFlow(srcASwitch.getInPort(), srcASwitch.getOutPort()),
                    new ASwitchFlow(srcASwitch.getOutPort(), srcASwitch.getInPort())));
        }
        //create new flow
        ASwitchFlow aswFlowForward = new ASwitchFlow(srcASwitch.getInPort(),
                plugIntoSource ? dstASwitch.getInPort() : dstASwitch.getOutPort());
        ASwitchFlow aswFlowReverse = new ASwitchFlow(aswFlowForward.getOutPort(), aswFlowForward.getInPort());
        lockKeeperService.addFlows(Arrays.asList(aswFlowForward, aswFlowReverse));

        //plug back
        lockKeeperService.portsUp(portsToUnplug);

        return TopologyDefinition.Isl.factory(
                replugSource ? (plugIntoSource ? dstIsl.getSrcSwitch() : dstIsl.getDstSwitch()) : srcIsl.getSrcSwitch(),
                replugSource ? (plugIntoSource ? dstIsl.getSrcPort() : dstIsl.getDstPort()) : srcIsl.getSrcPort(),
                replugSource ? srcIsl.getDstSwitch() : (plugIntoSource ? dstIsl.getSrcSwitch() : dstIsl.getDstSwitch()),
                replugSource ? srcIsl.getDstPort() : (plugIntoSource ? dstIsl.getSrcPort() : dstIsl.getDstPort()),
                0,
                new TopologyDefinition.ASwitch(aswFlowForward.getInPort(), aswFlowForward.getOutPort()));
    }

    /**
     * Get cost of a certain ISL from DB.
     *
     * @param isl ISL for which cost should be retrieved
     * @return ISL cost
     */
    public int getIslCost(Isl isl) {
        return db.getIslCost(isl);
    }

    private RetryPolicy retryPolicy() {
        return new RetryPolicy()
                .withDelay(3, TimeUnit.SECONDS)
                .withMaxRetries(15);
    }
}
