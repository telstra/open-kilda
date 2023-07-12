import paramiko


class SwitchCli:
    def __init__(self, host, user, secret, port):
        self.host = host
        self.user = user
        self.secret = secret
        self.port = port

    def execute_remote_commands(self, commands):
        client = paramiko.SSHClient()
        client.set_missing_host_key_policy(paramiko.AutoAddPolicy())
        client.connect(hostname=self.host, username=self.user, password=self.secret, port=self.port)
        data = []
        for command in commands:
            stdin, stdout, stderr = client.exec_command(command)
            data.append((stdout.read() + stderr.read()).decode('utf-8'))
        client.close()
        return data

    def execute_remote_command(self, command):
        return self.execute_remote_commands([command])[0]

    def dump_all_flows(self):
        pass

    def create_flows(self, flows):
        """Each flow is a dictionary with 2 keys: in_port and out_port"""
        pass

    def delete_flows(self, flows):
        pass

    def ports_up(self, ports):
        pass

    def ports_down(self, ports):
        pass
