package org.openkilda.functionaltests.helpers.model

import org.openkilda.testing.service.northbound.NorthboundServiceV2

import groovy.transform.EqualsAndHashCode
import groovy.transform.TupleConstructor
import org.openkilda.testing.service.northbound.model.HaFlowHistoryEntry

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
        return entries.findAll({it ->it.payloads.action == "HA-flow has been created successfully"})
    }




    boolean isContainingCreateEvent() {
        return false
    }

}
