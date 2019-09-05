DROP INDEX ON :`flow_meter` (`path_id`);
CREATE CONSTRAINT ON (`flow_meter`:`flow_meter`) ASSERT `flow_meter`.`path_id` IS UNIQUE;
CREATE CONSTRAINT ON (`lldp_resources`:`lldp_resources`) ASSERT `lldp_resources`.`entityId` IS UNIQUE;
CREATE CONSTRAINT ON (`lldp_resources`:`lldp_resources`) ASSERT `lldp_resources`.`flow_id` IS UNIQUE;
