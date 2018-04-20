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

    public List<IslLinkInfo> toIslLinksInfo(final List<IslLink> islLinks, Map<String,String> islCostMap) {
        if (islLinks != null) {
            final List<IslLinkInfo> islLinkInfos = new ArrayList<>();
            final Map<String, String> csNames =
                    switchIntegrationService.getCustomSwitchNameFromFile();
            islLinks.forEach(islLink -> {

                IslLinkInfo islLinkInfo = toIslLinkInfo(islLink, csNames, islCostMap);

                if (islLinkInfos.contains(islLinkInfo)) {
                    islLinkInfos.get(islLinkInfos.indexOf(islLinkInfo)).setUnidirectional(false);
                } else {

                    boolean isSwitchRelationAdd = false;

                    if (islLinkInfos.size() == 0) {
                        islLinkInfos.add(islLinkInfo);
                    } else {
                        for (int i = 0; i < islLinkInfos.size(); i++) {
                            IslLinkInfo flowInfo = islLinkInfos.get(i);
                            if (flowInfo.getDstPort() == islLinkInfo.getSrcPort()
                                    && flowInfo.getDstSwitch()
                                            .equalsIgnoreCase(islLinkInfo.getSrcSwitch())
                                    && flowInfo.getSrcPort() == islLinkInfo.getDstPort()
                                    && flowInfo.getSrcSwitch()
                                            .equalsIgnoreCase(islLinkInfo.getDstSwitch())) {
                                isSwitchRelationAdd = false;
                                break;
                            } else {
                                isSwitchRelationAdd = true;

                            }
                        }
                        if (isSwitchRelationAdd) {
                            islLinkInfos.add(islLinkInfo);
                        }
                    }
                }
            });
            return islLinkInfos;
        }
        return null;
    }

    private IslLinkInfo toIslLinkInfo(final IslLink islLink, final Map<String, String> csNames, Map<String,String> islCostMap) {
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
        String key1 = islLinkInfo.getSrcSwitch()+ "-"+ islLinkInfo.getSrcPort()+"-"+islLinkInfo.getDstSwitch() + "-" + islLinkInfo.getDstPort();
        String key2 = islLinkInfo.getDstSwitch()+ "-"+ islLinkInfo.getDstPort()+"-"+islLinkInfo.getSrcSwitch() + "-" + islLinkInfo.getSrcPort();
        if(islCostMap.containsKey(key1)) {
            islLinkInfo.setCost(islCostMap.get(key1));
        } else if (islCostMap.containsKey(key2)) {
            islLinkInfo.setCost(islCostMap.get(key2));
        }
        return islLinkInfo;
    }
}

