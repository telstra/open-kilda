import click
import functools
import json
import os
import sys

from kilda.llgen import client
from kilda.llgen import context
from kilda.llgen import daemon
from kilda.llgen import manager


@click.group()
@click.option(
    '--root',
    default=os.getcwd(),
    type=click.Path(exists=True, file_okay=False, dir_okay=True))
@click.option('--logging-config')
@click.option('--debug', is_flag=True)
@click.argument('name')
@click.pass_context
def cli(cli_ctx, **cli_args):
    ctx = context.Context(cli_args['root'], cli_args['name'])
    ctx.set_logging_config(cli_args['logging_config'])
    ctx.set_debug(cli_args['debug'])

    cli_ctx.obj = ctx


@cli.command(name='setup')
@click.argument('payload',  default=sys.stdin, type=click.File())
@click.pass_obj
def setup(ctx, **args):
    payload = args['payload']
    try:
        payload = json.load(payload)
    except (TypeError, ValueError) as e:
        raise click.ClickException(
            'Invalid payload - json deserialization failed - {}'.format(e))

    _daemon_communication(ctx, payload)


@cli.command(name='kill')
@click.pass_obj
def kill(ctx):
    try:
        daemon.Daemon.kill(ctx)
    except daemon.Daemon.NoRunningInstanceException:
        print('There is no running instance')
        sys.exit(1)
    else:
        print('TERM signal have been sent')


def _daemon_communication(ctx, payload):
    factory = functools.partial(manager.Manager, ctx)
    daemon.Daemon(ctx, factory)

    c = client.Client(ctx)
    print(c(payload))
