# FSM Overview
FSM stands for [finite-state machine](https://en.wikipedia.org/wiki/Finite-state_machine),
it is a model for computation of some process that changes its state in response to some input.

In OpenKilda, FSM is a central component for computing processes such as Flow creation, Switch synchronization, and others. 
Input for FSMs could be users input or a response from services, for example responses from switches about rules installation.

[Squirrel State Machine framework](https://hekailiang.github.io/squirrel/) has been chosen as the implementation of the FSM orchestrator.
It is agreed to name classes responsible for the processes computation using this framework with the suffix `Fsm`:
HaFlowUpdateFsm, SwitchSyncFsm.
In the context of OpenKilda, "FSM" often is a casual broad term that refer to infrastructure or a family of classes that are responsible
for handling some specific process.
