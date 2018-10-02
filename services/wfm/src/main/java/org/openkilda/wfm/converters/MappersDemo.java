package org.openkilda.wfm.converters;

import org.mapstruct.factory.Mappers;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.model.FlowPair;
import org.openkilda.messaging.model.SwitchId;

import java.util.ArrayList;
import java.util.List;

public class MappersDemo {
    private static final FlowMapper SWITCH_ID_MAPPER = Mappers.getMapper(FlowMapper.class);

    public static void main(String[] args) {

        Flow flow = new Flow();
        PathInfoData pathInfoData = new PathInfoData();
        pathInfoData.setLatency(1L);
        List<PathNode> path = new ArrayList<>();
        PathNode pathNode = new PathNode(new SwitchId(1), 1, 1, 1L, 1L);
        path.add(pathNode);
        pathNode = new PathNode(new SwitchId(2), 2, 2, 2L, 2L);
        path.add(pathNode);
        pathInfoData.setPath(path);
        flow.setFlowPath(pathInfoData);
        flow.setFlowId("12");
        flow.setCookie(11);
        flow.setSourcePort(113);
        flow.setSourceVlan(1112);
        flow.setDestinationPort(113);
        flow.setDestinationVlan(1112);
        flow.setBandwidth(23);
        flow.setDescription("SOME FLOW");
        flow.setLastUpdated("SOME LAST UPDATED FLOW");
        flow.setTransitVlan(87);
        flow.setMeterId(65);
        flow.setIgnoreBandwidth(true);
        flow.setPeriodicPings(true);
        FlowPair<Flow, Flow> pair = new FlowPair<>(flow, flow);
        org.openkilda.model.FlowPair p =  SWITCH_ID_MAPPER.flowPairToDB(pair);

        System.out.print(path);
    }
}
