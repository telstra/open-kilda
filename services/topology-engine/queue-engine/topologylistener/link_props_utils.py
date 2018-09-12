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

import logging
import textwrap

from topologylistener import db
from topologylistener import exc
from topologylistener import model

logger = logging.getLogger(__name__)


def create_if_missing(tx, *batch):
    q = textwrap.dedent("""
        MERGE (target:link_props {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_port: $dst_port,
            dst_switch: $dst_switch})
        ON CREATE SET 
          target.time_create=$time_create,
          target.time_modify=$time_modify
        ON MATCH SET target.time_modify=$time_modify""")

    for link in sorted(batch):
        p = _make_match(link)
        time_fields = [link.time_create, link.time_modify]
        time_fields = [
            str(x) if isinstance(x, model.TimeProperty) else x
            for x in time_fields]
        p['time_create'], p['time_modify'] = time_fields

        logger.info('Ensure link property %s exists', link)
        db.log_query('create link props', q, p)
        tx.run(q, p)


def read(tx, subject):
    db_subject = fetch(tx, subject)
    return model.LinkProps.new_from_db(db_subject)


def drop(tx, subject):
    logger.info("Delete %s request", subject)
    q = textwrap.dedent("""
        MATCH (target:link_props)
        WHERE target.src_switch=$src_switch,
          AND target.src_port=$src_port,
          AND target.dst_port=$dst_port,
          AND target.dst_switch=$dst_switch
        DELETE target
        RETURN target""")
    p = _make_match(subject)

    db.log_query('delete link props', q, p)
    cursor = tx.run(q, p)
    try:
        db_subject = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, p)

    return model.LinkProps.new_from_db(db_subject)


def drop_by_mask(tx, mask):
    logger.info('Delete link props by mask %s', mask)

    p = _make_match_by_mask(mask)
    if not p:
        raise exc.UnacceptableDataError(
            mask, 'reject to drop all link props in DB')

    where = ['target.{0}=${0}'.format(x) for x in p]
    q = 'MATCH (target:link_props)\n'
    if where:
        q += 'WHERE ' + '\n  AND '.join(where)
    q += '\nRETURN target, id(target) as ref'
    db.log_query('pre delete link props fetch', q, p)

    refs = []
    persistent = []
    for db_record in tx.run(q, p):
        persistent.append(model.LinkProps.new_from_db(db_record['target']))
        refs.append(db_record['ref'])

    q = 'MATCH (target:link_props)\n'
    q += 'WHERE id(target) in [{}]'.format(', '.join(str(x) for x in refs))
    q += '\nDELETE target'
    db.log_query('delete link props', q, {})
    tx.run(q)

    return persistent


def set_props_and_propagate_to_isl(tx, subject):
    origin, updated_props = set_props(tx, subject)
    push_props_to_isl(tx, subject, *updated_props)
    return origin


def set_props(tx, subject):
    db_subject = fetch(tx, subject)
    origin, update = db.locate_changes(db_subject, subject.props_db_view())
    if update:
        q = textwrap.dedent("""
        MATCH (target:link_props) 
        WHERE id(target)=$target_id
        """) + db.format_set_fields(
                db.escape_fields(update), field_prefix='target.')
        p = {'target_id': db.neo_id(db_subject)}

        db.log_query('propagate link props to ISL', q, p)
        tx.run(q, p)

    return origin, update.keys()


# low level DB operations


def push_props_to_isl(tx, subject, *fields):
    if not fields:
        return

    copy_fields = {
        name: 'source.' + db.escape(name) for name in fields}
    q = textwrap.dedent("""
        MATCH (source:link_props) 
        WHERE source.src_switch = $src_switch
          AND source.src_port = $src_port
          AND source.dst_switch = $dst_switch
          AND source.dst_port = $dst_port
        MATCH
          (:switch {name: source.src_switch})
          -
          [target:isl {
            src_switch: source.src_switch,
            src_port: source.src_port,
            dst_switch: source.dst_switch,
            dst_port: source.dst_port
          }]
          ->
          (:switch {name: source.dst_switch})
        """) + db.format_set_fields(
            db.escape_fields(copy_fields, raw_values=True),
            field_prefix='target.')
    p = _make_match(subject)
    db.log_query('link props to ISL', q, p)
    tx.run(q, p)


def fetch(tx, subject):
    p = _make_match(subject)
    q = textwrap.dedent("""
        MATCH (target:link_props {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port})
        RETURN target""")

    db.log_query('link props update', q, p)
    cursor = tx.run(q, p)

    try:
        db_object = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, p)

    return db_object


def _make_match_by_mask(mask):
    match = {}
    for endpoint, prefix in (
            (mask.source, 'src_'),
            (mask.dest, 'dst_')):
        if not endpoint:
            continue
        match.update(_make_endpoint_match(endpoint, prefix))

    return {k: v for k, v in match.items() if v is not None}


def _make_match(subject):
    match = _make_endpoint_match(subject.source, 'src_')
    match.update(_make_endpoint_match(subject.dest, 'dst_'))

    masked_fields = {k for k in match if match[k] is None}
    if masked_fields:
        raise exc.UnacceptableDataError(
            subject, 'Match field(s) without value: {}'.format(
                ', '.join(repr(x) for x in sorted(masked_fields))))
    return match


def _make_endpoint_match(endpoint, prefix):
    return {
        prefix + 'switch': endpoint.dpid,
        prefix + 'port': endpoint.port}
