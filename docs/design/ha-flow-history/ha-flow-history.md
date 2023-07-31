# HA flow history

## Overview
HA flow history is a feature that allows to save some specific information about operations on HA flow and read this 
information later. For example, when we create an HA flow, we save:
* the fact that there was an attempt to create an HA flow (this is called an event)
* specific steps performed during this operation such as validation, resources allocation, errors (these are actions)
* dump of an HA flow (this is a human-readable representation of an HA flow)

An event serves as a container for multiple actions and dumps. For example, an event "HA-flow UPDATE" comprises multiple 
actions and multiple dumps (before and after the update).

We can read this information later using NB API. The history model object is similar to HA flow model, but have slightly
different structure: paths are located at the root and transformed into a list of nodes; all fields of HA flow model object
don't have to be in the history.

## Saving history
In order to save a history it is required to create an event and then create specific actions or add dumps. It is advised
to use non-blocking way of saving the history: use a history service class that sends the history via messaging system
to the storage system.

### Create and save a new event
When some operation begins we can create a new event in the following way:
```java
HaFlowHistoryService.using(stateMachine.getCarrier()).saveNewHaFlowEvent(HaFlowEventData.builder()
        .event(HaFlowEventData.Event.CREATE)
        .haFlowId(stateMachine.getHaFlowId())
        .taskId(stateMachine.getCommandContext().getCorrelationId())
        .details("HA-flow has been validated successfully")
        .build());
```
Here: 
* using() takes a carrier that can deliver the message to the History bolt
* saveNewHaFlowEvent will take care of creating an event based on the provided parameters:
  * HA flow ID is mandatory: it will be used later to find this event,
  * task ID is mandatory: it will be used to group different actions together under this event,
  * event specifies the event name,
  * details could give more information about the operation.

### Create and save an action
When an event has been created and the specific steps are executed, we can save information about the execution in the 
following way:
```java
HaFlowHistoryService.using(stateMachine.getCarrier()).save(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("HA-flow paths have been installed")
                .withDescription(format("The HA-flow paths %s / %s have been installed",
                        newPrimaryForwardPathId, newPrimaryReversePathId))
                .withHaFlowId(stateMachine.getHaFlowId()));
```
Here: 
* using() takes a carrier that can deliver the message to the History bolt
* save will take care of creating and saving a new action with the given parameters:
    * task ID is mandatory: it will be used to group different actions together under the event,
    * HA flow ID is strongly advised to specify for consistency,
    * action is some short description of the action
    * details could give more information about the action and provide some information known at run-time.

