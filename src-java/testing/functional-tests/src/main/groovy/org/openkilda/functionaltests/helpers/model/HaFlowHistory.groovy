package org.openkilda.functionaltests.helpers.model

import org.openkilda.messaging.info.event.PathNode
import org.openkilda.messaging.payload.history.HaFlowHistoryEntry
import org.openkilda.testing.model.topology.TopologyDefinition.Switch
import org.openkilda.testing.service.northbound.NorthboundServiceV2

import com.fasterxml.jackson.annotation.JsonIgnore
import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor

@TupleConstructor
@EqualsAndHashCode
class HaFlowHistory {
    HaFlowHistory(List<HaFlowHistoryEntry> entries) {
        this.entries = entries
    }

    HaFlowHistory(NorthboundServiceV2 northboundServiceV2, List<HaFlowHistoryEntry> entries) {
        this.northboundServiceV2 = northboundServiceV2
        this.entries = entries
    }

    NorthboundServiceV2 northboundServiceV2;
    List<HaFlowHistoryEntry> entries;


    HaFlowHistoryEntry[] getUpdateEntries(){
        return entries.findAll({it ->it.action == "HA-Flow update"})
    }
    HaFlowHistoryEntry[] getCreateEntries(){
        return entries.findAll({it ->it.action == "HA-Flow create"})
    }




    boolean isContainingCreateEvent() {
        return false
    }

}
