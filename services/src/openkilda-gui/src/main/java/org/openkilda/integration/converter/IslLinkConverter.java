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

package org.openkilda.integration.converter;

import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.IslPath;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.IslLinkInfo;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * The Class IslLinkConverter.
 */

@Component
public class IslLinkConverter {

    /** The switch integration service. */
    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    /**
     * To isl links info.
     *
     * @param islLinks the isl links
     * @param islCostMap the isl cost map
     * @return the list
     */
    public List<IslLinkInfo> toIslLinksInfo(final List<IslLink> islLinks, Map<String, String> islCostMap) {
        if (islLinks != null) {
            final List<IslLinkInfo> islLinkInfos = new ArrayList<>();
            final Map<String, String> csNames = switchIntegrationService.getSwitchNames();
            islLinks.forEach(islLink -> {

                IslLinkInfo islLinkInfo = toIslLinkInfo(islLink, csNames, islCostMap);

                if (islLinkInfos.size() == 0) {
                    islLinkInfos.add(islLinkInfo);
                } else {
                    boolean isSwitchRelationAdd = true;
                    for (IslLinkInfo flowInfo : islLinkInfos) {
                        if (islLinkInfo.getForwardKey().equalsIgnoreCase(flowInfo.getReverseKey())) {
                            isSwitchRelationAdd = false;
                            flowInfo.setUnidirectional(false);
                            flowInfo.setState1(islLinkInfo.getState());
                            if (!flowInfo.getState().equalsIgnoreCase(islLinkInfo.getState())) {
                                flowInfo.setAffected(true);
                            }
                            break;
                        }
                    }
                    if (isSwitchRelationAdd) {
                        islLinkInfos.add(islLinkInfo);
                    }
                }
            });
            return islLinkInfos;
        }
        return null;
    }

    /**
     * To isl link info.
     *
     * @param islLink the isl link
     * @param csNames the cs names
     * @param islCostMap the isl cost map
     * @return the isl link info
     */
    private IslLinkInfo toIslLinkInfo(final IslLink islLink, final Map<String, String> csNames,
            Map<String, String> islCostMap) {
        IslLinkInfo islLinkInfo = new IslLinkInfo();
        islLinkInfo.setUnidirectional(true);
        islLinkInfo.setAvailableBandwidth(islLink.getAvailableBandwidth());
        islLinkInfo.setSpeed(islLink.getSpeed());
        islLinkInfo.setState(islLink.getState());
        List<IslPath> islPaths = islLink.getPath();
        if (islPaths != null && !islPaths.isEmpty()) {
            if (islPaths.get(0) != null) {
                islLinkInfo.setSrcPort(islPaths.get(0).getPortNo());
                islLinkInfo.setSrcSwitch(islPaths.get(0).getSwitchId());
                islLinkInfo.setSrcSwitchName(
                        switchIntegrationService.customSwitchName(csNames, islPaths.get(0).getSwitchId()));
                if (islPaths.get(0).getSegmentLatency() > 0) {
                    islLinkInfo.setLatency(islPaths.get(0).getSegmentLatency());
                }
            }
            if (islPaths.get(1) != null) {
                islLinkInfo.setDstPort(islPaths.get(1).getPortNo());
                islLinkInfo.setDstSwitch(islPaths.get(1).getSwitchId());
                islLinkInfo.setDstSwitchName(
                        switchIntegrationService.customSwitchName(csNames, islPaths.get(1).getSwitchId()));
                if (islPaths.get(1).getSegmentLatency() > 0) {
                    islLinkInfo.setLatency(islPaths.get(1).getSegmentLatency());
                }
            }
        }
        // set isl cost
        if (islCostMap.containsKey(islLinkInfo.getForwardKey())) {
            islLinkInfo.setCost(islCostMap.get(islLinkInfo.getForwardKey()));
        } else if (islCostMap.containsKey(islLinkInfo.getReverseKey())) {
            islLinkInfo.setCost(islCostMap.get(islLinkInfo.getReverseKey()));
        }
        return islLinkInfo;
    }
}
