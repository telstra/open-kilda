// This script rolls back the 1st stage of data migration (1-update-constraints-changelog.xml)!

DROP CONSTRAINT ON ( flow_meter:flow_meter ) ASSERT flow_meter.unique_index IS UNIQUE;

DROP INDEX ON :transit_vlan (path_id);

CREATE CONSTRAINT ON ( transit_vlan:transit_vlan ) ASSERT transit_vlan.path_id IS UNIQUE;

CREATE CONSTRAINT ON ( flow_segment:flow_segment ) ASSERT flow_segment.entityId IS UNIQUE;
