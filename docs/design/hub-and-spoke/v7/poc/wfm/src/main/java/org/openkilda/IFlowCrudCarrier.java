package org.openkilda;

import org.openkilda.model.FlCommand;

public interface IFlowCrudCarrier {
    void registerCallback(String key);
    void cancelCallback(String key);
    void installRule(String key, FlCommand command);
    void checkRule(String key, FlCommand command);
    void response(String key, String response);
}
