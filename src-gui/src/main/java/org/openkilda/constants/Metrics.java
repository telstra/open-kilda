/* Copyright 2023 Telstra Open Source
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

package org.openkilda.constants;

import lombok.AccessLevel;
import lombok.Getter;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Getter
public enum Metrics {

    FLOW_BITS("Flow_bits", "flow.bits"),

    FLOW_BYTES("Flow_bytes", "flow.bytes"),

    FLOW_PACKETS("Flow_packets", "flow.packets"),

    FLOW_INGRESS_PACKETS("Flow_ingress_packets", "flow.ingress.packets"),

    FLOW_RAW_PACKETS("Flow_raw_packets", "flow.raw.packets"),

    FLOW_RAW_BITS("Flow_raw_bits", "flow.raw.bits"),

    FLOW_RAW_BYTES("Flow_raw_bytes", "flow.raw.bytes"),

    FLOW_TABLEID("Flow_tableid", "flow.tableid"),

    ISL_RTT("Isl_rtt", "isl.rtt"),

    ISL_LATENCY("Isl_latency", "isl.latency"),

    SWITCH_COLLISIONS("Switch_collisions", "switch.collisions"),

    SWITCH_RX_CRC_ERROR("Switch_crcerror", "switch.rx-crc-error"),

    SWITCH_RX_FRAME_ERROR("Switch_frameerror", "switch.rx-frame-error"),

    SWITCH_RX_OVER_ERROR("Switch_overerror", "switch.rx-over-error"),

    SWITCH_RX_BITS("Switch_bits", "switch.rx-bits"),

    SWITCH_TX_BITS("Switch_bits", "switch.tx-bits"),

    SWITCH_RX_BYTES("Switch_bytes", "switch.rx-bytes"),

    SWITCH_TX_BYTES("Switch_bytes", "switch.tx-bytes"),

    SWITCH_RX_DROPPED("Switch_drops", "switch.rx-dropped"),

    SWITCH_TX_DROPPED("Switch_drops", "switch.tx-dropped"),

    SWITCH_RX_ERRORS("Switch_errors", "switch.rx-errors"),

    SWITCH_TX_ERRORS("Switch_errors", "switch.tx-errors"),

    SWITCH_TX_PACKETS("Switch_packets", "switch.rx-packets"),

    SWITCH_RX_PACKETS("Switch_packets", "switch.tx-packets"),

    SWITCH_STATE("Switch_state", "switch.state"),

    METER_BITS("Meter_bits", "flow.meter.bits"),

    METER_BYTES("Meter_bytes", "flow.meter.bytes"),

    METER_PACKETS("Meter_packets", "flow.meter.packets");

    private final String tag;

    @Getter(AccessLevel.NONE)
    private final String metricName;

    private static final Map<String, List<Metrics>> TAG_TO_METRICS_MAP = new HashMap<>();

    static {
        for (Metrics metric : values()) {
            if (TAG_TO_METRICS_MAP.containsKey(metric.getTag())) {
                TAG_TO_METRICS_MAP.get(metric.getTag()).add(metric);
            } else {
                List<Metrics> metricList = new ArrayList<>();
                metricList.add(metric);
                TAG_TO_METRICS_MAP.put(metric.getTag(), metricList);
            }
        }
    }

    /**
     * Instantiates a new metrics.
     *
     * @param tag        the tag
     * @param metricName the display tag
     */
    Metrics(final String tag, final String metricName) {
        this.tag = tag;
        this.metricName = metricName;
    }

    public final String getMetricName(String prefix) {
        return prefix + this.metricName;
    }

    public static String getFullMetricNameByMetricName(String metricName, String prefix) {
        return Arrays.stream(values()).filter(value -> value.metricName.equals(metricName))
                .map(val -> val.getMetricName(prefix)).findFirst().orElse(null);
    }

    /**
     * Flow value.
     *
     * @param tag            the tag
     * @param uniDirectional the uni directional
     * @return the list
     */
    public static List<String> flowValue(String tag, boolean uniDirectional, String prefix) {
        List<Metrics> metrics = TAG_TO_METRICS_MAP.get("Flow_" + tag.toLowerCase());
        List<String> metricNames = new ArrayList<>();
        if (CollectionUtils.isEmpty(metrics)) {
            return metricNames;
        }
        metrics.forEach(metric -> {
            metricNames.add(metric.getMetricName(prefix));
            if (uniDirectional) {
                metricNames.add(metric.getMetricName(prefix));
            }
        });
        return metricNames;
    }

    /**
     * Metric name.
     *
     * @param metricPart the last part of the full metric display text.
     * @return full metric name
     */
    public static String flowMetricName(String metricPart, String prefix) {
        if (TAG_TO_METRICS_MAP.get("Flow_" + metricPart.toLowerCase()) == null) {
            return StringUtils.EMPTY;
        }
        Optional<Metrics> metric = TAG_TO_METRICS_MAP.get("Flow_" + metricPart.toLowerCase()).stream().findFirst();
        return metric.map(metrics -> metrics.getMetricName(prefix)).orElse(StringUtils.EMPTY);
    }

    /**
     * Metric name.
     *
     * @param metricPart the last part of the full metric display text.
     * @return full metric name
     */
    public static String meterMetricName(String metricPart, String prefix) {
        if (TAG_TO_METRICS_MAP.get("Meter_" + metricPart.toLowerCase()) == null) {
            return StringUtils.EMPTY;
        }
        Optional<Metrics> metric = TAG_TO_METRICS_MAP.get("Meter_" + metricPart.toLowerCase()).stream().findFirst();
        return metric.map(metrics -> metrics.getMetricName(prefix)).orElse(StringUtils.EMPTY);
    }

    /**
     * Flow raw value.
     *
     * @param metricEnding the tag
     * @return the list
     */
    public static List<String> flowRawValue(String metricEnding, String prefix) {
        List<String> list = new ArrayList<>();
        metricEnding = "Flow_raw_" + metricEnding;
        for (Metrics metric : values()) {
            if (metric.getTag().equalsIgnoreCase(metricEnding)) {
                list.add(metric.getMetricName(prefix));
            }
        }
        return list;
    }

    /**
     * Switch value.
     *
     * @param metricEnding the tag
     * @return the list
     */
    public static List<String> switchValue(String metricEnding, String prefix) {
        List<String> list = new ArrayList<>();
        String metricTag;

        if (metricEnding.equalsIgnoreCase("latency")) {
            metricTag = "Isl_" + metricEnding;
        } else if (metricEnding.equalsIgnoreCase("rtt")) {
            metricTag = "Isl_" + metricEnding;
        } else {
            metricTag = "Switch_" + metricEnding;
        }
        for (Metrics metric : values()) {
            if (metric.getTag().equalsIgnoreCase(metricTag)) {
                list.add(metric.getMetricName(prefix));
            }
        }
        return list;
    }

    /**
     * Gets the starts with.
     *
     * @param tag the tag
     * @return the starts with
     */
    public static List<String> getStartsWith(String tag, String prefix) {
        List<String> list = new ArrayList<>();
        for (Metrics metric : values()) {
            if (metric.getTag().startsWith(tag)) {
                list.add(metric.getMetricName(prefix));
            }
        }
        return list;
    }

    /**
     * Meter value.
     *
     * @param tag the tag
     * @return the list
     */
    public static List<String> meterValue(String tag, String prefix) {
        List<Metrics> metrics = TAG_TO_METRICS_MAP.get("Meter_" + tag.toLowerCase());
        List<String> metricNames = new ArrayList<>();
        if (CollectionUtils.isEmpty(metrics)) {
            return metricNames;
        }
        metrics.forEach(metric -> metricNames.add(metric.getMetricName(prefix)));
        return metricNames;
    }

    /**
     * List.
     *
     * @return the list
     */
    public static List<String> list(String prefix) {
        return Arrays.stream(values()).map(metric -> metric.getMetricName(prefix)).collect(Collectors.toList());
    }

}
