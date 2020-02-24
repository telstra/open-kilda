/* Copyright 2018 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.wfm.topology.ping.model;

import org.openkilda.messaging.model.Ping;

import lombok.Builder;
import lombok.Data;

@Data
public class PingObserver {
    private final long failDelay;
    private final long failReset;
    private final long garbageDelay;

    private State state = State.UNKNOWN;
    private State realState = State.UNKNOWN;
    private long lastStateTransitionAt = 0L;

    @Builder
    public PingObserver(long failDelay, long failReset) {
        this.failDelay = failDelay;
        this.failReset = failReset;
        this.garbageDelay = failReset;
    }

    public void markOperational(long timestamp) {
        dispatch(Event.SUCCESS, timestamp);
    }

    public void markFailed(long timestamp, Ping.Errors error) {
        dispatch(errorToEventMap(error), timestamp);
    }

    public State timeTick(long timestamp) {
        dispatch(Event.TIME, timestamp);
        return state;
    }

    public State getState() {
        return state;
    }

    /**
     * Expose failing state of this ping "stream".
     */
    public boolean isFail() {
        boolean result;
        switch (state) {
            case FAIL:
            case UNRELIABLE:
                result = true;
                break;

            default:
                result = false;
        }
        return result;
    }

    private void dispatch(Event event, long timestamp) {
        switch (realState) {
            case UNKNOWN:
            case GARBAGE:
                stateUnknown(event, timestamp);
                break;
            case OPERATIONAL:
                stateOperational(event, timestamp);
                break;
            case PRE_FAIL:
                statePreFail(event, timestamp);
                break;
            case FAIL:
                stateFail(event, timestamp);
                break;
            case PRE_UNRELIABLE:
                statePreUnreliable(event, timestamp);
                break;
            case UNRELIABLE:
                stateUnreliable(event, timestamp);
                break;

            default:
                throw new IllegalArgumentException(String.format(
                        "Unsupported %s value %s", State.class.getName(), realState));
        }
    }

    private void stateUnknown(Event event, long timestamp) {
        switch (event) {
            case TIME:
                if (lastStateTransitionAt + garbageDelay < timestamp) {
                    doStateTransition(State.GARBAGE, timestamp);
                }
                break;
            case SUCCESS:
                doStateTransition(State.OPERATIONAL, timestamp);
                break;
            case ERROR:
                doStateTransition(State.PRE_FAIL, timestamp);
                break;
            case OPERATIONAL_ERROR:
                doStateTransition(State.PRE_UNRELIABLE, timestamp);
                break;

            default:
        }
    }

    private void stateOperational(Event event, long timestamp) {
        switch (event) {
            case ERROR:
                doStateTransition(State.PRE_FAIL, timestamp);
                break;
            case OPERATIONAL_ERROR:
                doStateTransition(State.PRE_UNRELIABLE, timestamp);
                break;

            default:
        }
    }

    private void statePreFail(Event event, long timestamp) {
        switch (event) {
            case TIME:
                if (lastStateTransitionAt + failDelay < timestamp) {
                    doStateTransition(State.FAIL, timestamp);
                }
                break;
            case SUCCESS:
                doStateTransition(State.OPERATIONAL, timestamp);
                break;
            case OPERATIONAL_ERROR:
                doStateTransition(State.PRE_UNRELIABLE);
                break;

            default:
        }
    }

    private void stateFail(Event event, long timestamp) {
        switch (event) {
            case TIME:
                if (lastStateTransitionAt + failReset < timestamp) {
                    doStateTransition(State.UNKNOWN, timestamp);
                }
                break;
            case SUCCESS:
                doStateTransition(State.OPERATIONAL, timestamp);
                break;
            case OPERATIONAL_ERROR:
                doStateTransition(State.PRE_UNRELIABLE, timestamp);
                break;

            default:
        }
    }

    private void statePreUnreliable(Event event, long timestamp) {
        switch (event) {
            case TIME:
                if (lastStateTransitionAt + failDelay < timestamp) {
                    doStateTransition(State.UNRELIABLE, timestamp);
                }
                break;
            case SUCCESS:
                doStateTransition(State.OPERATIONAL, timestamp);
                break;
            case ERROR:
                doStateTransition(State.PRE_FAIL);
                break;

            default:
        }
    }

    private void stateUnreliable(Event event, long timestamp) {
        switch (event) {
            case TIME:
                if (lastStateTransitionAt + failReset < timestamp) {
                    doStateTransition(State.UNKNOWN, timestamp);
                }
                break;
            case SUCCESS:
                doStateTransition(State.OPERATIONAL, timestamp);
                break;
            case ERROR:
                doStateTransition(State.PRE_FAIL, timestamp);
                break;

            default:
        }
    }

    private void doStateTransition(State target, long timestamp) {
        doStateTransition(target);
        this.lastStateTransitionAt = timestamp;
    }

    private void doStateTransition(State target) {
        realState = target;
        updateState();
    }

    private void updateState() {
        switch (realState) {
            case OPERATIONAL:
            case FAIL:
            case UNRELIABLE:
            case GARBAGE:
            case UNKNOWN:
                state = realState;
                break;

            default:
        }
    }

    private static Event errorToEventMap(Ping.Errors error) {
        switch (error) {
            case SOURCE_NOT_AVAILABLE:
            case DEST_NOT_AVAILABLE:
                return Event.OPERATIONAL_ERROR;
            default:
                return Event.ERROR;
        }
    }

    public enum State {
        UNKNOWN,
        OPERATIONAL,
        PRE_FAIL,
        FAIL,
        PRE_UNRELIABLE,
        UNRELIABLE,
        GARBAGE
    }

    private enum Event {
        TIME,
        SUCCESS,
        ERROR,
        OPERATIONAL_ERROR
    }
}
