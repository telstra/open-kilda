from cli.switch_cli import SwitchCli


PORT_UP_STATE = "off"   # Yes, 'off' is a correct port UP state because 'off' will turn off 'portdown' flag
PORT_DOWN_STATE = "on"  # Yes, 'on' is a correct port DOWN state because 'on' will turn on 'portdown' flag


class NoviCli(SwitchCli):
    def __init__(self, host, user, secret, port):
        super().__init__(host, user, secret, port)

    def dump_all_flows(self):
        flows = self.execute_remote_command('show status flow tableid all')
        return self._parse_dump_flows(flows)

    def create_flows(self, flows):
        """Each flow is a dictionary with 2 keys: in_port and out_port"""
        commands = ['set config flow tableid 0 command add priority 1000 matchfields in_port ' \
                    'valuesmasks {in_port} instruction apply_actions action output ' \
                    'port {out_port}'.format(**flow) for flow in flows]
        self.execute_remote_commands(commands)

    def delete_flows(self, flows):
        commands = ['del config flow tableid all strict on priority 1000 matchfields in_port ' \
                    'valuesmasks {in_port}'.format(**flow) for flow in flows]
        self.execute_remote_commands(commands)

    def ports_up(self, ports):
        self._change_ports_state(ports, PORT_UP_STATE)

    def ports_down(self, ports):
        self._change_ports_state(ports, PORT_DOWN_STATE)

    def _change_ports_state(self, ports, port_state):
        commands = ["set config port portno {port} portdown {state}"
                    .format(port=port, state=port_state) for port in ports]
        return self.execute_remote_commands(commands)

    @staticmethod
    def _int_from_str_by_pattern(string, pattern):
        start = string.find(pattern)
        end = string.find('\n', start)
        if end == -1:
            end = None
        return int(string[start + len(pattern):end])

    @classmethod
    def _parse_dump_flows(cls, raw):
        flows = raw.split("FLOW_ID")[1:]
        dump = []
        for flow in flows:
            in_port = cls._int_from_str_by_pattern(flow, 'OFPXMT_OFB_IN_PORT = ')
            out_port = cls._int_from_str_by_pattern(flow, 'port = ')
            dump.append({'in_port': in_port, 'out_port': out_port})
        return dump
