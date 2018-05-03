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

import json
import logging
import textwrap

import py2neo

from topologylistener import db
from topologylistener import exc
from topologylistener import model

logger = logging.getLogger(__name__)


def create_if_missing(tx, *links):
    q = textwrap.dedent("""
        MATCH (src:switch {name: $src_switch})
        MATCH (dst:switch {name: $dst_switch})
        MERGE (src) - [target:isl {
          src_switch: $src_switch,
          src_port: $src_port,
          dst_switch: $dst_switch,
          dst_port: $dst_port
        }] -> (dst)
        ON CREATE SET 
          target.status=$status, target.actual=$status,
          target.latency=0""")

    for isl in sorted(links):
        for target in (isl, isl.reversed()):
            match = _make_match(target)
            match['status'] = 'inactive'

            logger.info('Ensure ISL exist: %s', target)
            tx.run(q, match)


def fetch(tx, isl):
    match = _make_match(isl)
    q = textwrap.dedent("""
        MATCH
          (:switch {name: $src_switch})
          -
          [target:isl {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port
          }]
          ->
          (:switch {name: $dst_switch})
        RETURN target""")

    logger.debug('link_props lookup query:\n%s', q)
    cursor = tx.run(q, match)

    try:
        target = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, match)

    return target


def fetch_by_endpoint(tx, endpoint):
    q = (
        'MATCH (src:switch)-[link:isl]->(dst:switch)\n'
        'WHERE src.name=$src_dpid AND link.src_port=$src_port\n'
        'RETURN src, link, dst')
    p = {
        'src_dpid': endpoint.dpid,
        'src_port': endpoint.port}
    cursor = tx.run(q, p)

    try:
        result_set = db.fetch_one(cursor)
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, p)

    src_sw, isl, dst_sw = result_set['src'], result_set['link'], result_set['dst']
    return model.InterSwitchLink.new_from_db(src_sw, dst_sw, isl)


def fetch_by_datapath(tx, dpid):
    q = textwrap.dedent("""
        MATCH (sw:switch {name: $src_switch})
        - [target:isl {src_switch: $src_switch}] -> ()
        RETURN target""")
    p = {'src_switch': dpid}
    cursor = tx.run(q, p)
    return (x['target'] for x in cursor)


def switch_unplug(tx, dpid):
    logging.info("Deactivate all ISL to/from %s", dpid)

    q = textwrap.dedent("""
        MATCH (:switch) - [target:isl] -> ()
        WHERE id(target) = $id
        SET target.actual = 'inactive'""")

    for db_link in fetch_by_datapath(tx, dpid):
        source = model.NetworkEndpoint(
                db_link['src_switch'], db_link['src_port'])
        dest = model.NetworkEndpoint(db_link['dst_switch'], db_link['dst_port'])
        isl = model.InterSwitchLink(source, dest, db_link['actual'])
        logging.debug("Found ISL: %s", isl)

        tx.run(q, {'id': db.neo_id(db_link)})

        update_status(tx, isl)


def update_status(tx, isl):
    logging.info("Sync status both sides of ISL %s to each other", isl)

    q = textwrap.dedent("""
        MATCH
          (:switch {name: $src_switch})
          -
          [self:isl {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port
          }]
          ->
          (:switch {name: $dst_switch})
        MATCH
          (:switch {name: $peer_src_switch})
          -
          [peer:isl {
            src_switch: $peer_src_switch,
            src_port: $peer_src_port,
            dst_switch: $peer_dst_switch,
            dst_port: $peer_dst_port
          }]
          ->
          (:switch {name: $peer_dst_switch})

        WITH self, peer,
          CASE WHEN self.actual = $status_up AND peer.actual = $status_up
               THEN $status_up ELSE $status_down END AS isl_status

        SET self.status=isl_status
        SET peer.status=isl_status""")

    p = {
        'status_up': 'active',
        'status_down': 'inactive'}
    p.update(_make_match(isl))
    p.update({'peer_' + k: v for k, v in _make_match(isl.reversed()).items()})

    logger.debug('ISL update status query:\n%s', q)
    cursor = tx.run(q, p)

    stats = cursor.stats()
    if stats['properties_set'] != 2:
        logger.error(
            'ISL update status query do not update one or both edges '
            'of ISL.')
        logger.error('ISL update status query:\n%s', q)
        logger.error(
            'ISL update status query stats:\n%s',
            json.dumps(stats, indent=2))


def set_cost(tx, isl, cost):
    props = {'cost': cost}
    try:
        changed = set_link_props(tx, isl, props)
    except exc.DBRecordNotFound:
        changed = set_props(tx, isl, props)

    original_cost = changed.get('cost')
    if original_cost != cost:
        logger.warning(
            'ISL %s cost have been changed from %s to %s',
            isl, original_cost, cost)


def set_props(tx, isl, props):
    match = _make_match(isl)
    q = textwrap.dedent("""
        MATCH
          (:switch {name: $src_switch})
          -
          [target:isl {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port
          }]
          ->
          (:switch {name: $dst_switch})
        RETURN target""")

    logger.debug('ISL lookup query:\n%s', q)
    cursor = tx.run(q, match)

    try:
        target = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, match)

    origin, update = _locate_changes(target, props)
    if update:
        q = textwrap.dedent("""
        MATCH (:switch)-[target:isl]->(:switch) 
        WHERE id(target)=$target_id
        """) + db.format_set_fields(
                db.escape_fields(update), field_prefix='target.')

        logger.debug('Push ISL properties: %r', update)
        tx.run(q, {'target_id': db.neo_id(target)})

    return origin


def set_link_props(tx, isl, props):
    match = _make_match(isl)
    q = textwrap.dedent("""
        MATCH (target:link_props {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port})
        RETURN target""")

    logger.debug('link_props lookup query:\n%s', q)
    cursor = tx.run(q, match)

    try:
        target = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, match)

    origin, update = _locate_changes(target, props)
    if update:
        q = textwrap.dedent("""
        MATCH (target:link_props) 
        WHERE id(target)=$target_id
        """) + db.format_set_fields(
                db.escape_fields(update), field_prefix='target.')

        logger.debug('Push link_props properties: %r', update)
        tx.run(q, {'target_id': py2neo.remote(target)._id})

        sync_with_link_props(tx, isl, *update.keys())

    return origin


def sync_with_link_props(tx, isl, *fields):
    copy_fields = {
        name: 'source.' + name for name in fields}
    q = textwrap.dedent("""
        MATCH (source:link_props) 
        WHERE source.src_switch = $src_switch
          AND source.src_port = $src_port
          AND source.dst_switch = $dst_switch
          AND source.dst_port = $dst_port
        MATCH
          (:switch {name: $src_switch})
          -
          [target:isl {
            src_switch: $src_switch,
            src_port: $src_port,
            dst_switch: $dst_switch,
            dst_port: $dst_port
          }]
          ->
          (:switch {name: $dst_switch})
        """) + db.format_set_fields(
            db.escape_fields(copy_fields, raw_values=True),
            field_prefix='target.')
    tx.run(q, _make_match(isl))


def _locate_changes(target, props):
    origin = {}
    update = {}
    for field, value in props.items():
        try:
            current = target[field]
        except KeyError:
            update[field] = props[field]
        else:
            if current != props[field]:
                update[field] = props[field]
                origin[field] = current

    return origin, update


def _make_match(isl):
    return {
        'src_switch': isl.source.dpid,
        'src_port': isl.source.port,
        'dst_switch': isl.dest.dpid,
        'dst_port': isl.dest.port}
