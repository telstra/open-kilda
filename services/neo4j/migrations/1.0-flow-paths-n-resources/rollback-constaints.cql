// This script rolls back the 1st stage of data migration (1-update-constraints-changelog.xml)!

DROP CONSTRAINT ON (flow:flow) ASSERT flow.flow_id IS UNIQUE;

CREATE CONSTRAINT ON (flow:flow) ASSERT flow.flowid IS UNIQUE;

CREATE CONSTRAINT ON ( flow_segment:flow_segment ) ASSERT flow_segment.entityId IS UNIQUE;

CREATE CONSTRAINT ON (port:port) ASSERT port.entityId IS UNIQUE;

CREATE CONSTRAINT ON ( transit_vlan:transit_vlan ) ASSERT transit_vlan.path_id IS UNIQUE;

// Check for existence of the index to avoid duplication!!!
CREATE INDEX ON :flow_path(flow_id);

CREATE INDEX ON :path_segment(path_id);

DROP INDEX ON :transit_vlan (path_id);

DROP CONSTRAINT ON ( flow_meter:flow_meter ) ASSERT flow_meter.unique_index IS UNIQUE;

