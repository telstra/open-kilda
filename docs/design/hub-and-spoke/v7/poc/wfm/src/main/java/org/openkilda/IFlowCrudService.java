package org.openkilda;

import org.openkilda.model.FlowCreate;

public interface IFlowCrudService {

    void handleFlowCreate(String key, FlowCreate flow);

    void handleAsyncResponseFromWorker(String key, String message);

    void handleTaskTimeout(String key);
}
