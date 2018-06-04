package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.IslPath;
import org.openkilda.integration.service.SwitchIntegrationService;
import org.openkilda.model.IslLinkInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class IslLinkConverter {

    @Autowired
    private SwitchIntegrationService switchIntegrationService;

    public List<IslLinkInfo> toIslLinksInfo(final List<IslLink> islLinks,
            Map<String, String> islCostMap) {
        if (islLinks != null) {
            final List<IslLinkInfo> islLinkInfos = new ArrayList<>();
            final Map<String, String> csNames =
                    switchIntegrationService.getCustomSwitchNameFromFile();
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
                islLinkInfo.setSrcSwitchName(switchIntegrationService.customSwitchName(csNames,
                        islPaths.get(0).getSwitchId()));
                if (islPaths.get(0).getSegmentLatency() > 0) {
                    islLinkInfo.setLatency(islPaths.get(0).getSegmentLatency());
                }
            }
            if (islPaths.get(1) != null) {
                islLinkInfo.setDstPort(islPaths.get(1).getPortNo());
                islLinkInfo.setDstSwitch(islPaths.get(1).getSwitchId());
                islLinkInfo.setDstSwitchName(switchIntegrationService.customSwitchName(csNames,
                        islPaths.get(1).getSwitchId()));
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
