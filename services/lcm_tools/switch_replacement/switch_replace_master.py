import json

from py2neo import Graph, Path, remote


class KildaDBActions(object):

    def __init__(self, db_addr, user, passwd, port, secure, scheme):
        self._db_addr = db_addr
        self._user = user
        self._passwd = passwd
        self._port = port
        self._secure = secure
        self._scheme = scheme
        self.graph = None

        self._get_db_connection()

    def _get_db_connection(self):
        uri = "{}:{}/db/data".format(
            self._scheme, self._db_addr, self._port)
        self.graph = Graph(
            uri,
            user=self._user,
            password=self._passwd,
            secure=False,
            http_port=self._port
        )


    def _find_all_affected_flow_segments(self, old_switch_id):
        """
        MATCH() -[rel:flow_segment]-> ()
        WHERE rel.dst_switch = "{}" OR rel.src_switch = "{}"
        RETURN rel;
        """
        query = """
        MATCH() -[rel:flow_segment]-> ()
        WHERE rel.dst_switch = "{sw_id}" OR rel.src_switch = "{sw_id}"
        RETURN distinct rel;
        """.format(sw_id=old_switch_id)
        print query
        return self.graph.data(query)

    def _find_all_affected_flows(self, old_switch_id, links):
        """
        MATCH() -[rel:flow]-> ()
        WHERE rel.dst_switch = "{sw_id}" OR rel.src_switch = "{sw_id}"
              OR rel.flowid IN [...]
        RETURN rel;
        """
        query = """
        MATCH() -[rel:flow]-> ()
        WHERE rel.dst_switch = "{sw_id}" OR rel.src_switch = "{sw_id}" OR rel.flowid IN {links}
        RETURN distinct rel;
        """.format(sw_id=old_switch_id, links=json.dumps(links))
        print query
        return self.graph.data(query)

    def _migrate_segments(self, affected_segments, old_switch_id, new_switch_id,
            replug_port_map):
        for segment in affected_segments:
            if segment['rel'].get('dst_switch') == old_switch_id:
                segment['rel']['dst_switch'] = new_switch_id
                segment['rel']['dst_port'] = replug_port_map[str(segment['rel'].get('dst_port'))]

            if segment['rel'].get('src_switch') == old_switch_id:
                segment['rel']['src_switch'] = new_switch_id
                segment['rel']['src_port'] = replug_port_map[str(segment['rel'].get('src_port'))]

        return affected_segments

    def _migrate_flows(self, affected_flows, old_switch_id, new_switch_id,
            replug_port_map):
        for flow in affected_flows:
            if flow['rel'].get('dst_switch') == old_switch_id:
                flow['rel']['dst_switch'] = new_switch_id
                flow['rel']['dst_port'] = replug_port_map[str(flow['rel'].get('dst_port'))]

            if flow['rel'].get('src_switch') == old_switch_id:
                flow['rel']['src_switch'] = new_switch_id
                flow['rel']['src_port'] = replug_port_map[str(flow['rel'].get('src_port'))]


            flow_path = json.loads(flow['rel'].get("flowpath"))
            for path_segment in flow_path['path']:
                if path_segment['switch_id'] == old_switch_id:
                    path_segment['switch_id'] = new_switch_id
                    path_segment['port_no'] = replug_port_map[str(
                        path_segment['port_no'])]

            flow['rel']["flowpath"] = json.dumps(flow_path)

        return affected_flows

    def update_isl_bandwidth(self, src_switch, src_port, dst_switch, dst_port):
        """ WARNING CLONED FROM KILDA TOPOLOGY CLASSES!!! """

        available_bw_query = (
            "MATCH (src:switch {{name:'{src_switch}'}}), (dst:switch {{name:'{dst_switch}'}}) WITH src,dst "
            " MATCH (src)-[i:isl {{ src_port:{src_port}, dst_port: {dst_port}}}]->(dst) WITH src,dst,i "
            " OPTIONAL MATCH (src)-[fs:flow_segment {{ src_port:{src_port}, dst_port: {dst_port}, ignore_bandwidth: false }}]->(dst) "
            " WITH sum(fs.bandwidth) AS used_bandwidth, i as i "
            " SET i.available_bandwidth = i.max_bandwidth - used_bandwidth "
        )
        params = {
            'src_switch': src_switch,
            'src_port': src_port,
            'dst_switch': dst_switch,
            'dst_port': dst_port,
        }
        self.tx.run(available_bw_query.format(**params))

    def apply_migrated_data(self, affected_segments, affected_flows):
        self.tx = self.graph.begin(autocommit=False)

        # Recreate Flow Segments
        for segment in affected_segments:
            neo_repr = segment['rel'].__repr__()
            self.tx.run(
                """
                    MATCH (a),(b)
                    WHERE a.name = "{src_sw}" AND b.name = "{dst_sw}"
                    CREATE (a)-[r:flow_segment {params}]->(b)
                    RETURN r
                """.format(
                    src_sw=segment['rel']['src_switch'],
                    dst_sw=segment['rel']['dst_switch'],
                    params=neo_repr.split('-[:flow_segment ')[1].split(']->(')[0]
            ))
            # Clean Old Segments
            self.tx.run("""
                    MATCH ()-[ rel:flow_segment ]->()
                    WHERE id(rel) = {seg_id}
                    DELETE rel;
                """.format(
                    seg_id=remote(segment['rel'])._id,
            ))
            # Update Affected ISL
            self.update_isl_bandwidth(
                src_switch=segment['rel']['src_switch'],
                src_port=segment['rel']['src_port'],
                dst_switch=segment['rel']['dst_switch'],
                dst_port=segment['rel']['dst_port'])

        # Recreate Flows
        for flow in affected_flows:
            neo_repr = flow['rel'].__repr__()
            self.tx.run(
                """
                    MATCH (a),(b)
                    WHERE a.name = "{src_sw}" AND b.name = "{dst_sw}"
                    CREATE (a)-[ r:flow {params} ]->(b)
                    RETURN r
                """.format(
                    src_sw=flow['rel']['src_switch'],
                    dst_sw=flow['rel']['dst_switch'],
                    params=neo_repr.split('-[:flow ')[1].split(']->(')[0]
            ))
            # Clean Old Flows
            self.tx.run("""
                    MATCH ()-[ rel:flow ]->()
                    WHERE id(rel) = {flow_id}
                    DELETE rel;
                """.format(
                    flow_id=remote(flow['rel'])._id,
            ))

        self.tx.commit()


