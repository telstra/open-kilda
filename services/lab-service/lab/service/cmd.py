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

import subprocess
import logging

logger = logging.getLogger()


def vsctl(commands, **kwargs):
    cmd = 'ovs-vsctl '
    cmd += " -- ".join(commands)
    return run_cmd(cmd, **kwargs)


def ofctl(commands, **kwargs):
    return [run_cmd('ovs-ofctl ' + cmd, **kwargs) for cmd in commands]


def run_cmd(cmd, sync=True):
    """
    Runs specified command in terminal.
    :param cmd: the command to run
    :param sync: block and wait for command termination
    :return:
    Stdout+stderr, when sync=True.
    Subprocess.Popen instance, when sync=False.
    """
    logger.debug(cmd)
    p = subprocess.Popen(cmd, stdout=subprocess.PIPE, stderr=subprocess.PIPE, shell=True)
    if sync:
        output, error = p.communicate()
        resp = output.decode('utf-8') + error.decode('utf-8')
        if p.returncode == 0:
            return resp
        else:
            raise OSError("Command '%s' failed:  %s" % (cmd, resp))
    else:
        return p
