CREATE CONSTRAINT ON (`connected_device`:`connected_device`) ASSERT `connected_device`.`entityId` IS UNIQUE;
CREATE CONSTRAINT ON (`connected_device`:`connected_device`) ASSERT `connected_device`.`unique_index` IS UNIQUE;
CREATE INDEX ON :`connected_device` (`flow_id`);
CREATE INDEX ON :`connected_device` (`source`);
CREATE INDEX ON :`connected_device` (`mac_address`);
CREATE INDEX ON :`connected_device` (`type`);
CREATE INDEX ON :`connected_device` (`chassis_id`);
CREATE INDEX ON :`connected_device` (`port_id`);
