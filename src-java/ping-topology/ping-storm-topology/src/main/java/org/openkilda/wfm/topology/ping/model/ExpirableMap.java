/* Copyright 2018 Telstra Open Source
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

package org.openkilda.wfm.topology.ping.model;

import java.io.Serializable;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;

public class ExpirableMap<K, V extends Expirable<K>> implements Serializable {
    private final PriorityQueue<V> expirableQueue =
            new PriorityQueue<>(Comparator.comparingLong(Expirable::getExpireAt));
    private final Map<K, V> map = new HashMap<>();

    /**
     * Add a record.
     */
    public V put(K key, V value) {
        expirableQueue.add(value);
        return map.put(key, value);
    }

    /**
     * Get a record.
     */
    public V get(K key) {
        return map.get(key);
    }

    /**
     * Add a value.
     */
    public void add(V value) {
        put(value.getExpirableKey(), value);
    }

    /**
     * Add record if there is no record with same key.
     */
    public V addIfAbsent(V value) {
        K key = value.getExpirableKey();
        V current = map.putIfAbsent(key, value);
        if (current == null) {
            current = value;
            expirableQueue.add(value);
        }
        return current;
    }

    /**
     * Remove record be key.
     */
    public V remove(K key) {
        V value = map.remove(key);
        if (value != null) {
            value.setActive(false);
        }
        return value;
    }

    /**
     * Clear records.
     */
    public void clear() {
        expirableQueue.clear();
        map.clear();
    }

    /**
     * Iterate over list until meet not expired record. Remove passed records.
     */
    public List<V> expire(long edge) {
        LinkedList<V> expired = new LinkedList<>();
        while (!expirableQueue.isEmpty()) {
            V firstExpirable = expirableQueue.peek();
            if (edge < firstExpirable.getExpireAt()) {
                break;
            }
            expirableQueue.remove(firstExpirable);
            map.remove(firstExpirable.getExpirableKey());
            if (!firstExpirable.isActive()) {
                continue;
            }
            expired.add(firstExpirable);
        }
        return expired;
    }

    public int size() {
        return map.size();
    }
}
