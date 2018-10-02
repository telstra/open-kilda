package org.openkilda.wfm.converters;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.model.Flow;
import org.openkilda.model.Node;
import org.openkilda.model.Path;
import org.openkilda.model.FlowPair;

@Mapper(uses=SwitchIdMapper.class)
public interface FlowMapper {

    Node pathNodeToDB(PathNode p);

    PathNode pathToMessaging(Node p);

    @Mapping(source = "nodes", target = "path")
    PathInfoData pathToMessaging(Path p);

    @Mapping(source = "path", target = "nodes")
    Path pathInfoDataToDB(PathInfoData p);

    @Mapping(source = "srcPort", target = "sourcePort")
    @Mapping(source = "srcVlan", target = "sourceVlan")
    @Mapping(source = "destPort", target = "destinationPort")
    @Mapping(source = "destVlan", target = "destinationVlan")
    @Mapping(source = "srcSwitchId", target = "sourceSwitch")
    @Mapping(source = "destSwitchId", target = "destinationSwitch")
    org.openkilda.messaging.model.Flow flowToMessaging(Flow flow);

    @Mapping(source = "sourcePort", target = "srcPort")
    @Mapping(source = "sourceVlan", target = "srcVlan")
    @Mapping(source = "destinationPort", target = "destPort")
    @Mapping(source = "destinationVlan", target = "destVlan")
    @Mapping(source = "sourceSwitch", target = "srcSwitchId")
    @Mapping(source = "destinationSwitch", target = "destSwitchId")
    Flow flowToDB(org.openkilda.messaging.model.Flow flow);


    default org.openkilda.messaging.model.FlowPair flowPairToMessaging(FlowPair flowPair) {
        return new org.openkilda.messaging.model.FlowPair(flowToMessaging(flowPair.getForward()),
                flowToMessaging(flowPair.getReverse()));
    }

    default FlowPair flowPairToDB(org.openkilda.messaging.model.FlowPair<org.openkilda.messaging.model.Flow, org.openkilda.messaging.model.Flow> flowPair) {
        return new FlowPair(flowToDB(flowPair.getLeft()), flowToDB(flowPair.getRight()));
    }
}
