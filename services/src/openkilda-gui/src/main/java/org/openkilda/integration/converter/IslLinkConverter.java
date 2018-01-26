package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.response.IslLink;
import org.openkilda.integration.model.response.IslPath;
import org.openkilda.model.IslLinkInfo;

public final class IslLinkConverter {

    private IslLinkConverter() {
    }

    public static List<IslLinkInfo> toIslLinksInfo(final List<IslLink> islLinks) {
        if(islLinks != null) {
            final List<IslLinkInfo> islLinkInfos = new ArrayList<>();
            islLinks.forEach(islLink -> {
                IslLinkInfo islLinkInfo = toIslLinkInfo(islLink);

                boolean isSwitchRelationAdd = false;

                if (islLinkInfos.size() == 0) {
                    islLinkInfos.add(islLinkInfo);
                } else {
                    for (int i = 0; i < islLinkInfos.size(); i++) {
                        IslLinkInfo flowInfo = islLinkInfos.get(i);
                        if (flowInfo.getDstPort() == islLinkInfo.getSrcPort()
                                && flowInfo.getDstSwitch().equalsIgnoreCase(islLinkInfo.getSrcSwitch())
                                && flowInfo.getSrcPort() == islLinkInfo.getDstPort()
                                && flowInfo.getSrcSwitch().equalsIgnoreCase(islLinkInfo.getDstSwitch())) {
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
            });
            return islLinkInfos;
        }
        return null;
    }

    public static IslLinkInfo toIslLinkInfo(final IslLink islLink) {
        IslLinkInfo islLinkInfo = new IslLinkInfo();
        islLinkInfo.setAvailableBandwidth(islLink.getAvailableBandwidth());
        islLinkInfo.setSpeed(islLink.getSpeed());

        List<IslPath> islPaths = islLink.getPath();
        if (islPaths != null && !islPaths.isEmpty()) {
            if (islPaths.get(0) != null) {
                islLinkInfo.setSrcPort(islPaths.get(0).getPortNo());
                islLinkInfo.setSrcSwitch(islPaths.get(0).getSwitchId());
            }
            if (islPaths.get(1) != null) {
                islLinkInfo.setDstPort(islPaths.get(1).getPortNo());
                islLinkInfo.setDstSwitch(islPaths.get(1).getSwitchId());
            }
        }
        return islLinkInfo;
    }
}
