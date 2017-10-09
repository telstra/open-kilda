/* Copyright 2017 Telstra Open Source
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

package org.openkilda.topology.domain.repository;

import org.openkilda.topology.domain.Isl;

import org.springframework.data.neo4j.annotation.Query;
import org.springframework.data.neo4j.repository.GraphRepository;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * ISL repository.
 * Manages operations on links.
 */
public interface IslRepository extends GraphRepository<Isl> {

    //TODO: replace queries on Spring auto generated methods

    /**
     * Creates link in database.
     *
     * @param sourceNode        source switch datapath id
     * @param destinationNode   destination switch datapath id
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source switch port
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination switch port
     * @param latency           link latency
     * @param speed             port speed
     * @param bandwidth         available bandwidth
     */
    @Query("MATCH ( u:switch { name: {src_node} } ), ( r:switch { name: {dst_node} } ) MERGE (u)-[:isl " +
            "{ src_port: {src_port}, dst_port: {dst_port}, src_switch: {src_switch}, dst_switch: {dst_switch}, " +
            "latency: {latency}, speed: {speed}, bandwidth: {bandwidth} } " +
            "]->(r)")
    void creteIsl(@Param("src_node") String sourceNode,
                  @Param("dst_node") String destinationNode,
                  @Param("src_switch") String sourceSwitch,
                  @Param("src_port") int sourcePort,
                  @Param("dst_switch") String destinationSwitch,
                  @Param("dst_port") int destinationPort,
                  @Param("latency") long latency,
                  @Param("speed") long speed,
                  @Param("bandwidth") long bandwidth);

    /**
     * Finds ISL by source and destination.
     *
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source switch port
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination switch port
     * @return {@link Isl} instance
     */
    @Query("MATCH (a:switch)-[r:isl " +
            "{ src_switch: {src_switch}, src_port: {src_port}, dst_switch: {dst_switch}, dst_port: {dst_port} } " +
            "]->(b:switch) return r")
    Isl findIsl(@Param("src_switch") String sourceSwitch,
                @Param("src_port") int sourcePort,
                @Param("dst_switch") String destinationSwitch,
                @Param("dst_port") int destinationPort);

    /**
     * Finds ISL and updates latency.
     *
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source switch port
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination switch port
     * @param latency           link latency
     * @return {@link Isl} instance
     */
    @Query("MATCH (a:switch)-[r:isl " +
            "{ src_switch: {src_switch}, src_port: {src_port}, dst_switch: {dst_switch}, dst_port: {dst_port} } " +
            "]->(b:switch) set r.latency = {latency} return r")
    Isl updateLatency(@Param("src_switch") String sourceSwitch,
                      @Param("src_port") int sourcePort,
                      @Param("dst_switch") String destinationSwitch,
                      @Param("dst_port") int destinationPort,
                      @Param("latency") long latency);

    /**
     * Finds and deletes ISL.
     *
     * @param sourceSwitch      source switch datapath id
     * @param sourcePort        source switch port
     * @param destinationSwitch destination switch datapath id
     * @param destinationPort   destination switch port
     */
    @Query("MATCH (a:switch)-[r:isl " +
            "{ src_switch: {src_switch}, src_port: {src_port}, dst_switch: {dst_switch}, dst_port: {dst_port} } " +
            "]->(b:switch) delete r")
    void deleteIsl(@Param("src_switch") String sourceSwitch,
                   @Param("src_port") int sourcePort,
                   @Param("dst_switch") String destinationSwitch,
                   @Param("dst_port") int destinationPort);

    /**
     * Computes path between two switches.
     *
     * @param sourceNode      source switch datapath id
     * @param destinationNode destination switch datapath id
     * @return list of {@link Isl} instances
     */
    @Query("MATCH ( a:switch { name: {src_node} } ), ( b:switch { name: {dst_node} } ), " +
            "p = shortestPath((a)-[:isl*..100]->(b)) where ALL(x in nodes(p) WHERE x.state = 'active') RETURN p")
    List<Isl> getPath(@Param("src_node") String sourceNode,
                      @Param("dst_node") String destinationNode);

    /**
     * Gets all ISLs.
     *
     * @return list of {@link Isl} instance
     */
    @Query("MATCH (a:switch)-[r:isl]->(b:switch) return r")
    List<Isl> getAllIsl();
}
