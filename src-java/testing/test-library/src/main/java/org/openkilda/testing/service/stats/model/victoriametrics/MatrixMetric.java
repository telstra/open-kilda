/* Copyright 2022 Telstra Open Source
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

package org.openkilda.testing.service.stats.model.victoriametrics;

import java.util.HashMap;
import java.util.Map;

public class MatrixMetric extends HashMap<String, String> {
    private static final String NAME_KEY = "__name__";

    public MatrixMetric() {
        super();
    }

    public String getMetricName() {
        return get(NAME_KEY);
    }

    /**
     * Make tags representation as simple map.
     */
    public Map<String, String> getTags() {
        HashMap<String, String> copy = new HashMap<>(this);
        copy.remove(NAME_KEY);
        return copy;
    }
}
