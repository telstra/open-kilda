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

package org.openkilda.floodlight.command.group;

import static java.util.stream.Collectors.toList;

import org.openkilda.floodlight.command.SpeakerCommandProcessor;
import org.openkilda.floodlight.converter.OfFlowStatsMapper;
import org.openkilda.floodlight.error.SwitchIncorrectMirrorGroupException;
import org.openkilda.floodlight.error.SwitchMissingGroupException;
import org.openkilda.floodlight.error.UnsupportedSwitchOperationException;
import org.openkilda.floodlight.model.FlowTransitData;
import org.openkilda.floodlight.utils.CompletableFutureAdapter;
import org.openkilda.messaging.MessageContext;
import org.openkilda.messaging.info.rule.GroupBucket;
import org.openkilda.messaging.info.rule.GroupEntry;
import org.openkilda.model.GroupId;
import org.openkilda.model.MacAddress;
import org.openkilda.model.MirrorConfig;
import org.openkilda.model.MirrorConfig.MirrorConfigData;
import org.openkilda.model.MirrorConfig.PushVxlan;
import org.openkilda.model.SwitchId;

import org.projectfloodlight.openflow.protocol.OFBucket;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsEntry;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsReply;
import org.projectfloodlight.openflow.protocol.OFGroupDescStatsRequest;
import org.projectfloodlight.openflow.protocol.match.MatchField;
import org.projectfloodlight.openflow.types.DatapathId;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;

public class GroupVerifyCommand extends AbstractGroupInstall<GroupVerifyReport> {
    public GroupVerifyCommand(MessageContext messageContext, SwitchId switchId, MirrorConfig mirrorConfig,
                              FlowTransitData flowTransitData) {
        super(messageContext, switchId, mirrorConfig, flowTransitData);
    }

    @Override
    protected CompletableFuture<GroupVerifyReport> makeExecutePlan(SpeakerCommandProcessor commandProcessor)
            throws UnsupportedSwitchOperationException {
        ensureSwitchSupportGroups();

        return new CompletableFutureAdapter<>(
                messageContext, getSw().writeStatsRequest(makeGroupReadCommand()))
                .thenApply(this::handleGroupStats)
                .thenApply(this::makeSuccessReport);
    }

    @Override
    protected GroupVerifyReport makeReport(Exception error) {
        return new GroupVerifyReport(this, error);
    }

    private GroupVerifyReport makeSuccessReport(MirrorConfig mirrorConfig) {
        return new GroupVerifyReport(this, mirrorConfig);
    }

    private OFGroupDescStatsRequest makeGroupReadCommand() {
        return getSw().getOFFactory().buildGroupDescStatsRequest().build();
    }

    private MirrorConfig handleGroupStats(List<OFGroupDescStatsReply> groupDescStatsReply) {
        OFGroupDescStatsEntry target = null;
        List<OFGroupDescStatsEntry> groupDescStatsEntries = groupDescStatsReply.stream()
                .map(OFGroupDescStatsReply::getEntries)
                .flatMap(List::stream)
                .collect(toList());
        for (OFGroupDescStatsEntry entry : groupDescStatsEntries) {

            if (mirrorConfig.getGroupId().intValue() == entry.getGroup().getGroupNumber()) {
                target = entry;
                break;
            }
        }

        if (target == null) {
            throw maskCallbackException(new SwitchMissingGroupException(getSw().getId(), mirrorConfig.getGroupId()));
        }

        validateGroupConfig(target);

        return mirrorConfig;
    }

    private void validateGroupConfig(OFGroupDescStatsEntry group) {
        DatapathId datapathId = getSw().getId();
        OFFactory ofFactory = getSw().getOFFactory();
        List<OFBucket> expected = buildGroupOfBuckets(ofFactory);
        List<OFBucket> actual = group.getBuckets();
        if (!expected.equals(actual)) {
            throw maskCallbackException(new SwitchIncorrectMirrorGroupException(
                    datapathId, mirrorConfig, fromStatsEntry(group)));
        }
    }

    private MirrorConfig fromStatsEntry(OFGroupDescStatsEntry entry) {
        GroupEntry groupEntry = OfFlowStatsMapper.INSTANCE.toFlowGroupEntry(entry);
        GroupId groupId = new GroupId(groupEntry.getGroupId());
        if (groupEntry.getBuckets().size() < 2) {
            return null;
        }

        GroupBucket flowBucket = groupEntry.getBuckets().get(0);
        int flowPort = Integer.parseInt(flowBucket.getApplyActions().getFlowOutput());

        Set<MirrorConfigData> mirrorConfigDataSet = new HashSet<>();
        for (int i = 1; i < groupEntry.getBuckets().size(); ++i) {
            GroupBucket mirrorBucket = groupEntry.getBuckets().get(i);

            int mirrorPort = Integer.parseInt(mirrorBucket.getApplyActions().getFlowOutput());
            Integer mirrorVlan = mirrorBucket.getApplyActions().getSetFieldActions().stream().filter(
                    action -> (MatchField.VLAN_VID.getName().equals(action.getFieldName())))
                    .map(action -> Integer.valueOf(action.getFieldValue())).findFirst().orElse(null);

            PushVxlan pushVxlan = getPushVxlan(mirrorBucket.getApplyActions().getPushVxlan());
            mirrorConfigDataSet.add(new MirrorConfigData(mirrorPort, mirrorVlan, pushVxlan));
        }

        return MirrorConfig.builder().groupId(groupId).flowPort(flowPort)
                .mirrorConfigDataSet(mirrorConfigDataSet).build();
    }

    private PushVxlan getPushVxlan(String pushAsString) {
        if (pushAsString == null || pushAsString.isEmpty()) {
            return null;
        }
        String[] split = pushAsString.split(",");
        return new PushVxlan(Integer.parseInt(split[0]), new MacAddress(split[1]));
    }
}
