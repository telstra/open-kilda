## How to create a diagram for an FSM?
Squirrel framework has support of transforming an FSM to a DOT file, which a textual representation of an FSM metadata.
To create a DOT file you can execute in java:
```
DotVisitor visitor = SquirrelProvider.getInstance().newInstance(DotVisitor.class);
anInstanceOfYourFsm.accept(visitor);
visitor.convertDotFile("src/main/resources/YourFsm");
```
A DOT file, produced by this code, could be converted into a picture by using 
one of the converters available on the Internet.

Since we need to instantiate an FSM class to build a DOT file, we need to put this code into some module that has
all necessary dependencies. One of the ways is to use a unit test in the same package where an FSM is located.
For building a diagram we might not need to instantiate all the classes responsible for actions. For a diagram containing 
only states, events, and transitions, it is enough to copy-paste the FSM builder part without any `perform` methods.
An example of this approach is `HaFlowUpdateFsmDiagramTest`.
