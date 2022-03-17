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

package org.openkilda.wfm.topology.flowhs.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import org.openkilda.floodlight.api.request.FlowSegmentRequest;
import org.openkilda.messaging.command.flow.FlowSyncRequest;
import org.openkilda.model.Flow;
import org.openkilda.model.FlowPathStatus;
import org.openkilda.model.FlowStatus;
import org.openkilda.model.PathId;
import org.openkilda.persistence.repositories.FlowPathRepository;
import org.openkilda.wfm.CommandContext;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathOperationConfig;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathReference;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathRequest;
import org.openkilda.wfm.topology.flowhs.model.path.FlowPathResultCode;

import com.google.common.collect.Sets;
import lombok.Value;
import org.apache.commons.lang3.function.FailableConsumer;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Queue;
import java.util.Set;

@RunWith(MockitoJUnitRunner.class)
public class FlowSyncServiceTest extends AbstractFlowTest<FlowSegmentRequest> {
    private static final int SPEAKER_RETRY_LIMIT = 3;
    private static final FlowPathOperationConfig PATH_OPERATION_CONFIG = new FlowPathOperationConfig(
            SPEAKER_RETRY_LIMIT);

    @Mock
    private FlowSyncCarrier carrier;

    private final Queue<CarrierLaunchPathOperation> pathRequests = new ArrayDeque<>();

    @Before
    public void setUp() throws Exception {
        doAnswer(invocation -> {
            CarrierLaunchPathOperation pathOperation = new CarrierLaunchPathOperation(
                    invocation.getArgument(0), invocation.getArgument(1), invocation.getArgument(2));
            pathRequests.offer(pathOperation);
            return null;
        }).when(carrier).launchFlowPathInstallation(any(), any(), any());

        setupFlowRepositorySpy();
        setupFlowPathRepositorySpy();
    }

