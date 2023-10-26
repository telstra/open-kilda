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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.openkilda.wfm.topology.opentsdb.models.Storage.NULL_TAG;
import static org.openkilda.wfm.topology.opentsdb.models.Storage.createKey;

import org.openkilda.messaging.info.Datapoint;
import org.openkilda.wfm.topology.opentsdb.models.Storage.DatapointValue;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class StorageTest {

    public static final String KEY_1 = "key1";
    public static final String KEY_2 = "key2";
    public static final String KEY_3 = "key3";
    public static final String VALUE_1 = "value1";
    public static final String VALUE_2 = "value2";
    public static final String METRIC_1 = "metric1";
    public static final String METRIC_2 = "metric2";
    public static final String METRIC_3 = "metric3";
    private static final Random random = new Random();

    @Test
    public void testStorageUniqueness() {
        int size = 1000;
        List<Datapoint> datapoints = new ArrayList<>();
        Storage storage = new Storage();
        for (int i = 0; i < size; i++) {
            Datapoint datapoint = createDatapoint(i);
            datapoints.add(datapoint);
            storage.add(datapoint);
        }
        assertEquals(size, storage.size());

        for (Datapoint datapoint : datapoints) {
            assertDataPointValue(datapoint, storage.get(datapoint));
        }
    }

    @Test
    public void testStorageKeyNullDatapoint() {
        assertEquals(Storage.NULL_KEY, createKey(null));
    }

    @Test
    public void testStorageKeyEmptyDatapoint() {
        assertEquals("null", createKey(new Datapoint(null, null, null, null)));
    }

    @Test
    public void testStorageKeyNullTag() {
        String key = createKey(createDatapoint(METRIC_1, createTags(null, VALUE_1, KEY_2, null)));
        assertEquals(METRIC_1 + "_" + NULL_TAG + ":" + VALUE_1 + "_" + KEY_2 + ":null", key);
    }

    @Test
    public void testStorageKey() {
        String key = createKey(createDatapoint(METRIC_2, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2)));
        assertEquals(METRIC_2 + "_" + KEY_1 + ":" + VALUE_1 + "_" + KEY_2 + ":" + VALUE_2, key);
    }

    @Test
    public void testStorageDifferentTagOrder() {
        Datapoint datapoint1 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_1, createTags(KEY_2, VALUE_2, KEY_1, VALUE_1), 2);
        assertEquals(createKey(datapoint1), createKey(datapoint2));

        Storage storage = createStorage(datapoint1, datapoint2);
        assertEquals(1, storage.size());
        assertDataPointValue(datapoint2, storage.get(datapoint1));
    }

    @Test
    public void testStorageKeyDifferentTags() {
        Datapoint datapoint1 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);
        assertNotEquals(createKey(datapoint1), createKey(datapoint2));

        Storage storage = createStorage(datapoint1, datapoint2);
        assertEquals(2, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertDataPointValue(datapoint2, storage.get(datapoint2));
    }

    @Test
    public void testStorageKeyDifferentValues() {
        Datapoint datapoint1 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_1), 2);
        assertNotEquals(createKey(datapoint1), createKey(datapoint2));

        Storage storage = createStorage(datapoint1, datapoint2);
        assertEquals(2, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertDataPointValue(datapoint2, storage.get(datapoint2));
    }

    @Test
    public void testStorageKeyDifferentMetrics() {
        Datapoint datapoint1 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_2, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 2);
        assertNotEquals(createKey(datapoint1), createKey(datapoint2));

        Storage storage = createStorage(datapoint1, datapoint2);
        assertEquals(2, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertDataPointValue(datapoint2, storage.get(datapoint2));
    }

    @Test
    public void testStorageGetNoneExistent() {
        Datapoint datapoint1 = createDatapoint(METRIC_1, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_2, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 2);

        Storage storage = createStorage(datapoint1);
        assertEquals(1, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertNull(storage.get(datapoint2));
    }

    @Test
    public void testRemoveOutdatedAll() {
        long now = System.currentTimeMillis();
        Datapoint datapoint1 = createDatapoint(METRIC_1, now, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_2, now - 1_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);
        Datapoint datapoint3 = createDatapoint(METRIC_3, now - 2_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);

        Storage storage = createStorage(datapoint1, datapoint2, datapoint3);
        assertEquals(3, storage.size());

        storage.removeOutdated(-500);
        assertEquals(0, storage.size());

        assertNull(storage.get(datapoint1));
        assertNull(storage.get(datapoint2));
        assertNull(storage.get(datapoint3));
    }

    @Test
    public void testRemoveTwoOutdated() {
        long now = System.currentTimeMillis();
        Datapoint datapoint1 = createDatapoint(METRIC_1, now, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_2, now - 1_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);
        Datapoint datapoint3 = createDatapoint(METRIC_3, now - 2_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);

        Storage storage = createStorage(datapoint1, datapoint2, datapoint3);
        assertEquals(3, storage.size());

        storage.removeOutdated(500);
        assertEquals(1, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertNull(storage.get(datapoint2));
        assertNull(storage.get(datapoint3));
    }


    @Test
    public void testRemoveNoneOutdated() {
        long now = System.currentTimeMillis();
        Datapoint datapoint1 = createDatapoint(METRIC_1, now, createTags(KEY_1, VALUE_1, KEY_2, VALUE_2), 1);
        Datapoint datapoint2 = createDatapoint(METRIC_2, now - 1_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 2);
        Datapoint datapoint3 = createDatapoint(METRIC_3, now - 2_000, createTags(KEY_1, VALUE_1, KEY_3, VALUE_2), 3);

        Storage storage = createStorage(datapoint1, datapoint2, datapoint3);
        assertEquals(3, storage.size());

        storage.removeOutdated(5_000);
        assertEquals(3, storage.size());

        assertDataPointValue(datapoint1, storage.get(datapoint1));
        assertDataPointValue(datapoint2, storage.get(datapoint2));
        assertDataPointValue(datapoint3, storage.get(datapoint3));
    }

    private static Map<String, String> createTags(String key1, String value1, String key2, String value2) {
        Map<String, String> map = new HashMap<>();
        map.put(key1, value1);
        map.put(key2, value2);
        return map;
    }

    private static Datapoint createDatapoint(String metric, Long time, Map<String, String> tags, long value) {
        return new Datapoint(metric, time, tags, value);
    }

    private static Datapoint createDatapoint(String metric, Map<String, String> tags, long value) {
        return createDatapoint(metric, random.nextLong(), tags, value);
    }

    private static Datapoint createDatapoint(String metric, Map<String, String> tags) {
        return createDatapoint(metric, random.nextLong(), tags, random.nextInt());
    }

    private static Datapoint createDatapoint(int number) {
        Map<String, String> tags = new HashMap<>();
        for (int i = 0; i < 5; i++) {
            tags.put("key" + i, "value" + i);
        }
        return new Datapoint("some.metric." + number, (long) number, tags, number);
    }

    private static Storage createStorage(Datapoint... datapoints) {
        Storage storage = new Storage();
        for (Datapoint datapoint : datapoints) {
            storage.add(datapoint);
        }
        return storage;
    }

    private static void assertDataPointValue(Datapoint expectedDatapoint, DatapointValue actualValue) {
        assertEquals(expectedDatapoint.getValue(), actualValue.getValue());
        assertEquals(expectedDatapoint.getTime(), actualValue.getTime());
    }
}
