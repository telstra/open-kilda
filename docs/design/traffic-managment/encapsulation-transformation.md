# Customer traffic encapsulation transformation

Open-kilda support VLAN encapsulation of customer traffic. Flow's endpoint
encapsulations can be: full-port, VLAN, QinQ.

Because of flow's endpoints encapsulation setup can differ from each other
(one endpoint defined as full-port and opposite endpoint defined as VLAN
tagged) open-kilda must perform not only traffic delivery but traffic
transformation also.

Open-kilda does not know actual traffic encapsulation i.e., even if flow
endpoint defined as full-port actual traffic can be VLAN tagged(on any
depth). So defined encapsulation on flow endpoints can be used only to
make proper matching on ingress endpoint and to calculate/apply a required
transformation to the traffic somewhere before emitting it to the
customer port.

So traffic transformation can be treated in the following way (simplified view):
- pop all VLAN tags defined on ingress endpoint
- push all VLAN tags defined on egress endpoint
- keep all nested VLAN tags (if any) not defined on flow endpoints

## Implementation idea
Ingress endpoint must use 2 VLAN(up to 2) layers match (also it should try
to detect is there really 2 level of VLANs encapsulation).

Transformation can be performed on ingress switch, egress switch or both. But
due to the goal to keep customer traffic untouched across the whole path, it
must be done only on egress switch.

Packet manipulation by endpoint (do encapsulation transformation on egress endpoint):
* [ingress] - use multiple VLAN match (if required)
* [ingress] - push transit encapsulation
* [transit] - match transit encapsulation, apply transit routing
* [egress] - pop transit encapsulation
* [egress] - pop all defined VLAN tags (from ingress side)
* [egress] - push all defined VLAN tags

## Flow API update (v1)
Proposed flow create/update payload change:
```
--- a.json	2019-07-09 15:12:11.282756047 +0300
+++ b.json	2019-07-09 15:23:04.289046084 +0300
@@ -3,12 +3,14 @@
   "source": {
     "switch-id": "00:00:00:22:3d:6c:00:b8",
     "port-id": 22,
-    "vlan-id": 100
+    "vlan-id": 100,
+    "nested-vlan-id": null
   },
   "destination": {
     "switch-id": "00:00:00:22:3d:5a:04:87",
     "port-id": 23,
     "vlan-id": 200,
+    "nested-vlan-id": 300
   },
   "maximum-bandwidth": 1000,
   "ignore_bandwidth": false,
```
New field `nested-vlan-id` is added into flow's endpoint. According to it's name it
can define nested VLAN tag.

Whole payload will become:
```json
{
  "flowid": "flow-0",
  "source": {
    "switch-id": "00:00:00:22:3d:6c:00:b8",
    "port-id": 22,
    "vlan-id": 100,
    "nested-vlan-id": null
  },
  "destination": {
    "switch-id": "00:00:00:22:3d:5a:04:87",
    "port-id": 23,
    "vlan-id": 200,
    "nested-vlan-id": 300
  },
  "maximum-bandwidth": 1000,
  "ignore_bandwidth": false,
  "periodic-pings": true,
  "allocate_protected_path": false,
  "description": "tests",
  "created": "2019-07-01T13:09:15.313Z",
  "last-updated": "2019-07-01T13:09:15.499Z",
  "status": "In progress",
  "pinned": false,
  "encapsulation-type": "transit_vlan"
}
```

Existing `vlan-id` field keeps it's current meaning and define outermost VLAN
tag. Nested VLAN tag can be defined via `nested-vlan-id` field.

Validation process must reject endpoint with defined `nested-vlan-id` and
undefined (or equal to 0) `vlan-id` fields.
