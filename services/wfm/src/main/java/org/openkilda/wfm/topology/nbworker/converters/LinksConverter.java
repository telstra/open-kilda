/* Copyright 2017 Telstra Open Source
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

package org.openkilda.wfm.topology.nbworker.converters;

import org.openkilda.messaging.info.event.IslChangeType;
import org.openkilda.messaging.info.event.IslInfoData;
import org.openkilda.messaging.info.event.PathNode;
import org.openkilda.messaging.model.NetworkEndpoint;
import org.openkilda.messaging.nbtopology.response.LinkPropsData;

import org.apache.commons.lang3.math.NumberUtils;
import org.neo4j.driver.v1.Value;
import org.neo4j.driver.v1.types.Node;
import org.neo4j.driver.v1.types.Relationship;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public final class LinksConverter {

    private static final String SRC_SWITCH_FIELD = "src_switch";
    private static final String SRC_PORT_FIELD = "src_port";
    private static final String DST_SWITCH_FIELD = "dst_switch";
    private static final String DST_PORT_FIELD = "dst_port";

    private static final Logger logger = LoggerFactory.getLogger(LinksConverter.class);

    /**
     * Transforms response from the database into {@link IslInfoData} message.
     * @param relationship received from the DB.
     * @return converted ISL.
     */
    public static IslInfoData toIslInfoData(Relationship relationship) {
        // max_bandwidth not used in IslInfoData
        PathNode src = new PathNode();
        src.setSwitchId(relationship.get(SRC_SWITCH_FIELD).asString());
        src.setPortNo(parseIntValue(relationship.get(SRC_PORT_FIELD)));
        src.setSegLatency(parseIntValue(relationship.get("latency")));
        src.setSeqId(0);

        PathNode dst = new PathNode();
        dst.setSwitchId(relationship.get(DST_SWITCH_FIELD).asString());
        dst.setPortNo(parseIntValue(relationship.get(DST_PORT_FIELD)));
        dst.setSegLatency(parseIntValue(relationship.get("latency")));
        dst.setSeqId(1);

        List<PathNode> pathNodes = new ArrayList<>();
        pathNodes.add(src);
        pathNodes.add(dst);

        IslChangeType state = getStatus(relationship.get("status").asString());
        IslInfoData isl = new IslInfoData(
                parseIntValue(relationship.get("latency")),
                pathNodes,
                parseIntValue(relationship.get("speed")),
                state,
                parseIntValue(relationship.get("available_bandwidth"))
        );
        isl.setTimestamp(System.currentTimeMillis());

        return isl;
    }

    /**
     * Converts props node into {@link LinkPropsData}. The main purpose of that node is to determine properties for
     * specific ISL. As we have "dynamic" properties there - we store them in map.
     * @param node link props node from the database.
     * @return converted link props item.
     */
    public static LinkPropsData toLinkPropsData(Node node) {
        // create mutable map from immutable returned by neo4j to be able to delete items from it.
        Map<String, String> properties = node.asMap().entrySet()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, el -> String.valueOf(el.getValue())));

        // remove known fields to leave only props in that map
        String srcSwitch = properties.remove(SRC_SWITCH_FIELD);
        int srcPort = NumberUtils.toInt(properties.remove(SRC_PORT_FIELD));
        String dstSwitch = properties.remove(DST_SWITCH_FIELD);
        int dstPort = NumberUtils.toInt(properties.remove(DST_PORT_FIELD));

        return new LinkPropsData(new NetworkEndpoint(srcSwitch, srcPort), new NetworkEndpoint(dstSwitch, dstPort),
                properties);
    }

    private static int parseIntValue(Value value) {
        int intValue = 0;
        try {
            intValue = Optional.ofNullable(value)
                    .filter(val -> !val.isNull())
                    .map(Value::asString)
                    .filter(NumberUtils::isCreatable)
                    .map(Integer::parseInt)
                    .orElse(0);
        } catch (Exception e) {
            logger.info("Exception trying to get an Integer; the String isn't parseable. Value: {}", value);
        }
        return intValue;
    }

    private static IslChangeType getStatus(String status) {
        switch (status) {
            case "active":
                return IslChangeType.DISCOVERED;
            case "inactive":
                return IslChangeType.FAILED;
            case "moved":
                return IslChangeType.MOVED;
            default:
                logger.warn("Found incorrect ISL status: {}", status);
                return IslChangeType.FAILED;
        }
    }

    private LinksConverter() {
    }

}
