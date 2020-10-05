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

import org.openkilda.persistence.ferma.frames.TransitVlanFrame;
import org.openkilda.persistence.ferma.repositories.FermaTransitVlanRepository;
import org.openkilda.persistence.orientdb.OrientDbGraphFactory;
import org.openkilda.persistence.repositories.TransitVlanRepository;
import org.openkilda.persistence.tx.TransactionManager;

import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResult;
import org.apache.tinkerpop.gremlin.orientdb.executor.OGremlinResultSet;

import java.util.Iterator;
import java.util.Optional;

/**
 * OrientDB implementation of {@link TransitVlanRepository}.
 */
public class OrientDbTransitVlanRepository extends FermaTransitVlanRepository {
    private final OrientDbGraphFactory orientDbGraphFactory;

    OrientDbTransitVlanRepository(OrientDbGraphFactory orientDbGraphFactory, TransactionManager transactionManager) {
        super(orientDbGraphFactory, transactionManager);
        this.orientDbGraphFactory = orientDbGraphFactory;
    }

    @Override
    public boolean exists(int transitVlan) {
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT @rid FROM %s WHERE %s = ? LIMIT 1",
                        TransitVlanFrame.FRAME_LABEL, TransitVlanFrame.VLAN_PROPERTY), transitVlan)) {
            return results.iterator().hasNext();
        }
    }

    @Override
    public Optional<Integer> findFirstUnassignedVlan(int lowestTransitVlan, int highestTransitVlan) {
        try (OGremlinResultSet results = orientDbGraphFactory.getOrientGraph().querySql(
                format("SELECT FROM (SELECT difference(unionAll($init_vlan, $next_to_vlans).vlan, "
                                + "$vlans.vlan) as vlan "
                                + "LET $init_vlan = (SELECT %s as vlan), "
                                + "$vlans = (SELECT %s.asInteger() as vlan FROM %s WHERE %s >= ? AND %s < ?), "
                                + "$next_to_vlans = (SELECT %s.asInteger() + 1 as vlan FROM %s "
                                + "WHERE %s >= ? AND %s < ?) "
                                + "UNWIND vlan) ORDER by vlan LIMIT 1",
                        lowestTransitVlan,
                        TransitVlanFrame.VLAN_PROPERTY, TransitVlanFrame.FRAME_LABEL,
                        TransitVlanFrame.VLAN_PROPERTY, TransitVlanFrame.VLAN_PROPERTY,
                        TransitVlanFrame.VLAN_PROPERTY, TransitVlanFrame.FRAME_LABEL,
                        TransitVlanFrame.VLAN_PROPERTY, TransitVlanFrame.VLAN_PROPERTY),
                lowestTransitVlan, highestTransitVlan, lowestTransitVlan, highestTransitVlan)) {
            Iterator<OGremlinResult> it = results.iterator();
            if (it.hasNext()) {
                Number vlan = it.next().getProperty("vlan");
                if (vlan != null) {
                    return Optional.of(vlan.intValue());
                }
            }
            return Optional.empty();
        }
    }
}
