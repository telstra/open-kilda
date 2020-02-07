DROP INDEX ON :flow_meter (flow_id);
DROP CONSTRAINT ON (connected_device:connected_device) ASSERT connected_device.entityId IS UNIQUE;
DROP CONSTRAINT ON (connected_device:connected_device) ASSERT connected_device.unique_index IS UNIQUE;
DROP INDEX ON :connected_device (flow_id);
DROP INDEX ON :connected_device (source);
DROP INDEX ON :connected_device (mac_address);
DROP CONSTRAINT ON (lldp_resources:lldp_resources) ASSERT lldp_resources.flow_id IS UNIQUE;
DROP CONSTRAINT ON (lldp_resources:lldp_resources) ASSERT lldp_resources.entityId IS UNIQUE;
