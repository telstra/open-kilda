# Evolve PCE with weights computation strategies

## Goals
For now PCE uses only cost field as graph edges weights

While path computing logic going to be more complicated - we need to take several factors\costs to compute final edge\node weight, and keep this things maintainable.
Instead of current "computer class per cost strategy", proposed to use one computer class parameterized with "get weight" strategy function.

## Proposed changes
- Introduce PCE data model instead of reusing DAO: for storing graph weighs by features and more convenient logging
- Decouple AvailableNetwork creation: it will use own building strategies.
- Introduce weights computing strategies: like weight getters above data model, defines what weigh features will be used in path computation and in that proportions

Consequently, weights computation will occur at runtime, by calling weigh strategy.

### Weight strategy examples
Simplest strategy is some property getter, for example ISLs `getCost`.

Boolean properties may be considered as configurable constants, with usage as addendum in proper strategies:
- In ISL maintenance scenario strategy may looks like `isl cost + isl maitenance cost`
- In diverse scenario on  `sw1 -> isl -> sw2` graph edge strategy may looks like `sw1 diversity cost + isl cost + isl diversity cost + sw2 diversity cost`.

### Sequence Diagram
![Evolve PCE with weights computation strategies](./pce-weights-strategies.png)
