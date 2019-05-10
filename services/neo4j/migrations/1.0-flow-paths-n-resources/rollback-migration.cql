// This script rolls back the 2st stage of data migration (2-migration-changelog.xml)!
// IMPORTANT: The 3nd stage (3-cleanup-changelog.xml) cant be reverted!

MATCH (ps:path_segment) DETACH DELETE ps;
MATCH (fp:flow_path) DETACH DELETE fp;
MATCH (fc:flow_cookie) DELETE fc;
MATCH (fm:flow_meter) DETACH DELETE fm;
MATCH (tv:transit_vlan) DELETE tv;
MATCH (f:flow) DETACH DELETE f;