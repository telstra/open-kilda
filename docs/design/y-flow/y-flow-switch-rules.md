# Y-flows

## Switch rules

A y-flow requires several meters and rules to be installed. See [Y-flow design](y-flow-design.md)
The purpose of y-flow meters is to limit summary bandwidth of sub-flows at points where an individual flow meter can't properly control utilization: the shared endpoint and the y-point. Y-flow rules, for their part, apply the meters to all sub-flows.

Y-flow rules are built in the same way as flow ingress / transit rules, but the Y-flow rules have the higher priority, different cookie type and apply shared y-flow meters. 
The higher priority is needed to overcome the rules installed by sub-flows: a y-flow rule handles a packet instead of a sub-flow rule. 
The new cookie type (CookieType.YFLOW_SEGMENT) is needed to distinguish y-flow from ordinary flow rules. This type is represented by FlowSegmentCookie and utilized by RuleManager to produce y-flow specific OF commands.

For each sub-flow we build 2 YFLOW_SEGMENT rules based on ingress / transit rules, and install them on corresponding switches: the shared endpoint and the y-point.

### Shared endpoint

A y-flow requires a dedicated meter on the shared endpoint. The rate and burst size corresponds to the y-flow bandwidth.

For each sub-flow and sub-flow forward path (from Z-end to A-end / B-end), a dedicated y-flow rule is to be installed. It's built based on ingress rule. The flow cookie is kept the same, but cookie type is set to YFLOW_SEGMENT. 

A single meter is used for all forward paths instead of individual sub-flow meters. 

### Y-point

A y-flow may have a y-point for main paths and another y-point (called protected-y-point) for protected paths. On each y-point, a y-flow requires a dedicated meter. The rate and burst size corresponds to y-flow bandwidth.

For each sub-flow and sub-flow reverse path (from A-end / B-end to Z-end), a dedicated y-flow rule is to be installed. It's built based on transit rule. The flow cookie is kept the same, but cookie type is set to YFLOW_SEGMENT.
