# Flow traffic mirroring

## Idea
There must be an ability to mirror traffic on a given flow 
and send it to a network endpoint (switch+port+inner_vlan+outer_vlan).

## Model
FlowPath has a list of FlowMirrorPoints, which will contain MirrorGroupId for multicasting on the switch. 
FlowMirrorPoint itself has a list of MirrorFlowPaths. The MirrorFlowPaths list contains the endpoint 
where traffic is mirrored and other necessary information to build a path.

![DB model](./model.png "DB model")

If the receiver point is on the mirroring source switch, then no segments will be built for this MirrorFlowPath. 
It will work in the same way as the paths in one-switch flow.

## API
* Create a mirror point:
  
  `PUT /flows/{flow_id}/mirror`
  
  payload:
  ```
  {
      "mirror_point_id": string,
      "flow_id": string,
      "mirror_direction": string [FORWARD|REVERSE],
      "mirror_point_switch_id": string,
      "sink_endpoint": {
         "switch_id": string,
         "port_number": int,
         "vlan_id": int,
         "inner_vlan_id": int
      }
  }
  ```

* Delete a mirror point:

  `DELETE /flows/{flow_id}/mirror/{mirror_point_id}`


* Get a list of mirror points by flow id:

  `GET /flows/{flow_id}/mirror`

  Response payload:
  ```
  {
      "flow_id": string,
      "points":[
          {
              "mirror_point_id": string,
              "mirror_direction": string [FORWARD|REVERSE],
              "mirror_point_switch_id": string,
              "sink_endpoint": {
                  "switch_id": string,
                  "port_number": int,
                  "vlan_id": int,
                  "inner_vlan_id": int
              }
          }
      ]
  }
  ```
  `mirror_point_id` must be specified by a user. It is unique for the given flow and can only consist of alphanumeric 
  characters, underscores, and hyphens. The length of this parameter must not exceed 100 characters.


* API that needs to be updated: 
  - Need to add information about the state of the mirror paths in the flow payload.
  - It is necessary to add information about built mirror paths to API `GET /flows/{flow_id}/path`

## Workflow

Creation and deletion of a flow mirror point in the FlowHSTopology:

![Create a flow traffic mirror point](./create-mirror-point.png "Create a flow traffic mirror point")
A mirror path must use the Transit VLAN encapsulation, if it is currently used for the corresponding flow. 
Also, this feature should work both in single-table mode and in multi-table modes.
![Delete a flow traffic mirror point](./delete-mirror-point.png "Delete a flow traffic mirror point")

Getting flow mirror points in the NbWorkerTopology:

![Get flow traffic mirror point](./get-mirror-point.png "Get a flow traffic mirror point")

## Affected kilda components
* need to add mirror groups to the Floodlight;
* need to update RerouteTopology to react on the network events for the mirror paths;
* add logic to the flow update operation when updating flow endpoints;
* update switch and flow validation.

## Limitations
It is allowed to use a transit switch as a mirror point only if the corresponding flow is pinned.

## Related issues
There are currently no asymmetric bandwidth ISLs in the system. This feature sets paths in one direction only. 
This results in asymmetric ISLs appearing in the system. This effect on the system requires more in-depth research.

A possible solution to this issue is to create a dummy path that will consume bandwidth in the opposite direction.

## Switch rules
Existing actions set for any type of existing OF flows (ingress, transit, egress) 
will be replaced with a "goto group" action instead of an "output port" action.
The group will have 2 or more buckets: one will represent output to the flow or ISL port, 
the rest will represent mirror actions set (i.e. routing to the mirror paths).

## FSM diagrams

### FlowMirrorPointCreateFsm
![FlowMirrorPointCreateFsm](./flow-create-mirror-point-fsm.png "FlowMirrorPointCreateFsm")

### FlowMirrorPointDeleteFsm
![FlowMirrorPointDeleteFsm](./flow-delete-mirror-point-fsm.png "FlowMirrorPointDeleteFsm")
