/* Copyright 2019 Telstra Open Source
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

package org.openkilda.floodlight.utils;

import lombok.Getter;
import org.projectfloodlight.openflow.protocol.OFFactory;
import org.projectfloodlight.openflow.protocol.OFFlowMod;
import org.projectfloodlight.openflow.types.TableId;

public abstract class OfFlowModBuilderFactory {
    @Getter
    private final int basePriority;

    public OfFlowModBuilderFactory(int basePriority) {
        this.basePriority = basePriority;
    }

    public OFFlowMod.Builder makeBuilder(OFFactory of, int priorityOffset) {
        return makeBuilder(of, null, priorityOffset);
    }

    public OFFlowMod.Builder makeBuilder(OFFactory of, TableId tableId) {
        return makeBuilder(of, tableId, 0);
    }

    /**
     * Make OF flow-mod builder.
     */
    public OFFlowMod.Builder makeBuilder(OFFactory of, TableId tableId, int priorityOffset) {
        OFFlowMod.Builder builder = makeBuilder(of);
        if (tableId != null) {
            builder = setTableId(builder, tableId);
        }
        return setPriority(builder, priorityOffset);
    }

    public abstract OFFlowMod.Builder makeBuilder(OFFactory of);

    public abstract OFFlowMod.Builder setTableId(OFFlowMod.Builder builder, TableId tableId);

    public OFFlowMod.Builder setPriority(OFFlowMod.Builder builder, int priorityOffset) {
        return builder.setPriority(basePriority + priorityOffset);
    }

    public static Factory makeFactory() {
        return new Factory();
    }

    public static final class Factory {
        private Integer basePriority;
        private Boolean multiTable;
        private ModAction action;

        public Factory basePriority(int basePriority) {
            this.basePriority = basePriority;
            return this;
        }

        public Factory multiTable(boolean isMultiTable) {
            multiTable = isMultiTable;
            return this;
        }

        public Factory actionAdd() {
            action = ModAction.ADD;
            return this;
        }

        public Factory actionDelete() {
            action = ModAction.DELETE;
            return this;
        }

        /**
         * Produce concrete instance of `OfFlowModBuilderFactory`.
         */
        public OfFlowModBuilderFactory make() {
            ensureMandatoryFieldsSet();

            OfFlowModBuilderFactory result;
            switch (action) {
                case ADD:
                    result = makeAdd();
                    break;
                case DELETE:
                    result = makeDelete();
                    break;
                default:
                    throw new IllegalStateException(String.format("Unsupported flow MOD action \"%s\"", action));
            }
            return result;
        }

        private OfFlowModBuilderFactory makeAdd() {
            ensureMandatoryFieldsSet(true);

            if (multiTable != null && multiTable) {
                return new OfFlowModAddMultiTableMessageBuilderFactory(basePriority);
            } else {
                return new OfFlowModAddSingleTableMessageBuilderFactory(basePriority);
            }
        }

        private OfFlowModBuilderFactory makeDelete() {
            ensureMandatoryFieldsSet(true);

            if (multiTable != null && multiTable) {
                return new OfFlowModDelMultiTableMessageBuilderFactory(basePriority);
            } else {
                return new OfFlowModDelSingleTableMessageBuilderFactory(basePriority);
            }
        }

        private void ensureMandatoryFieldsSet() {
            ensureMandatoryFieldsSet(false);
        }

        private void ensureMandatoryFieldsSet(boolean isIgnoreAction) {
            if (basePriority == null) {
                throw new IllegalStateException("Base priority is not defined");
            }
            if (multiTable == null) {
                throw new IllegalStateException("MultiTable mode is not defined");
            }
            if (! isIgnoreAction && action == null) {
                throw new IllegalStateException("Flow MOD action is not defined");
            }
        }
    }

    private enum ModAction {
        ADD, DELETE
    }
}
