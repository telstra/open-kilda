// This script rolls back the 2st stage of data migration (2-migration-changelog.xml)!
// IMPORTANT: The 3nd stage (3-cleanup-changelog.xml) cant be reverted!

MATCH ()-[ps:path_segment]->() DELETE ps;
MATCH ()-[fp:flow_path]->() DELETE fp;
MATCH (fc:flow_cookie) DELETE fc;
MATCH (fm:flow_meter) DETACH DELETE fm;
MATCH (tv:transit_vlan) DELETE tv;
MATCH ()-[f:flow]->() DELETE f;

MATCH (src:switch)-[of:old_flow]->(dst:switch)
WITH src, of, dst
CREATE (src)-[f:flow]->(dst)
SET f = of;

// This line is to be executed only if the previous queries have been executed successfully.
MATCH ()-[of:old_flow]->() DELETE of;