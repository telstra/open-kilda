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

package org.openkilda.functionaltests.helpers

import groovy.util.logging.Slf4j
import org.openkilda.testing.service.elastic.ElasticService
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.stereotype.Service

@Service
@Slf4j
class ElasticHelper {

    @Autowired
    private ElasticService elasticService

    /**
     * Returns log entries from ElasticSearch in accordance to the query parameters.
     * @return Map<String, Object>. In case of ElasticSearch communication failure, an empty map will be returned.
     */
    def getLogs(ElasticQuery q) {
        return elasticService.getLogs(q.appId, q.tags, q.level, q.timeRange, q.resultCount, q.defaultField, q.index)
    }
}
