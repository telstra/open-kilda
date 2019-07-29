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

import lombok.extern.slf4j.Slf4j;
import net.floodlightcontroller.core.IOFSwitch;
import net.floodlightcontroller.core.internal.TableFeatures;
import org.apache.commons.lang3.StringUtils;
import org.projectfloodlight.openflow.protocol.OFTableFeatureProp;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenter;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropExperimenterMiss;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropType;
import org.projectfloodlight.openflow.protocol.OFTableFeaturePropWildcards;
import org.projectfloodlight.openflow.protocol.OFTableFeatures;
import org.projectfloodlight.openflow.protocol.OFTableFeaturesStatsReply;
import org.projectfloodlight.openflow.protocol.ver13.OFTableFeaturePropTypeSerializerVer13;
import org.projectfloodlight.openflow.protocol.ver14.OFTableFeaturePropTypeSerializerVer14;
import org.projectfloodlight.openflow.types.DatapathId;
import org.projectfloodlight.openflow.types.TableId;

@Slf4j
public class SwitchPipelineAdapter {
    private final IOFSwitch sw;

    /**
     * Extract "normalized" table feature property type.
     */
    public static OFTableFeaturePropType getTableFeaturePropType(OFTableFeatureProp p) {
        switch (p.getVersion()) {
            case OF_13:
                return OFTableFeaturePropTypeSerializerVer13.ofWireValue((short) p.getType());
            case OF_14:
                return OFTableFeaturePropTypeSerializerVer14.ofWireValue((short) p.getType());
            default:
                throw new IllegalArgumentException(
                        "OFVersion " + p.getVersion().toString() + " does not support OFTableFeature messages.");
        }
    }

    public SwitchPipelineAdapter(IOFSwitch sw) {
        this.sw = sw;
    }

    /**
     * Dump switch pipeline (use stored data collected during handshake).
     */
    public void dumpPipeline() {
        pipelineLogIntro();
        for (TableId tableId : sw.getTables()) {
            dumpSwitchTableDetails(sw.getTableFeatures(tableId));
        }
    }

    /**
     * Dump switch pipeline.
     */
    public void dumpPipeline(OFTableFeaturesStatsReply pipelineReply) {
        pipelineLogIntro();
        for (OFTableFeatures entry : pipelineReply.getEntries()) {
            dumpSwitchTableDetails(TableFeatures.of(entry));
        }
    }

    private void pipelineLogIntro() {
        logSwitch(sw.getId(), String.format("OF version %s", sw.getOFFactory().getVersion()));
    }

    private void dumpSwitchTableDetails(TableFeatures features) {
        TableId tableId = features.getTableId();

        logSwitchTableFeature(tableId, "name", features.getTableName());
        logSwitchTableFeature(tableId, "max-entries", String.valueOf(features.getMaxEntries()));
        logSwitchTableFeature(tableId, "matadata-match-bits", features.getMetadataMatch().toString());
        logSwitchTableFeature(tableId, "metadata-write-bits", features.getMetadataWrite().toString());

        logSwitchTableFeature(tableId, "instructions",
                              features.getPropInstructions().getInstructionIds());
        logSwitchTableFeature(tableId, "instruction (missing)s",
                              features.getPropInstructionsMiss().getInstructionIds());

        logSwitchTableFeature(tableId, "match", features.getPropMatch().getOxmIds());

        logSwitchTableFeature(tableId, "apply-action",
                              features.getPropApplyActions().getActionIds());
        logSwitchTableFeature(tableId, "apply-action (missing)",
                              features.getPropApplyActionsMiss().getActionIds());

        logSwitchTableFeature(tableId, "apply-set-field", features.getPropApplySetField().getOxmIds());
        logSwitchTableFeature(tableId, "apply-set-field", features.getPropApplySetFieldMiss().getOxmIds());

        logSwitchTableFeature(tableId, "write-action",
                              features.getPropWriteActions().getActionIds());
        logSwitchTableFeature(tableId, "write-action (miss)",
                              features.getPropWriteActionsMiss().getActionIds());

        logSwitchTableFeature(tableId, "write-set-field", features.getPropWriteSetField().getOxmIds());
        logSwitchTableFeature(tableId, "write-set-field (miss)", features.getPropWriteSetFieldMiss().getOxmIds());

        logSwitchTableFeature(tableId, "next-table", features.getPropNextTables().getNextTableIds());
        logSwitchTableFeature(tableId, "next-table (miss)", features.getPropNextTablesMiss().getNextTableIds());

        OFTableFeaturePropWildcards wildcards = features.getPropWildcards();
        if (wildcards != null) {
            logSwitchTableFeature(tableId, "wildcard", wildcards.getOxmIds());
        }

        OFTableFeaturePropExperimenter e = features.getPropExperimenter();
        if (e != null) {
            logSwitchTableFeature(tableId, "experimenter", String.format(
                    "id=%d, subtype=%d, payload %d bytes",
                    e.getExperimenter(), e.getSubtype(),
                    e.getExperimenterData() == null ? 0 : e.getExperimenterData().length));
        }
        OFTableFeaturePropExperimenterMiss ee = features.getPropExperimenterMiss();
        if (ee != null) {
            logSwitchTableFeature(tableId, "experimenter (miss)", String.format(
                    "id=%d, subtype=%d, payload %d bytes",
                    ee.getExperimenter(), ee.getSubtype(), ee.getExperimenterData().length));
        }
    }

    private void logSwitchTableFeature(TableId tableId, String kind, Iterable<?> nameSequence) {
        logSwitchTableFeature(tableId, kind, StringUtils.join(nameSequence, ","));
    }

    private void logSwitchTableFeature(TableId tableId, String kind, String message) {
        logSwitchTable(sw.getId(), tableId, String.format("features - %s: %s", kind, message));
    }

    private void logSwitchTable(DatapathId dpId, TableId tableId, String message) {
        logSwitch(dpId, String.format("table %s %s", tableId, message));
    }

    private void logSwitch(DatapathId dpid, String message) {
        log.info("switch {} {}", dpid, message);
    }
}
