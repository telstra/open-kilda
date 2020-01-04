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

import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;

public class ExpirableMap<K, V extends Expirable<K>> {
    private final LinkedList<V> queue = new LinkedList<>();
    private final HashMap<K, V> map = new HashMap<>();

    public V put(K key, V value) {
        queue.addLast(value);
        return map.put(key, value);
    }

    public V get(K key) {
        return map.get(key);
    }

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
            queue.addLast(value);
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
     * Iterate over list until meet not expired record. Remove passed records.
     */
    public List<V> expire(long edge) {
        LinkedList<V> removed = new LinkedList<>();

        while (! queue.isEmpty()) {
            V value = queue.getFirst();

            if (edge < value.getExpireAt()) {
                break;
            }

            queue.removeFirst();
            map.remove(value.getExpirableKey());
            if (!value.isActive()) {
                continue;
            }

            removed.addLast(value);
        }

        return removed;
    }

    public int size() {
        return map.size();
    }
}
