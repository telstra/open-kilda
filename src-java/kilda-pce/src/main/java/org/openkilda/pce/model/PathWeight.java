/* Copyright 2020 Telstra Open Source
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

package org.openkilda.pce.model;

import static com.google.common.primitives.Longs.asList;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Vector path weight representation. Each value in vector corresponds to some path param defined
 * by {@link WeightFunction}. Only PathWeights created by one WeightFunction should be added and compared.
 */
public class PathWeight implements Comparable<PathWeight> {

    private List<Long> params;

    public PathWeight(long... params) {
        this.params = asList(params);
    }

    public PathWeight(List<Long> params) {
        this.params = params;
    }

    /**
     * Sum two path weights.
     * @param toAdd path weight to add.
     * @return new path weight.
     */
    public PathWeight add(PathWeight toAdd) {
        List<Long> result = new ArrayList<>();
        Iterator<Long> firstIterator = params.iterator();
        Iterator<Long> secondIterator = toAdd.params.iterator();
        while (firstIterator.hasNext() || secondIterator.hasNext()) {
            long first = firstIterator.hasNext() ? firstIterator.next() : 0L;
            long second = secondIterator.hasNext() ? secondIterator.next() : 0L;
            result.add(first + second);
        }
        return new PathWeight(result);
    }

    /**
     * Simple scalar representation of weight.
     * @return scalar weight representation.
     */
    public long toLong() {
        return params.size() > 0 ? params.get(0) : 0;
    }

    @Override
    public int compareTo(PathWeight o) {
        int firstSize = params.size();
        int secondSize = o.params.size();
        int limit = Math.min(firstSize, secondSize);
        int i = 0;
        while (i < limit) {
            long first = params.get(i).longValue();
            long second = o.params.get(i).longValue();
            if (first != second) {
                return first > second ? 1 : -1;
            }
            i++;
        }
        if (firstSize == secondSize) {
            return 0;
        } else {
            return Integer.compare(firstSize, secondSize);
        }
    }
}
