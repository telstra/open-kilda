package org.openkilda.wfm;

import org.apache.storm.tuple.Tuple;

public abstract class AbstractEmbeddedAction extends AbstractAction {
    public AbstractEmbeddedAction(IKildaBolt bolt, Tuple tuple) {
        super(bolt, tuple);
    }

    @Override
    protected void commit() {}
}