def _macify_switch_id(dpid):
    dpid = dpid.lower().replace(":", '').replace("sw", '')
    c = list(dpid[::-2])
    c.reverse()
    return ":".join(
        map(
            lambda a: ''.join(a),
            zip(dpid[::2], c)
            )
        )


def _read_replug_ports_map_from_file(old_switch_id, new_swithc_id):
    file_name = "{}-to-{}-ports.json".format(
        old_switch_id.replace(':', ''),
        new_swithc_id.replace(':', '')
    )
    with open(file_name, 'r') as ports_map:
        ports_map_data = json.load(ports_map)

    return ports_map_data

def start_migration(old_switch_id, new_switch_id, db_connection_config,
                    replug_port_map=None):
    # get old_switch_id
    old_switch_id = _macify_switch_id(old_switch_id)
    # get new_switch_id
    new_switch_id = _macify_switch_id(new_switch_id)

    # get replug_port_map in format "old port" -> "new_port"
    if not replug_port_map:
        replug_port_map = _read_replug_ports_map_from_file(
            old_switch_id, new_switch_id)

    kilda_db = KildaDBActions(**db_connection_config)
    # 0 STEP
    # Get all affected flow-segments from NEO
    affected_segments = kilda_db._find_all_affected_flow_segments(
        old_switch_id)
    parent_flows = list(set([
        segment['rel'].get('flowid')
        for segment in affected_segments
    ]))
    # Get all affected flows from NEO
    affected_flows = kilda_db._find_all_affected_flows(old_switch_id, links=parent_flows)
    # 1 STEP
    # Performf in memory update all affected segments
    affected_segments = kilda_db._migrate_segments(
        affected_segments, old_switch_id, new_switch_id, replug_port_map)
    # Performf in memory update all affected flows
    affected_flows = kilda_db._migrate_flows(
        affected_flows, old_switch_id, new_switch_id, replug_port_map)
    # Prepate transaction and commit cahnges to DB
    kilda_db.apply_migrated_data(affected_segments, affected_flows)


def do_test():
    """Simple test with semi random data"""
    db_connection_config = {
        "db_addr": "localhost",
        "user": 'neo4j',
        "passwd": '....',
        "port": 7474,
        "secure": False,
        "scheme": 'http'
    }
    old_switch_id = "01:00:00:22:3d:5a:04:87"
    new_switch_id = "00:00:00:22:3d:5a:04:87"
    replug_port_map = {str(i): i for i in xrange(100)}

    start_migration(
        old_switch_id=old_switch_id,
        new_switch_id=new_switch_id,
        db_connection_config=db_connection_config,
        replug_port_map=replug_port_map)

if __name__ == "__main__":
    import argparse

    parser = argparse.ArgumentParser(description='Switch Migration tool')

    parser.add_argument('--old_switch_id', type=str,
                   help='Old Switch dpid')
    parser.add_argument('--new_switch_id', type=str,
                   help='new Switch dpid')

    parser.add_argument('--test', type=bool,
                   help='test', default=False)

    parser.add_argument('--neo_scheme', type=str,
                   help='Neo4j scheme name', default='http')
    parser.add_argument('--neo_host', type=str,
                   help='Neo4j host name or IP', default='locahost')
    parser.add_argument('--neo_port', type=int,
                   help='Neo4j port', default=7474)
    parser.add_argument('--neo_secure', type=bool,
                   help='Neo4j secure', default=False)

    parser.add_argument('--neo_user', type=str,
                   help='Neo4j user name', default='neo4j')
    parser.add_argument('--neo_passwd', type=str,
                   help='Neo4j password')

    args = parser.parse_args()

    if args.test:
        do_test()
    else:
        old_switch_id = args.old_switch_id
        new_switch_id = args.new_switch_id

        db_connection_config = {
            "db_addr": args.neo_host,
            "user": args.neo_user,
            "passwd": args.neo_passwd,
            "port": args.neo_port,
            "secure": args.neo_secure,
            "scheme": args.neo_scheme
        }
        start_migration(
            old_switch_id=old_switch_id,
            new_switch_id=new_switch_id,
            db_connection_config=db_connection_config)

