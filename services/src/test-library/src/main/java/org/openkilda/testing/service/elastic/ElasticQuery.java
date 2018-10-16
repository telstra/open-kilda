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

package org.openkilda.testing.service.elastic;

class ElasticQuery {
    String appId;
    String tags;
    String level;
    long timeRange;
    long resultCount;
    String defaultField;
    String index;

    ElasticQuery(String appId, String tags, String level, long timeRange,
                        long resultCount, String defaultField, String index) {
        this.appId = appId;
        this.tags = tags;
        this.level = level;
        this.timeRange = timeRange;
        this.resultCount = resultCount;
        this.defaultField = defaultField;
        this.index = index;
    }
}
