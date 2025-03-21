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


package org.openkilda.server42.control.stormstub;

import org.openkilda.model.SwitchId;
import org.openkilda.server42.control.messaging.flowrtt.ClearFlows;
import org.openkilda.server42.control.messaging.flowrtt.Headers;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsRequest;
import org.openkilda.server42.control.messaging.flowrtt.ListFlowsResponse;
import org.openkilda.server42.control.messaging.flowrtt.RemoveFlow;
import org.openkilda.server42.control.messaging.islrtt.ClearIsls;
import org.openkilda.server42.control.messaging.islrtt.ListIslsRequest;
import org.openkilda.server42.control.messaging.islrtt.ListIslsResponse;
import org.openkilda.server42.control.messaging.islrtt.RemoveIsl;
import org.openkilda.server42.control.stormstub.api.AddFlowPayload;
import org.openkilda.server42.control.stormstub.api.AddIslPayload;
import org.openkilda.server42.control.stormstub.api.PushSettingsPayload;
import org.openkilda.server42.messaging.FlowDirection;

import io.swagger.v3.oas.annotations.Operation;
import lombok.extern.slf4j.Slf4j;
import org.mapstruct.factory.Mappers;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.async.DeferredResult;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

@RestController
@RequestMapping(value = "/kafka")
@Slf4j
public class KafkaController {

    private final KafkaTemplate<String, Object> template;

    private static ApiMapper mapper = Mappers.getMapper(ApiMapper.class);

    @Value("${openkilda.server42.control.kafka.topic.to_storm}")
    private String toStorm;
    @Value("${openkilda.server42.control.kafka.topic.from_storm}")
    private String fromStorm;

    private ConcurrentHashMap<String, DeferredResult<ResponseEntity<?>>> deferredResultConcurrentHashMap =
            new ConcurrentHashMap<String, DeferredResult<ResponseEntity<?>>>();

    public KafkaController(KafkaTemplate<String, Object> template) {
        this.template = template;
    }

