# Copyright 2017 Telstra Open Source
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

import os
import signal
import sys

import click

from kilda.traffexam import common
from kilda.traffexam import const
from kilda.traffexam import context as context_module
from kilda.traffexam import exc
from kilda.traffexam import model
from kilda.traffexam import proc
from kilda.traffexam import service
from kilda.traffexam import system
from kilda.traffexam import rest


@click.command()
@click.option(
    '--root',
    default=context_module.Context.root,
    type=click.Path(file_okay=False, dir_okay=True))
@click.option('--logging-config')
@click.option('--debug', is_flag=True)
@click.argument('iface')
@click.argument('bind')
def main(iface, bind, **args):
    try:
        check_privileges()

        iface = model.TargetIface.new_from_cli(iface)
        bind = model.BindAddress.new_from_cli(bind)

        context = context_module.Context(iface, bind)
        try:
            context.set_root(args['root'])

            logging_config = args['logging_config']
            if logging_config:
                try:
                    context.set_logging_config(logging_config)
                except KeyError:
                    pass

            context.set_debug_mode(args['debug'])

            with proc.PidFile(context.make_lock_file_name()):
                setup_environment(context)

                context.set_service_adapter(service.Adapter(context))
                SigCHLD(context.children)

                rest.init(bind, context)
        finally:
            context.close()
    except exc.InputDataError as e:
        click.echo(str(e), err=True)
        sys.exit(const.CLI_ERROR_CODE_COMMON)


def check_privileges():
    if 0 < os.geteuid():
        raise exc.InsufficientPrivilegesError


def setup_environment(context):
    context.acquire_resources(system.IPDBRoot)

    iface_info_getter = system.RootIfaceInfo(context)
    target_iface = iface_info_getter(context.iface.index)

    context.acquire_resources(
            system.NetworkNamespace,
            system.VEthPair)

    if target_iface.kind != 'bridge':
        context.acquire_resources(
                system.BridgeToTarget,
                system.TargetIfaceCleanUp)
    else:
        context.acquire_resources(system.JoinTargetBridge)

    context.acquire_resources(
            system.OwnedNetworksCleanUp,
            system.NSNetworksCleanUp,
            system.NSGatewaySetUp,
            system.TargetIfaceSetUp)


class SigCHLD(common.AbstractSignal):
    def __init__(self, children):
        super().__init__(signal.SIGCHLD)
        self.children = children

    def handle(self):
        for child in self.children:
            child.poll()
