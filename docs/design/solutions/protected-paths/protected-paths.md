# Protected path for flow

## Goals
Calculate and deploy protected diverse path for flow, so if the primary path will fail we can switch traffic fast to protected path.

## API changes
- flow object should be extended with an boolean parameter allocate-protected-path with values false(default) and true
- /flows/{flow-id}/path should also return `protected_path` with the protected path and its diversity factor in comparison to the primary one.

## DB changes
- Flow: add allocate-protected-path, primary-path-id, protected-path-id properties.
- FlowSegment: path-id property.

Path will store as FlowSegments in Neo4J as usual. Flow primary-path-id property match the current primary path (FlowSegments with path-id chain).

Protected path must be fully diverse from the primary one, in terms ISL or switches (configurable). If no overlapping protected path cannot be find, flow move to DOWN state. 

### Sequence Diagrams
![Create protected flow](protected-paths-create.png)
![Reroute protected flow](protected-paths-reroute.png)

### Limitations
We still needed control plane to perform switching to protected path, with several controller roundtrips.
