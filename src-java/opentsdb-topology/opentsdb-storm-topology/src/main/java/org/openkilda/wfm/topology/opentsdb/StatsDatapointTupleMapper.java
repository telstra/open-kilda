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

package org.openkilda.wfm.topology.opentsdb;

import org.openkilda.messaging.info.Datapoint;

import org.apache.storm.opentsdb.OpenTsdbMetricDatapoint;
import org.apache.storm.opentsdb.bolt.ITupleOpenTsdbDatapointMapper;
import org.apache.storm.tuple.ITuple;

public class StatsDatapointTupleMapper implements ITupleOpenTsdbDatapointMapper {
    private final String datapointField;

    public StatsDatapointTupleMapper(String datapointField) {
        this.datapointField = datapointField;
    }

    @Override
    public OpenTsdbMetricDatapoint getMetricPoint(ITuple tuple) {
        Object rawDatapoint = tuple.getValueByField(datapointField);
        Datapoint datapoint;
        try {
            datapoint = (Datapoint) rawDatapoint;
        } catch (ClassCastException e) {
            throw new IllegalArgumentException(String.format(
                    "Unexpected tuple field %s value, expecting instance of %s, but got %s",
                    datapointField, Datapoint.class.getName(), rawDatapoint.getClass().getName()));
        }

        return new OpenTsdbMetricDatapoint(
                datapoint.getMetric(), datapoint.getTags(), datapoint.getTimestamp(), datapoint.getValue());
    }
}
