# Flow creation with hub and spoke design

## Current implementation

There are multiple issues occur and might occur because of the design of crud (create operations in particular):
- rules might not be installed due to some errors or switches disappearance during installation
- there is still one last cache where we store flow resources
- it is almost impossible to track down what went wrong during flow creation
- it is hard to support current code base without FSM
![Flow create current version](flow-create-current.png "Flow create current version")

## Flow creation using hub and spoke design
![Flow create design](flow-create-hs.png "Flow create design")
