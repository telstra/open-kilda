# Copyright 2018 Telstra Open Source
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
#

import pathlib
import subprocess
import logging

logger = logging.getLogger()


def vsctl(commands):
    cmd = 'ovs-vsctl '
    cmd += " -- ".join(commands)
    return run_cmd(cmd)


def ofctl(commands):
    return [run_cmd('ovs-ofctl ' + cmd) for cmd in commands]


def run_cmd(cmd):
    """
    Runs specified command in terminal.
    :param cmd: the command to run
    :return: stdout+stderr
    """
    logger.debug(cmd)
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.STDOUT, shell=True)
    output = p.communicate()[0].decode('utf-8')
    if p.returncode != 0:
        raise OSError("Command '%s' failed:  %s" % (cmd, output))
    return output


def daemon_start(cmd, name):
    log_destination = pathlib.Path("log") / '{}.log'.format(name)
    with log_destination.open("at") as output:
        return subprocess.Popen(cmd, stdout=output, stderr=subprocess.STDOUT)