    @Operation(summary = "Create Flow")
    @PostMapping(value = "/flow")
    private void createFlow(@RequestParam String switchId, @RequestBody AddFlowPayload flow)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, mapper.map(flow));
    }

    @Operation(summary = "Get Flow list")
    @GetMapping(value = "/flow/")
    @ResponseBody
    private DeferredResult<ResponseEntity<?>> getFlowList(@RequestParam String switchId)
            throws InterruptedException, ExecutionException, TimeoutException {

        Headers headers = buildHeader();
        String correlationId = headers.getCorrelationId();
        send(switchId, ListFlowsRequest.builder().headers(headers).build());

        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(500L);

        deferredResult.onTimeout(() -> {
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                            .body("Request timeout occurred."));
            deferredResultConcurrentHashMap.remove(correlationId);
        });

        deferredResult.onCompletion(() ->
                deferredResultConcurrentHashMap.remove(correlationId)
        );

        deferredResult.onError((Throwable throwable) -> {
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(throwable));
            deferredResultConcurrentHashMap.remove(correlationId);
        });

        deferredResultConcurrentHashMap.put(correlationId, deferredResult);

        return deferredResult;
    }

    @Operation(summary = "Delete Flow")
    @DeleteMapping(value = "/flow/{id}")
    private void deleteFlow(@RequestParam String switchId, @RequestParam(value = "id") String flowId,
                            @RequestParam FlowDirection direction)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, RemoveFlow.builder().headers(buildHeader()).flowId(flowId).direction(direction).build());
    }

    @Operation(summary = "Clear Flows")
    @DeleteMapping(value = "/flow/")
    private void clearFlows(@RequestParam String switchId)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, ClearFlows.builder().headers(buildHeader()).build());
    }

    @Operation(summary = "Push settings")
    @PostMapping(value = "/settings/")
    private void pushSettings(@RequestParam String switchId, @RequestBody PushSettingsPayload settingsPayload)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, mapper.map(settingsPayload));
    }

    @KafkaListener(id = "server42-control-storm-stub", topics = "${openkilda.server42.control.kafka.topic.to_storm}")
    private void listen(ListFlowsResponse listFlowsResponse) {
        try {
            DeferredResult<ResponseEntity<?>> responseEntityDeferredResult =
                    deferredResultConcurrentHashMap.get(listFlowsResponse.getHeaders().getCorrelationId());
            log.info(listFlowsResponse.getFlowIds().toString());
            log.info(mapper.map(listFlowsResponse).toString());
            responseEntityDeferredResult.setResult(ResponseEntity.ok(mapper.map(listFlowsResponse)));
        } catch (NullPointerException ex) {
            log.error("ListFlowsRequest dropped correlation_id {}", listFlowsResponse.getHeaders().getCorrelationId());
        }
    }

    @KafkaListener(id = "server42-control-storm-stub-isls",
            topics = "${openkilda.server42.control.kafka.topic.to_storm}")
    private void listen(ListIslsResponse listIslsResponse) {
        try {
            DeferredResult<ResponseEntity<?>> responseEntityDeferredResult =
                    deferredResultConcurrentHashMap.get(listIslsResponse.getHeaders().getCorrelationId());
            log.info("ListIslsResponse: {}, {}", listIslsResponse.getPorts(), mapper.map(listIslsResponse));
            responseEntityDeferredResult.setResult(ResponseEntity.ok(mapper.map(listIslsResponse)));
        } catch (NullPointerException ex) {
            log.error("ListIslsResponse dropped correlation_id {}", listIslsResponse.getHeaders().getCorrelationId());
        }
    }

    @Operation(summary = "Create Isl")
    @PostMapping(value = "/isl")
    private void createIsl(@RequestBody AddIslPayload isl)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(isl.getSwitchId().toString(), mapper.map(isl));
    }

    @Operation(summary = "Get Isl list")
    @GetMapping(value = "/isl")
    @ResponseBody
    private DeferredResult<ResponseEntity<?>> getIslList(@RequestParam String switchId)
            throws InterruptedException, ExecutionException, TimeoutException {

        Headers headers = buildHeader();
        String correlationId = headers.getCorrelationId();
        send(switchId, ListIslsRequest.builder().headers(headers)
                .switchId(new SwitchId(switchId)).build());

        DeferredResult<ResponseEntity<?>> deferredResult = new DeferredResult<>(500L);

        deferredResult.onTimeout(() -> {
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.REQUEST_TIMEOUT)
                            .body("Request timeout occurred."));
            deferredResultConcurrentHashMap.remove(correlationId);
        });

        deferredResult.onCompletion(() ->
                deferredResultConcurrentHashMap.remove(correlationId)
        );

        deferredResult.onError((Throwable throwable) -> {
            deferredResult.setErrorResult(
                    ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(throwable));
            deferredResultConcurrentHashMap.remove(correlationId);
        });

        deferredResultConcurrentHashMap.put(correlationId, deferredResult);

        return deferredResult;
    }

    @Operation(summary = "Delete Isl")
    @DeleteMapping(value = "/isl/{switchId}/{port}")
    private void deleteIsl(@PathVariable String switchId, @PathVariable Integer port)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, RemoveIsl.builder().headers(buildHeader())
                .switchId(new SwitchId(switchId)).port(port).build());
    }

    @Operation(summary = "Clear Isls")
    @DeleteMapping(value = "/isl")
    private void clearIsls(@RequestParam String switchId)
            throws InterruptedException, ExecutionException, TimeoutException {
        send(switchId, ClearIsls.builder().headers(buildHeader())
                .switchId(new SwitchId(switchId)).build());
    }

    private void send(String switchId, Object payload)
            throws InterruptedException, ExecutionException, TimeoutException {
        CompletableFuture<SendResult<String, Object>> future = template.send(fromStorm, switchId, payload);
        future.get(30, TimeUnit.SECONDS);
    }

    private Headers buildHeader() {
        String correlationId = UUID.randomUUID().toString();
        return Headers.builder().correlationId(correlationId).build();
    }
}
