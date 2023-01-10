/* Copyright 2023 Telstra Open Source
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

package org.openkilda;

import com.orientechnologies.orient.core.db.ODatabaseSession;
import com.orientechnologies.orient.core.db.OrientDB;
import com.orientechnologies.orient.core.db.OrientDBConfig;
import liquibase.change.custom.CustomTaskChange;
import liquibase.change.custom.CustomTaskRollback;
import liquibase.database.Database;
import liquibase.exception.CustomChangeException;
import liquibase.exception.RollbackImpossibleException;
import liquibase.exception.SetupException;
import liquibase.exception.ValidationErrors;
import liquibase.resource.ResourceAccessor;

public class FlowMirrorMigration implements CustomTaskChange, CustomTaskRollback {
    private final String host = System.getenv("KILDA_ORIENTDB_HOST");
    private final String dbName = System.getenv("KILDA_ORIENTDB_DB_NAME");
    private final String user = System.getenv("KILDA_ORIENTDB_USER");
    private final String password = System.getenv("KILDA_ORIENTDB_PASSWORD");
    private ODatabaseSession db;

    @SuppressWarnings({"UnusedDeclaration", "FieldCanBeLocal"})
    private ResourceAccessor resourceAccessor;

    @Override
    public void execute(Database database) throws CustomChangeException {
        String script = "BEGIN;\n";
        script += "FOREACH ($path IN (SELECT FROM flow_mirror_path)) {\n";
        script += "LET $fm = INSERT INTO flow_mirror (flow_mirror_id, forward_path_id, mirror_switch_id, "
                + "egress_switch_id, status, egress_port, egress_outer_vlan, egress_inner_vlan) VALUES ($path.path_id, "
                + "$path.path_id, $path.mirror_switch_id, $path.egress_switch_id, $path.status, $path.egress_port, "
                + "$path.egress_outer_vlan, $path.egress_inner_vlan);\n";
        script += "UPDATE EDGE owns SET in = $fm.@rid[0] UPSERT WHERE @rid = $path.in_owns[0];\n";
        script += "CREATE EDGE owns FROM $fm TO $path;\n";
        script += "};\n";
        script += "COMMIT;\n";

        db.execute("sql", script);
    }

    @Override
    public String getConfirmationMessage() {
        return "Flow mirror migration successfully applied";
    }

    @Override
    public void setUp() throws SetupException {
        try (OrientDB orient = new OrientDB("remote:" + host, OrientDBConfig.defaultConfig())) {
            db = orient.open(dbName, user, password);
        } catch (Exception e) {
            throw new SetupException(e);
        }
    }

    @Override
    public void setFileOpener(ResourceAccessor resourceAccessor) {
        this.resourceAccessor = resourceAccessor;
    }

    @Override
    public ValidationErrors validate(Database database) {
        return null;
    }

    @Override
    public void rollback(Database database) throws CustomChangeException, RollbackImpossibleException {
        String script = "BEGIN;\n";
        script += "FOREACH ($fm IN (SELECT FROM flow_mirror)) {\n";
        script += "UPDATE EDGE owns SET in = $fm.out_owns.in[0] UPSERT WHERE @rid = $fm.in_owns[0];\n";
        script += "};\n";
        script += "DELETE FROM flow_mirror UNSAFE;\n";
        script += "COMMIT;\n";

        db.execute("sql", script);
    }
}
