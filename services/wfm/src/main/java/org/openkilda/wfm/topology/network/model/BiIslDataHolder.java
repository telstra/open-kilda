/* Copyright 2019 Telstra Open Source
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

package org.openkilda.wfm.topology.network.model;

import lombok.Getter;

import java.util.Arrays;
import java.util.List;

public class BiIslDataHolder<T> {
    @Getter
    private final IslReference reference;

    protected final List<T> islData = Arrays.asList(null, null);

    public BiIslDataHolder(IslReference reference) {
        this.reference = reference;
    }

    public void put(Endpoint endpoint, T data) {
        int idx = dataIndexByEndpoint(reference, endpoint);
        islData.set(idx, data);
    }

    public T get(Endpoint endpoint) {
        int idx = dataIndexByEndpoint(reference, endpoint);
        return islData.get(idx);
    }

    /**
     * Put same value for both ends.
     */
    public void putBoth(T data) {
        for (int idx = 0; idx < islData.size(); idx++) {
            islData.set(idx, data);
        }
    }

    public T getForward() {
        return islData.get(0);
    }

    public T getReverse() {
        return islData.get(1);
    }

    protected static int dataIndexByEndpoint(IslReference ref, Endpoint e) {
        int idx;
        if (ref.getSource().equals(e)) {
            idx =  0; // forward bind
        } else if (ref.getDest().equals(e)) {
            idx = 1; // reverse bind
        } else {
            throw new IllegalArgumentException(String.format("Endpoint %s is not belong to ISL %s", e, ref));
        }
        return idx;
    }
}