    @Test
    public void testGenericSync() throws Exception {
        Flow origin = makeFlow();
        FlowPathRepository repository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        repository.updateStatus(origin.getForwardPathId(), FlowPathStatus.INACTIVE);

        FlowSyncRequest request = new FlowSyncRequest(origin.getFlowId());
        FlowSyncService service = newService();

        service.handleRequest("request-key", request, new CommandContext("test-correlation-id"));

        List<CarrierLaunchPathOperation> unexpected = new ArrayList<>();
        Set<PathId> expected = Sets.newHashSet(origin.getForwardPathId(), origin.getReversePathId());
        proceedPathRequests(entry -> {
            FlowPathReference reference = entry.getRequest().getReference();
            if (expected.remove(reference.getPathId())) {
                service.handlePathSyncResponse(reference, FlowPathResultCode.SUCCESS);
            } else {
                unexpected.add(entry);
            }
        });

        Assert.assertTrue(expected.isEmpty());
        Assert.assertTrue(unexpected.isEmpty());

        Flow flow = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyFlowPathStatus(flow.getForwardPath(), FlowPathStatus.ACTIVE, "forward");
        verifyFlowPathStatus(flow.getReversePath(), FlowPathStatus.ACTIVE, "reversed");

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testProtectedSync() throws Exception {
        Flow origin = dummyFactory.makeFlowWithProtectedPath(
                flowSource, flowDestination,
                Collections.singletonList(islSourceDest),
                Arrays.asList(islSourceTransit, islTransitDest));
        FlowPathRepository repository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        repository.updateStatus(origin.getForwardPathId(), FlowPathStatus.INACTIVE);
        repository.updateStatus(origin.getProtectedForwardPathId(), FlowPathStatus.INACTIVE);

        FlowSyncRequest request = new FlowSyncRequest(origin.getFlowId());
        FlowSyncService service = newService();

        service.handleRequest("request-key", request, new CommandContext("test-correlation-id"));

        List<CarrierLaunchPathOperation> unexpected = new ArrayList<>();
        Set<PathId> expected = Sets.newHashSet(origin.getForwardPathId(), origin.getReversePathId());
        Set<PathId> expectedProtected = Sets.newHashSet(
                origin.getProtectedForwardPathId(), origin.getProtectedReversePathId());
        proceedPathRequests(entry -> {
            FlowPathRequest pathRequest = entry.getRequest();
            FlowPathReference reference = pathRequest.getReference();
            if (expected.remove(reference.getPathId())) {
                Assert.assertEquals(2, pathRequest.getPathChunks().size());
                service.handlePathSyncResponse(reference, FlowPathResultCode.SUCCESS);
            } else if (expectedProtected.remove(reference.getPathId())) {
                Assert.assertEquals(1, pathRequest.getPathChunks().size());
                service.handlePathSyncResponse(reference, FlowPathResultCode.SUCCESS);
            } else {
                unexpected.add(entry);
            }
        });

        Assert.assertTrue(expected.isEmpty());
        Assert.assertTrue(expectedProtected.isEmpty());
        Assert.assertTrue(unexpected.isEmpty());

        Flow flow = verifyFlowStatus(origin.getFlowId(), FlowStatus.UP);
        verifyFlowPathStatus(flow.getForwardPath(), FlowPathStatus.ACTIVE, "forward");
        verifyFlowPathStatus(flow.getReversePath(), FlowPathStatus.ACTIVE, "reversed");
        verifyFlowPathStatus(flow.getProtectedForwardPath(), FlowPathStatus.ACTIVE, "forward-protected");
        verifyFlowPathStatus(flow.getProtectedReversePath(), FlowPathStatus.ACTIVE, "reversed-protected");

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testPathInstallFailure() throws Exception {
        Flow origin = makeFlow();
        FlowSyncRequest request = new FlowSyncRequest(origin.getFlowId());
        FlowSyncService service = newService();

        service.handleRequest("request-key", request, new CommandContext("test-correlation-id"));

        List<CarrierLaunchPathOperation> unexpected = new ArrayList<>();
        proceedPathRequests(entry -> {
            FlowPathReference reference = entry.getRequest().getReference();
            if (origin.getReversePathId().equals(reference.getPathId())) {
                service.handlePathSyncResponse(reference, FlowPathResultCode.SPEAKER_ERROR);
            } else if (origin.getForwardPathId().equals(reference.getPathId())) {
                service.handlePathSyncResponse(reference, FlowPathResultCode.SUCCESS);
            } else {
                unexpected.add(entry);
            }
        });

        Assert.assertTrue(unexpected.isEmpty());

        // All flow's path are in ACTIVE state (same as they were before sync), but due to error during sync
        // final flow state will be DEGRADED (so system will try to fix later).
        Flow flow = verifyFlowStatus(origin.getFlowId(), FlowStatus.DEGRADED);
        verifyFlowPathStatus(flow.getForwardPath(), FlowPathStatus.ACTIVE, "forward");
        verifyFlowPathStatus(flow.getReversePath(), FlowPathStatus.ACTIVE, "reversed");

        Assert.assertTrue(service.deactivate());
    }

    @Test
    public void testGlobalTimeout() throws Exception {
        Flow origin = makeFlow();
        FlowPathRepository repository = persistenceManager.getRepositoryFactory().createFlowPathRepository();
        repository.updateStatus(origin.getForwardPathId(), FlowPathStatus.INACTIVE);

        FlowSyncRequest request = new FlowSyncRequest(origin.getFlowId());
        FlowSyncService service = newService();

        String requestKey = "request-key";
        service.handleRequest(requestKey, request, new CommandContext("test-correlation-id"));
        service.handleTimeout(requestKey);

        verify(carrier).cancelFlowPathOperation(eq(origin.getForwardPathId()));
        verify(carrier).cancelFlowPathOperation(eq(origin.getReversePathId()));

        proceedPathRequests(entry -> {
            FlowPathReference reference = entry.getRequest().getReference();
            service.handlePathSyncResponse(reference, FlowPathResultCode.CANCEL);
        });

        Flow flow = verifyFlowStatus(origin.getFlowId(), FlowStatus.DOWN);
        verifyFlowPathStatus(flow.getForwardPath(), FlowPathStatus.INACTIVE, "forward");
        verifyFlowPathStatus(flow.getReversePath(), FlowPathStatus.ACTIVE, "reversed");

        Assert.assertTrue(service.deactivate());
    }

    // utility/service

    private void proceedPathRequests(FailableConsumer<CarrierLaunchPathOperation, Exception> handler) throws Exception {
        for (CarrierLaunchPathOperation entry = pathRequests.poll(); entry != null; entry = pathRequests.poll()) {
            handler.accept(entry);
        }
    }

    private FlowSyncService newService() {
        FlowSyncService service = new FlowSyncService(
                carrier, persistenceManager, flowResourcesManager, PATH_OPERATION_CONFIG);
        service.activate();
        return service;
    }

    @Value
    private static class CarrierLaunchPathOperation {
        FlowPathRequest request;
        FlowPathOperationConfig config;
        CommandContext commandContext;
    }
}
