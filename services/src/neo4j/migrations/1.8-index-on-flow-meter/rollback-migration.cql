// This script rolls back the 2st stage of data migration (2-migration-changelog.xml)!

MATCH (sw:switch), (fm:flow_meter)
WHERE sw.name = fm.switch_id
CREATE (sw)-[:owns]->(fm)
