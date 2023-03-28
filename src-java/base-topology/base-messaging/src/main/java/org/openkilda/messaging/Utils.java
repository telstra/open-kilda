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

package org.openkilda.messaging;

import org.openkilda.model.OutputVlanType;
import org.openkilda.model.SwitchId;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utils for flow commands.
 */
public final class Utils {
    /**
     * Common object mapper.
     */
    public static final ObjectMapper MAPPER = new ObjectMapper();
    /**
     * The request timestamp attribute.
     */
    public static final String TIMESTAMP = "timestamp";
    /**
     * The transaction ID property name.
     */
    public static final String TRANSACTION_ID = "transaction_id";
    /**
     * The correlation ID header name.
     */
    public static final String CORRELATION_ID = "correlation_id";
    /**
     * The storm internal topology name.
     */
    public static final String TOPOLOGY_NAME = "topology_name";
    /**
     * The Extra auth header name.
     */
    public static final String EXTRA_AUTH = "EXTRA_AUTH";
    /**
     * The destination property.
     */
    public static final String DESTINATION = "destination";

    /**
     * The region of message origination.
     */
    public static final String REGION = "region";

    public static final String ROUTE = "route";
    /**
     * The payload property.
     */
    public static final String PAYLOAD = "payload";
    /**
     * The payload property.
     */
    public static final String FLOW_ID = "flowid";
    /**
     * The payload property.
     */
    public static final String FLOW_PATH = "flowpath";
    /**
     * The default correlation ID value.
     */
    public static final String DEFAULT_CORRELATION_ID = "admin-request";
    /**
     * The default correlation ID value.
     */
    public static final String SYSTEM_CORRELATION_ID = "system-request";
    /**
     * The health check operational status.
     */
    public static final String HEALTH_CHECK_OPERATIONAL_STATUS = "operational";
    /**
     * The health check non operational status.
     */
    public static final String HEALTH_CHECK_NON_OPERATIONAL_STATUS = "non-operational";
    /**
     * VLAN TAG Ether type value.
     */
    public static final int ETH_TYPE = 0x8100;
    /**
     * OpenFlow controller port number.
     */
    public static final int OF_CONTROLLER_PORT = 0xFFFFFFFD;
    /**
     * Minimum allowable VLAN ID value.
     */
    public static final int MIN_VLAN_ID = 0;
    /**
     * Maximum allowable VLAN ID value.
     */
    public static final int MAX_VLAN_ID = 4095;

    /**
     * Minimum allowable VXLAN VNI value.
     */
    private static final int MIN_VXLAN_ID = 0;

    /**
     * Maximum allowable VXLAN VNI value.
     */
    private static final int MAX_VXLAN_ID = 16777214;



    /**
     * A private constructor.
     */
    private Utils() {
        throw new UnsupportedOperationException();
    }

    /**
     * Checks if specified vlan id is in allowable range.
     *
     * @param vlanId vlan id
     * @return true if vlan id is valid
     */
    public static boolean validateVlanRange(final Integer vlanId) {
        return (vlanId >= MIN_VLAN_ID) && (vlanId <= MAX_VLAN_ID);
    }

    public static boolean validateVxlanRange(final Integer vni) {
        return (vni >= MIN_VXLAN_ID) && (vni <= MAX_VXLAN_ID);
    }

    /**
     * Validates output vlan operation type value by output vlan tag.
     *
     * @param outputVlanId   output vlan id
     * @param outputVlanType output vlan operation type
     * @return true if output vlan operation type is valid
     */
    public static boolean validateOutputVlanType(final Integer outputVlanId, final OutputVlanType outputVlanType) {
        return (outputVlanId != null && outputVlanId != 0)
                ? (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.REPLACE.equals(outputVlanType))
                : (OutputVlanType.POP.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType));
    }

    /**
     * Validates output vlan operation type value by input vlan tag.
     *
     * @param inputVlanId    input vlan id
     * @param outputVlanType output vlan operation type
     * @return true if output vlan operation type is valid
     */
    public static boolean validateInputVlanType(final Integer inputVlanId, final OutputVlanType outputVlanType) {
        return (inputVlanId != null && inputVlanId != 0)
                ? (OutputVlanType.POP.equals(outputVlanType) || OutputVlanType.REPLACE.equals(outputVlanType))
                : (OutputVlanType.PUSH.equals(outputVlanType) || OutputVlanType.NONE.equals(outputVlanType));
    }

    /**
     * Return true if switch id is valid.
     *
     * @param switchId switch id.
     * @return true if switch id is valid.
     */
    public static boolean validateSwitchId(SwitchId switchId) {
        // TODO: check valid switch id
        return switchId != null;
    }

    /**
     * Joins two lists into one.
     */
    public static <T> List<T> joinLists(List<T> list1, List<T> list2) {
        if (list1 == null && list2 == null) {
            return null;
        }
        List<T> result = new ArrayList<>();
        if (list1 != null) {
            result.addAll(list1);
        }
        if (list2 != null) {
            result.addAll(list2);
        }
        return result;
    }

    /**
     * Joins several lists into one.
     */
    public static <T> List<T> joinLists(Stream<List<T>> listsStream) {
        List<List<T>> nonNullLists = listsStream.filter(Objects::nonNull).collect(Collectors.toList());
        if (nonNullLists.isEmpty()) {
            return null;
        }
        return nonNullLists.stream().flatMap(Collection::stream).collect(Collectors.toList());
    }

    /**
     * Joins collection of booleans.
     */
    public static boolean joinBooleans(Collection<Boolean> booleans) {
        Set<Boolean> set = new HashSet<>(booleans);
        set.remove(null);

        if (set.size() > 1) {
            throw new IllegalArgumentException(String.format("Stream %s contains true and false booleans. "
                    + "It must contain only one value of boolean or null values.", booleans));
        }
        if (set.isEmpty()) {
            throw new IllegalArgumentException(String.format("Stream %s has no non-null values.", booleans));
        }
        return set.iterator().next();
    }

    public static int getSize(Collection<?> collection) {
        return collection == null ? 0 : collection.size();
    }

    /**
     * Splits lists on chunks.
     *
     * @param list list to be split
     * @param chunkSize size of chunks
     * @return list of chunks
     */
    public static <E> List<List<E>> split(List<E> list, int chunkSize) {
        return split(list, chunkSize, chunkSize);
    }

    /**
     * Splits lists on chunks with first chunk size different to the rest.
     *
     * @param list list to be split
     * @param firstChunkSize size of first chunk
     * @param chunkSize size of rest chunks
     * @return list of chunks
     */
    public static <E> List<List<E>> split(List<E> list, int firstChunkSize, int chunkSize) {
        List<List<E>> result = new ArrayList<>();
        result.add(list.subList(0, Math.min(firstChunkSize, list.size())));
        for (int i = firstChunkSize; i < list.size(); i += chunkSize) {
            result.add(list.subList(i, Math.min(i + chunkSize, list.size())));
        }
        return result;
    }
}

