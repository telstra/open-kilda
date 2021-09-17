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

import org.openkilda.persistence.ferma.frames.VxlanFrame;
import org.openkilda.persistence.ferma.repositories.FermaVxlanRepository;
import org.openkilda.persistence.orientdb.OrientDbPersistenceImplementation;
import org.openkilda.persistence.repositories.VxlanRepository;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResult;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

import java.util.Iterator;
import java.util.Optional;

/**
 * OrientDB implementation of {@link VxlanRepository}.
 */
public class OrientDbVxlanRepository extends FermaVxlanRepository {
    private final GraphSupplier graphSupplier;

    OrientDbVxlanRepository(OrientDbPersistenceImplementation implementation, GraphSupplier graphSupplier) {
        super(implementation);
        this.graphSupplier = graphSupplier;
    }

    @Override
    public boolean exists(int vxlan) {
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        VxlanFrame.FRAME_LABEL, VxlanFrame.VNI_PROPERTY), vxlan)) {
            return results.iterator().hasNext();
        }
    }

    @Override
    public Optional<Integer> findFirstUnassignedVxlan(int lowestVxlan, int highestVxlan) {
        try (OGremlinResultSet results = graphSupplier.get().querySql(
                format("SELECT FROM (SELECT difference(unionAll($init_vni, $next_to_vnis).vni, "
                                + "$vnis.vni) as vni "
                                + "LET $init_vni = (SELECT %s as vni), "
                                + "$vnis = (SELECT %s.asInteger() as vni FROM %s WHERE %s >= ? AND %s < ?), "
                                + "$next_to_vnis = (SELECT %s.asInteger() + 1 as vni FROM %s WHERE %s >= ? AND %s < ?) "
                                + "UNWIND vni) ORDER by vni LIMIT 1",
                        lowestVxlan,
                        VxlanFrame.VNI_PROPERTY, VxlanFrame.FRAME_LABEL,
                        VxlanFrame.VNI_PROPERTY, VxlanFrame.VNI_PROPERTY,
                        VxlanFrame.VNI_PROPERTY, VxlanFrame.FRAME_LABEL,
                        VxlanFrame.VNI_PROPERTY, VxlanFrame.VNI_PROPERTY),
                lowestVxlan, highestVxlan, lowestVxlan, highestVxlan)) {
            Iterator<OGremlinResult> it = results.iterator();
            if (it.hasNext()) {
                Number vni = it.next().getProperty("vni");
                if (vni != null) {
                    return Optional.of(vni.intValue());
                }
            }
            return Optional.empty();
        }
    }
}
