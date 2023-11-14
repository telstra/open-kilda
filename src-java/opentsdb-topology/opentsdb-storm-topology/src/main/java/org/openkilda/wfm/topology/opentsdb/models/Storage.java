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

package org.openkilda.wfm.topology.opentsdb.models;

import org.openkilda.messaging.info.Datapoint;

import com.google.common.annotations.VisibleForTesting;
import lombok.ToString;
import lombok.Value;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

@ToString
public class Storage implements Serializable {
    public static final String NULL_KEY = "null";
    public static final char TAG_KEY_DELIMITER = '_';
    public static final char TAG_VALUE_DELIMITER = ':';
    public static final String NULL_TAG = "NULL_TAG";

    private Map<String, DatapointValue> map;

    public Storage() {
        this.map = createNewMapInstance();
    }

    public void add(Datapoint datapoint) {
        map.put(createKey(datapoint), createValue(datapoint));
    }

    public DatapointValue get(Datapoint datapoint) {
        return map.get(createKey(datapoint));
    }

    /**
     * Removes datapoints from the storage if they are older than now() - ttlInMillis.
     */
    public void removeOutdated(long ttlInMillis) {
        long now = System.currentTimeMillis();
        map.entrySet().removeIf(entry -> now - entry.getValue().getTime() > ttlInMillis);
        if (map.isEmpty()) {
            map = createNewMapInstance();
        }
    }

    public int size() {
        return map.size();
    }

    @VisibleForTesting
    static String createKey(Datapoint datapoint) {
        if (datapoint == null) {
            return NULL_KEY;
        }
        StringBuilder key = new StringBuilder();
        key.append(datapoint.getMetric());

        if (datapoint.getTags() != null) {
            SortedMap<String, String> sortedTags = getSortedTags(datapoint);
            for (Entry<String, String> entry : sortedTags.entrySet()) {
                key.append(TAG_KEY_DELIMITER);
                key.append(entry.getKey());
                key.append(TAG_VALUE_DELIMITER);
                key.append(entry.getValue());
            }
        }
        return key.toString();
    }

    private static DatapointValue createValue(Datapoint datapoint) {
        if (datapoint == null) {
            return null;
        }
        return new DatapointValue(datapoint.getValue(), datapoint.getTime());
    }

    private static SortedMap<String, String> getSortedTags(Datapoint datapoint) {
        SortedMap<String, String> sortedTags = new TreeMap<>();
        for (Entry<String, String> entry : datapoint.getTags().entrySet()) {
            String key = Optional.ofNullable(entry.getKey()).orElse(NULL_TAG);
            sortedTags.put(key, entry.getValue());
        }
        return sortedTags;
    }

    /**
     * Instead of map.clear() we are creating a new map here.
     * We need it because map.clear() doesn't shrink already allocated map capacity, size of which can be significant.
     */
    private static Map<String, DatapointValue> createNewMapInstance() {
        return new HashMap<>();
    }

    @Value
    public static class DatapointValue implements Serializable {
        Number value;
        Long time;
    }
}
