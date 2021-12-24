/* Copyright 2021 Telstra Open Source
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

package org.openkilda.floodlight.command.rulemanager;

import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;

import net.floodlightcontroller.core.IOFSwitch;
import org.projectfloodlight.openflow.protocol.OFFlowStatsReply;
import org.projectfloodlight.openflow.protocol.OFFlowStatsRequest;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsRequest;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsReply;
import org.projectfloodlight.openflow.protocol.OFMeterConfigStatsRequest;
import org.projectfloodlight.openflow.types.OFGroup;

import java.util.List;
import java.util.concurrent.CompletableFuture;

public final class OfUtils {

    private OfUtils() {}

    /**
     * Run verify meters.
     * @param messageContext message context
     * @param sw target switch
     * @return future with stats reply
     */
    public static CompletableFuture<List<OFMeterConfigStatsReply>> verifyMeters(MessageContext messageContext,
                                                                                IOFSwitch sw) {

        return new CompletableFutureAdapter<>(
                messageContext, sw.writeStatsRequest(makeMeterReadCommand(sw)));
    }

    private static OFMeterConfigStatsRequest makeMeterReadCommand(IOFSwitch sw) {
        return sw.getOFFactory().buildMeterConfigStatsRequest()
                .setMeterId(0xffffffff)
                .build();
    }


    /**
     * Run verify groups.
     * @param messageContext message context
     * @param sw target switch
     * @return future with stats reply
     */
    public static CompletableFuture<List<OFGroupDescStatsReply>> verifyGroups(MessageContext messageContext,
                                                                              IOFSwitch sw) {

        return new CompletableFutureAdapter<>(
                messageContext, sw.writeStatsRequest(makeGroupReadCommand(sw)));
    }

    private static OFGroupDescStatsRequest makeGroupReadCommand(IOFSwitch sw) {
        return sw.getOFFactory().buildGroupDescStatsRequest().build();
    }

    public static CompletableFuture<List<OFFlowStatsReply>> verifyFlows(MessageContext messageContext,
                                                                              IOFSwitch sw) {
        return new CompletableFutureAdapter<>(
                messageContext, sw.writeStatsRequest(makeFlowStatsCommand(sw)));
    }

    private static OFFlowStatsRequest makeFlowStatsCommand(IOFSwitch sw) {
        OFFlowStatsRequest.Builder request = sw.getOFFactory().buildFlowStatsRequest()
                .setOutGroup(OFGroup.ANY);
        return request.build();
    }



}
