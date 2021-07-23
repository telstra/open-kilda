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

package org.openkilda.persistence.orientdb.repositories;

import static java.lang.String.format;

import org.openkilda.model.MeterId;
import org.openkilda.model.SwitchId;
import org.openkilda.persistence.ferma.frames.FlowMeterFrame;
import org.openkilda.persistence.ferma.frames.converters.MeterIdConverter;
import org.openkilda.persistence.ferma.frames.converters.SwitchIdConverter;
import org.openkilda.persistence.ferma.repositories.FermaFlowMeterRepository;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.FlowMeterRepository;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResult;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

import java.util.Iterator;
import java.util.Optional;

/**
 * OrientDB implementation of {@link FlowMeterRepository}.
 */
public class OrientDbFlowMeterRepository extends FermaFlowMeterRepository {
    private final GraphSupplier graphSupplier;

    OrientDbFlowMeterRepository(OrientDbPersistenceImplementation implementation, GraphSupplier graphSupplier) {
        super(implementation);
        this.graphSupplier = graphSupplier;
    }

    @Override
    public boolean exists(SwitchId switchId, MeterId meterId) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        Long meterIdAsLong = MeterIdConverter.INSTANCE.toGraphProperty(meterId);
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? AND %s = ? LIMIT 1",
                        FlowMeterFrame.FRAME_LABEL, FlowMeterFrame.SWITCH_PROPERTY,
                        FlowMeterFrame.METER_ID_PROPERTY), switchIdAsStr, meterIdAsLong)) {
            return results.iterator().hasNext();
        }
    }

    @Override
    public Optional<MeterId> findFirstUnassignedMeter(SwitchId switchId, MeterId lowestMeterId,
                                                      MeterId highestMeterId) {
        String switchIdAsStr = SwitchIdConverter.INSTANCE.toGraphProperty(switchId);
        Long lowestMeterIdAsLong = MeterIdConverter.INSTANCE.toGraphProperty(lowestMeterId);
        Long highestMeterIdAsLong = MeterIdConverter.INSTANCE.toGraphProperty(highestMeterId);

        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT FROM (SELECT difference(unionAll($init_meter, $next_to_meters).meter, "
                                + "$meters.meter) as meter "
                                + "LET $init_meter = (SELECT %sL as meter), "
                                + "$meters = (SELECT %s.asLong() as meter FROM %s WHERE %s = ? "
                                + "AND %s >= ? AND %s < ?), "
                                + "$next_to_meters = (SELECT %s.asLong() + 1 as meter FROM %s WHERE %s = ? "
                                + "AND %s >= ? AND %s < ?) "
                                + "UNWIND meter) ORDER by meter LIMIT 1",
                        lowestMeterIdAsLong,
                        FlowMeterFrame.METER_ID_PROPERTY, FlowMeterFrame.FRAME_LABEL, FlowMeterFrame.SWITCH_PROPERTY,
                        FlowMeterFrame.METER_ID_PROPERTY, FlowMeterFrame.METER_ID_PROPERTY,
                        FlowMeterFrame.METER_ID_PROPERTY, FlowMeterFrame.FRAME_LABEL, FlowMeterFrame.SWITCH_PROPERTY,
                        FlowMeterFrame.METER_ID_PROPERTY, FlowMeterFrame.METER_ID_PROPERTY),
                switchIdAsStr, lowestMeterIdAsLong, highestMeterIdAsLong, switchIdAsStr,
                lowestMeterIdAsLong, highestMeterIdAsLong)) {
            Iterator<OGremlinResult> it = results.iterator();
            if (it.hasNext()) {
                Number meter = it.next().getProperty("meter");
                if (meter != null) {
                    return Optional.of(MeterIdConverter.INSTANCE.toEntityAttribute(meter.longValue()));
                }
            }
            return Optional.empty();
        }
    }
}
