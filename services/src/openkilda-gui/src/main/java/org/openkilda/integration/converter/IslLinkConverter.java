package org.openkilda.integration.converter;

import java.util.ArrayList;
import java.util.List;

import org.openkilda.integration.model.response.LinkResponse;
import org.openkilda.integration.model.response.PathNode;
import org.openkilda.model.IslLinkInfo;
import org.openkilda.utility.CollectionUtil;

public final class IslLinkConverter {

    private IslLinkConverter() {
    }

    public static List<IslLinkInfo> toIslLinksInfo(final List<LinkResponse> linksResponse) {
        if(!CollectionUtil.isEmpty(linksResponse)) {
            final List<IslLinkInfo> islLinkInfos = new ArrayList<>();
            linksResponse.forEach(linkResponse -> {
                IslLinkInfo islLinkInfo = toIslLinkInfo(linkResponse);

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

    public static IslLinkInfo toIslLinkInfo(final LinkResponse linkResponse) {
        IslLinkInfo islLinkInfo = new IslLinkInfo();
        islLinkInfo.setAvailableBandwidth(linkResponse.getAvailableBandwidth());
        islLinkInfo.setLatency(linkResponse.getLatencyNs());
        islLinkInfo.setSpeed(linkResponse.getSpeed());
        islLinkInfo.setState(linkResponse.getState());

        List<PathNode> pathList = linkResponse.getPath();
        if (pathList != null && !pathList.isEmpty()) {
            if (pathList.get(0) != null) {
                islLinkInfo.setSrcPort(pathList.get(0).getPortNo());
                islLinkInfo.setSrcSwitch(pathList.get(0).getSwitchId());
            }
            if (pathList.get(1) != null) {
                islLinkInfo.setDstPort(pathList.get(1).getPortNo());
                islLinkInfo.setDstSwitch(pathList.get(1).getSwitchId());
            }
        }
        return islLinkInfo;
    }
}
