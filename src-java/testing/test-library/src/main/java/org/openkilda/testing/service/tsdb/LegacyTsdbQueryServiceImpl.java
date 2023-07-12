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

package org.openkilda.testing.service.tsdb;

import org.openkilda.testing.service.tsdb.model.StatsResult;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang.NotImplementedException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.List;

@Service("legacyTsdbService")
@Slf4j
public class LegacyTsdbQueryServiceImpl implements TsdbQueryService {

    @Autowired
    @Qualifier("legacyTsdbRestTemplate")
    private RestTemplate restTemplate;

    @Override
    public List<StatsResult> queryDataPointsForLastFiveMinutes(String query) {
        throw new NotImplementedException("Not implemented yet");
    }

    @Override
    public List<StatsResult> queryDataPointsForLastMinutes(String query, int minutes) {
        throw new NotImplementedException("Not implemented yet");
    }
}
