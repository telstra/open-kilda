package org.openkilda.pce.api;

import org.neo4j.driver.v1.Record;
import org.openkilda.messaging.Utils;
import org.openkilda.messaging.info.event.PathInfoData;
import org.openkilda.messaging.model.Flow;
import org.openkilda.messaging.payload.flow.FlowState;

import java.io.IOException;

public class FlowAdapter {
    private final Flow flow;

    public FlowAdapter(Record dbRecord) {
        String pathJson = dbRecord.get("path").asString();
        if (pathJson.equals("null")){
            pathJson = "\"{\"path\": [], \"latency_ns\": 0, \"timestamp\": 0}\"";
        }

        /*
         * The 'clazz' value is stripped when storing in the database, but we need it in the string
         * in order for MAPPER to do its thing.  So, let's add it back in at the very beginning.
         */
        String remaining = pathJson.substring(2);
        PathInfoData path;
        pathJson = "{\"clazz\":\"org.openkilda.messaging.info.event.PathInfoData\", " + remaining;
        try {
            path = Utils.MAPPER.readValue(pathJson, PathInfoData.class);
        } catch (IOException e) {
            throw new IllegalArgumentException(String.format(
                    "Can\'t deserialize flow path: json=%s", pathJson), e);
        }

        flow = new Flow(
                dbRecord.get(Utils.FLOW_ID).asString(),
                dbRecord.get("bandwidth").asInt(),
                dbRecord.get("ignore_bandwidth").asBoolean(),
                dbRecord.get("cookie").asLong(),
                dbRecord.get("description").asString(),
                dbRecord.get("last_updated").asString(),
                dbRecord.get("src_switch").asString(),
                dbRecord.get("dst_switch").asString(),
                dbRecord.get("src_port").asInt(),
                dbRecord.get("dst_port").asInt(),
                dbRecord.get("src_vlan").asInt(),
                dbRecord.get("dst_vlan").asInt(),
                dbRecord.get("meter_id").asInt(),
                dbRecord.get("transit_vlan").asInt(),
                path, FlowState.CACHED
        );
    }

    public Flow getFlow() {
        return flow;
    }
}
