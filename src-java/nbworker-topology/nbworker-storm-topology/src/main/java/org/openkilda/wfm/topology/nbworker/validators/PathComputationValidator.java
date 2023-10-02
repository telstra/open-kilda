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

package org.openkilda.wfm.topology.nbworker.validators;

import static java.lang.String.format;

import org.openkilda.model.Flow;
import org.openkilda.model.PathId;
import org.openkilda.pce.AvailableNetworkFactory;
import org.openkilda.pce.FlowParameters;
import org.openkilda.pce.GetPathsResult;
import org.openkilda.pce.Path;
import org.openkilda.pce.PathComputer;
import org.openkilda.pce.exception.RecoverableException;
import org.openkilda.pce.exception.UnroutableFlowException;
import org.openkilda.pce.finder.FailReason;
import org.openkilda.pce.finder.FailReasonType;
import org.openkilda.pce.impl.AvailableNetwork;
import org.openkilda.pce.impl.RequestedPath;

import java.util.Collection;

/**
 * This class validates whether a specific path can be created using the path computer.
 * In contrast with {@link PathValidator}, which gives information about each segment, this validator
 * tries to actually compute a path but without resources allocation.
 */
public class PathComputationValidator {
    private final AvailableNetworkFactory availableNetworkFactory;
    private final PathComputer pathComputer;

    public PathComputationValidator(AvailableNetworkFactory availableNetworkFactory, PathComputer pathComputer) {
        this.availableNetworkFactory = availableNetworkFactory;
        this.pathComputer = pathComputer;
    }

    /**
     * This method creates an available network from a single path only and then executes PCE's methods
     * to compute a path. This mimics the way how the path is computed when executing operations on a flow.
     * Keep in mind that operations on an actual flow are done using the full network graph, while this validation
     * is done on a subset of that network. PCE doesn't have to select this path when executing operations on a flow,
     * for example when the given path is not the best one.
     * @param flow flow parameters that are used to create an available network
     * @param path the path to validate
     * @return a string describing the result of the path computation attempt
     */
    public GetPathsResult validatePath(Flow flow, Path path, Collection<PathId> reuseResources) {
        try {
            AvailableNetwork network = availableNetworkFactory.getAvailableNetwork(
                    new FlowParameters(flow), path,
                    reuseResources);

            return pathComputer.getPath(network, new RequestedPath(flow), false);
        } catch (UnroutableFlowException | RecoverableException e) {
            FailReasonType failReasonType = e instanceof UnroutableFlowException
                    ? FailReasonType.UNROUTABLE_FLOW : FailReasonType.RECOVERABLE_EXCEPTION;

            return GetPathsResult.builder().failReason(failReasonType,
                    new FailReason(failReasonType,
                            format("An exception occurred when trying to compute path: %s: %s",
                                    e.getClass().getSimpleName(), e.getMessage()))).build();
        }
    }
}
