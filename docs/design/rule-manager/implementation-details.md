# Implementation details for RuleManager library

Facade of RuleManager lib is implemented as an interface with two methods:
1. buildRulesForSwitch(SwitchId switchId, DataAdapter adapter)
2. buildRulesForPath(FlowPath flowPath, boolean filterOutUsedSharedRules, DataAdapter adapter)
3. buildRulesForYFlow(List<FlowPath> flowPaths, DataAdapter adapter)
4. buildIslServiceRules(SwitchId switchId, int port, DataAdapter adapter)

![Class diagram](class-diagram.png "class diagram")

Different adapter implementations may query database or read a file under the hood. Each operation should use separate adapter because they may cache data for better performance. All required parameters are provided as constructor params and filled by the calling component.

To generate service rules generators hierarchy (similar to current SwitchManager implementation) is used but the logic is moved from floodlight to RuleManager lib and result is in custom Command format. To generate flow related rules similar way is introduced. Custom Command format is close to OpenFlow format to simplify translation in speaker.

From floodlight perspective 3 new SpeakerCommands added: InstallSpeakerCommandsRequest, ModifySpeakerCommandsRequest and DeleteSpeakerCommandsRequest. Each request contains a collection of Commands of the same type (install/modify/delete). Commands inside collection may have dependepcies on each other. Requests should be sent to speaker one by one. Floodlight should build dependency tree, check it for errors (missing dependency, cycles etc), split collection to execution stages according to dependency, translate custom Commands into loxigen OpenFlow representation, send it to devices and process results. 

RuleManager returns collection of Commands and the calling component (Flow-HS/SwitchManager/Network) is responsible for encapsulating and sending this commands into floodlight in right order and correct batch options.

![Install/delete service isl rules sequence diagram](isl-service-rules.png "Install/delete service isl rules sequence diagram")
