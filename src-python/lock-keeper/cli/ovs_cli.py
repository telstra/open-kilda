from cli.switch_cli import SwitchCli

PORT_UP_STATE = "up"
PORT_DOWN_STATE = "down"


class OvsCli(SwitchCli):
    def __init__(self, host, user, secret, port):
        super().__init__(host, user, secret, port)

    def dump_all_flows(self):
        flows = self.execute_remote_command('ovs-ofctl dump-flows br0')
        return self._parse_dump_flows(flows)

    def create_flows(self, flows):
        """Each flow is a dictionary with 2 keys: in_port and out_port"""
        commands = ['ovs-ofctl add-flow br0 in_port={in_port},' \
                    'actions=output={out_port}'.format(**flow)
                    for flow in flows]
        self.execute_remote_commands(commands)

    def delete_flows(self, flows):
        commands = ['ovs-ofctl del-flows br0 in_port={in_port}'.format(**flow)
                    for flow in flows]
        self.execute_remote_commands(commands)

    def ports_up(self, ports):
        self._change_ports_state(ports, PORT_UP_STATE)

    def ports_down(self, ports):
        self._change_ports_state(ports, PORT_DOWN_STATE)

    def _change_ports_state(self, ports, port_state):
        """Common port states: up, down."""
        commands = ["ovs-ofctl mod-port br0 {} {}".format(str(port), port_state)
                    for port in ports]
        return self.execute_remote_commands(commands)

    @staticmethod
    def _int_from_str_by_pattern(string, pattern):
        start = string.find(pattern)
        end = string.find(' ', start)
        if end == -1:
            end = None
        return int(string[start + len(pattern):end])

    @classmethod
    def _parse_dump_flows(cls, raw):
        data = raw[raw.find('\n') + 1:]
        return [{'in_port': cls._int_from_str_by_pattern(x, 'in_port='),
                 'out_port': cls._int_from_str_by_pattern(x, 'actions=output:')}
                for x in data.split('\n') if x]
