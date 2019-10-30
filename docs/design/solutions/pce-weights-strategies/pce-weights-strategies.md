# PCE weight computation strategies

## Goals

While path computing logic going to be more complicated - we need to take several factors\costs to compute final edge\node weight, and keep this things maintainable.
One path computer class parameterized with "get weight" strategy function is introduced instead of "computer class per cost strategy" design.

## Features
- Introduced PCE data model instead of reusing DAO: for storing graph weighs by features and more convenient logging
- Decoupled AvailableNetwork creation: it has own building strategies.
- Introduced weight computing strategies: edge data model to long functions, defines what weigh features will be used in path computation and in what proportions

Consequently, weights computation will occur at runtime, by calling weigh strategy.

### Weight strategy examples
Simplest strategy is some property getter, for example ISLs `getCost` or `getLatency`.

Boolean properties may be considered as configurable constants, with usage as addendum in proper strategies:
- In ISL maintenance scenario strategy may looks like `isl cost + isl maitenance cost` or `isl latency + isl maintenance latency modifier`
- In diverse scenario on  `sw1 -> isl -> sw2` graph edge strategy may looks like `sw1 diversity cost + isl cost + isl diversity cost + sw2 diversity cost`.

### Sequence Diagram
![Evolve PCE with weights computation strategies](./pce-weights-strategies.png)