### Create and save an action with dumps
We can create an action in the same way as described above, but also add a dump of HA flow. There are three ways to do that:
* use `HaFlowHistory::withHaFlowDump` that takes a parameter `HaFlowDumpData` which you need to create yourself (possibly, 
using a mapper, see `HaFlowHistoryMapper::toHaFlowDumpData`). This way is useful when the details are calculated at run time.
* use `HaFlowHistory::withHaFlowDumpBefore` or `HaFlowHistory::withHaFlowDumpAfter` that take HaFlow directly. This is 
useful when the dump type is known at compile time (and you don't need to create `HaFlowDumpData` yourself).

### Create and save an error
Errors are actions; they just have negative results. It is possible to save an error using the same way as saving actions,
but it is advised to use the syntactic sugar: 
```java
HaFlowHistoryService.using(stateMachine.getCarrier()).saveError(HaFlowHistory
                .withTaskId(stateMachine.getCommandContext().getCorrelationId())
                .withAction("Failed to create the flow")
                .withDescription(stateMachine.getErrorReason())
                .withHaFlowId(stateMachine.getHaFlowId()));
```
the behavior is exactly the same as for `save`, but:
* action is a short description of a negative result 
* description is detailed information of the error or the result. possibly with some data known at run time.

## Reading history
There is a NB API endpoint that allows to display previously saved history: 
GET `v2/ha-flows/{ha-flow-id}/history`
with optional parameters for filtering:
* timeFrom
* timeTo
* maxCount

This API returns a response with the payload JSON in a form (some lines are omitted for brevity):
```json
[
  {
    "clazz": "org.openkilda.messaging.payload.history.HaFlowHistoryEntry",
    "haFlowId": "ha-flow-1",
    "time": 1686668538.389000000,
    "timestampIso": "2023-06-13T15:02:18.389Z",
    "action": "HA-Flow update",
    "taskId": "39eab882-5395-43e1-aba0-81546803c875 : none",
    "payloads": [
      {
        "timestamp": 1686668538.389000000,
        "timestamp_iso": "2023-06-13T15:02:18.389Z",
        "action": "HA-Flow update",
        "details": null
      }, 
      // ... output omitted 
      {
        "timestamp": 1686668541.023000000,
        "timestamp_iso": "2023-06-13T15:02:21.023Z",
        "action": "Ha-flow resources have been deallocated",
        "details": "The ha-flow resources for ha-flow-1_9560b4ac-a69d-4152-9af1-526e3f1424cb / ha-flow-1_1e8ea267-7b69-4ed3-aac2-a701a2292fda have been deallocated"
      },
      {
        "timestamp": 1686668541.042000000,
        "timestamp_iso": "2023-06-13T15:02:21.042Z",
        "action": "The HA-flow status has been set to UP",
        "details": null
      }
    ],
    "dumps": [
      {
        "task_id": "39eab882-5395-43e1-aba0-81546803c875 : none",
        "dump_type": "STATE_AFTER",
        "ha_flow_id": "ha-flow-1",
        "shared_switch_id": "00:00:00:00:00:00:00:02",
        "shared_port": 23,
        "shared_outer_vlan": 0,
        "shared_inner_vlan": 0,
        "maximum_bandwidth": 0,
        "path_computation_strategy": "COST",
        "encapsulation_type": "TRANSIT_VLAN",
        "max_latency": 0,
        "max_latency_tier2": 0,
        "ignore_bandwidth": true,
        "periodic_pings": true,
        "pinned": false,
        "priority": 0,
        "strict_bandwidth": false,
        "description": "HA flow test description 23",
        "allocate_protected_path": true,
        "diverse_group_id": "24ad1528-0160-4a97-8202-b1cd9259ee41",
        "affinity_group_id": null,
        "status": "IN_PROGRESS",
        "status_info": null,
        "flow_time_create": "2023-06-13T13:54:43.923Z",
        "flow_time_modify": "2023-06-13T15:02:18.419Z",
        "ha_sub_flows": [
          {
            "ha_flow_id": "ha-flow-1",
            "ha_sub_flow_id": "ha-flow-1-a",
            "status": "UP",
            "endpoint_switch_id": "00:00:00:00:00:00:00:09",
            "endpoint_port": 22,
            "endpoint_vlan": 0,
            "endpoint_inner_vlan": 0,
            "description": "the first end point",
            "time_create": "2023-06-13T13:54:43.977Z",
            "time_modify": "2023-06-13T13:54:47.078Z"
          },
          {
            "ha_flow_id": "ha-flow-1",
            "ha_sub_flow_id": "ha-flow-1-b",
            "status": "UP",
            "endpoint_switch_id": "00:00:00:00:00:00:00:09",
            "endpoint_port": 21,
            "endpoint_vlan": 0,
            "endpoint_inner_vlan": 0,
            "description": "the second end point",
            "time_create": "2023-06-13T13:54:43.987Z",
            "time_modify": "2023-06-13T13:54:47.078Z"
          }
        ],
        "forward_path": {
          "ha_path_id": "ha-flow-1_0457d211-5620-47f2-af4e-d938a0a1aeac",
          "cookie": "0x400000000000558E",
          "shared_point_meter_id": null,
          "bandwidth": 0,
          "ignore_bandwidth": true,
          "time_create": "2023-06-13T13:54:45.713Z",
          "time_modify": "2023-06-13T15:02:00.937Z",
          "status": "ACTIVE",
          "shared_switch_id": "00:00:00:00:00:00:00:02",
          "y_point_switch_id": "00:00:00:00:00:00:00:09",
          "y_point_group_id": "GroupId(value=3311)",
          "y_point_meter_id": null,
          "paths": [
            [
              {
                "switch_id": "00:00:00:00:00:00:00:02",
                "input_port": 23,
                "output_port": 6
              },
              {
                "switch_id": "00:00:00:00:00:00:00:07",
                "input_port": 19,
                "output_port": 49
              },
              {
                "switch_id": "00:00:00:00:00:00:00:09",
                "input_port": 48,
                "output_port": 22
              }
            ],
            [
              {
                "switch_id": "00:00:00:00:00:00:00:02",
                "input_port": 23,
                "output_port": 6
              },
              {
                "switch_id": "00:00:00:00:00:00:00:07",
                "input_port": 19,
                "output_port": 49
              },
              {
                "switch_id": "00:00:00:00:00:00:00:09",
                "input_port": 48,
                "output_port": 21
              }
            ]
          ],
          "ha_sub_flows": [
            {
              "ha_flow_id": null,
              "ha_sub_flow_id": "ha-flow-1-a",
              "status": "UP",
              "endpoint_switch_id": "00:00:00:00:00:00:00:09",
              "endpoint_port": 22,
              "endpoint_vlan": 0,
              "endpoint_inner_vlan": 0,
              "description": "the first end point",
              "time_create": "2023-06-13T13:54:43.977Z",
              "time_modify": "2023-06-13T13:54:47.078Z"
            },
            {
              "ha_flow_id": null,
              "ha_sub_flow_id": "ha-flow-1-b",
              "status": "UP",
              "endpoint_switch_id": "00:00:00:00:00:00:00:09",
              "endpoint_port": 21,
              "endpoint_vlan": 0,
              "endpoint_inner_vlan": 0,
              "description": "the second end point",
              "time_create": "2023-06-13T13:54:43.987Z",
              "time_modify": "2023-06-13T13:54:47.078Z"
            }
          ],
          "ypoint_switch_id": "00:00:00:00:00:00:00:09",
          "ypoint_group_id": "GroupId(value=3311)",
          "ypoint_meter_id": null
        }
// ... output omitted
```
