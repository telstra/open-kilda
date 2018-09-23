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

import logging
import textwrap

from topologylistener import db
from topologylistener import exc
from topologylistener import flow_utils
from topologylistener import model
from topologylistener import link_props_utils

logger = logging.getLogger(__name__)


def create_if_missing(tx, timestamp, *links):
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
          target.latency=-1,
          target.time_create=$timestamp,
          target.time_modify=$timestamp
        ON MATCH SET target.time_modify=$timestamp""")

    for isl in sorted(links):
        for target in (isl, isl.reversed()):
            p = _make_match(target)
            p['status'] = 'inactive'
            p['timestamp'] = str(timestamp)

            logger.info('Ensure ISL %s exists', target)
            db.log_query('create ISL', q, p)
            tx.run(q, p)


def fetch(tx, isl):
    p = _make_match(isl)
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

    db.log_query('ISL fetch', q, p)
    cursor = tx.run(q, p)

    try:
        target = db.fetch_one(cursor)['target']
    except exc.DBEmptyResponse:
        raise exc.DBRecordNotFound(q, p)

    return target


def fetch_by_endpoint(tx, endpoint):
    q = textwrap.dedent("""
        MATCH (src:switch)-[target:isl]->(:switch)
        WHERE src.name=$src_switch AND target.src_port=$src_port
        RETURN target""")
    p = {
        'src_switch': endpoint.dpid,
        'src_port': endpoint.port}
    cursor = tx.run(q, p)
    return db.ResponseIterator((x['target'] for x in cursor), q, p)


def fetch_by_datapath(tx, dpid):
    q = textwrap.dedent("""
        MATCH (sw:switch {name: $src_switch})
        - [target:isl {src_switch: $src_switch}] -> ()
        RETURN target""")
    p = {'src_switch': dpid}
    cursor = tx.run(q, p)
    return (x['target'] for x in cursor)


def touch(tx, isl, mtime=None):
    logger.debug("Touch ISL %s", isl)

    if mtime is None:
        mtime = model.TimeProperty.now()
    props = {'time_modify': str(mtime)}
    set_props(tx, isl, props)


def resolve_conflicts(tx, isl):
    logger.info('Check ISL %s for conflicts', isl)

    involved = [
        fetch(tx, isl), fetch(tx, isl.reversed())]
    keep_dbid = {db.neo_id(x) for x in involved}

    involved.extend(fetch_by_endpoint(tx, isl.source))
    involved.extend(fetch_by_endpoint(tx, isl.dest))

    _lock_affected_switches(tx, involved)

    for link in involved:
        link_dbid = db.neo_id(link)
        if link_dbid in keep_dbid:
            continue

        link_isl = model.InterSwitchLink.new_from_db(link)
        if is_active_status(link['actual']):
            logger.error('Detected ISL %s conflict with %s. Please contact dev team', link_isl, isl)
            # set_active_field(tx, link_dbid, 'inactive')
            # update_status(tx, link_isl)
        else:
            logger.debug("Skip conflict ISL %s deactivation due to its current status - %s", link_isl, link['actual'])


def disable_by_endpoint(tx, endpoint, is_moved=False, mtime=True):
    logging.debug('Locate all ISL starts on %s', endpoint)

    involved = list(fetch_by_endpoint(tx, endpoint))
    _lock_affected_switches(tx, involved, endpoint.dpid)

    updated = []
    for link in involved:
        isl = model.InterSwitchLink.new_from_db(link)
        logger.info('Deactivate ISL %s', isl)

        status = 'moved' if is_moved else 'inactive'
        set_active_field(tx, db.neo_id(link), status)
        update_status(tx, isl, mtime=mtime)

        updated.append(isl)

    return updated


def update_status(tx, isl, mtime=True):
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

        WITH self, peer, CASE 
          WHEN self.actual = $status_up AND peer.actual = $status_up
            THEN $status_up 
          WHEN self.actual = $status_moved OR peer.actual = $status_moved
            THEN $status_moved
          ELSE $status_down 
        END AS isl_status  

        SET self.status=isl_status
        SET peer.status=isl_status""")
    p = {
        'status_up': 'active',
        'status_moved': 'moved',
        'status_down': 'inactive'}
    p.update(_make_match(isl))
    p.update({'peer_' + k: v for k, v in _make_match(isl.reversed()).items()})

    expected_update_properties_count = 2
    if mtime:
        if not isinstance(mtime, model.TimeProperty):
            mtime = model.TimeProperty.now()

        q += '\nSET self.time_modify=$mtime, peer.time_modify=$mtime'
        p['mtime'] = str(mtime)
        expected_update_properties_count = 4

    db.log_query('ISL update status', q, p)
    cursor = tx.run(q, p)
    cursor.evaluate()  # to fetch first record

    stats = cursor.stats()
    if stats['properties_set'] != expected_update_properties_count:
        logger.error(
                'Failed to sync ISL\'s %s records statuses. Looks like it is '
                'unidirectional.', isl)


def get_life_cycle_fields(tx, isl):
    db_isl = fetch(tx, isl)
    values = [db_isl['time_create'], db_isl['time_modify']]
    for idx, item in enumerate(values):
        if not item:
            continue
        values[idx] = model.TimeProperty.new_from_db(item)
    return model.LifeCycleFields(*values)


def set_active_field(tx, neo_id, status):
    q = textwrap.dedent("""
        MATCH (:switch) - [target:isl] -> ()
        WHERE id(target) = $id
        SET target.actual = $status""")
    p = {
        'status': status,
        'id': neo_id}
    tx.run(q, p)


def increase_cost(tx, isl, amount, limit):
    cost = get_cost(tx, isl)
    if not cost:
        cost = 0
    if limit <= cost:
        return

    set_cost(tx, isl, cost + amount)


def get_cost(tx, isl):
    db_record = fetch(tx, isl)
    value = db_record['cost']
    if value is not None:
        value = model.convert_integer(value)
    return value


def set_cost(tx, isl, cost):
    link_props = model.LinkProps.new_from_isl(isl)
    link_props.props['cost'] = cost
    try:
        origin = link_props_utils.set_props_and_propagate_to_isl(
            tx, link_props)
    except exc.DBRecordNotFound:
        origin = set_props(tx, isl, {'cost': cost})

    original_cost = origin.get('cost')
    if original_cost != cost:
        logger.warning(
            'ISL %s cost have been changed from %s to %s',
            isl, original_cost, cost)


def set_props(tx, isl, props):
    target = fetch(tx, isl)
    origin, update = db.locate_changes(target, props)
    if update:
        q = textwrap.dedent("""
        MATCH (:switch)-[target:isl]->(:switch) 
        WHERE id(target)=$target_id
        """) + db.format_set_fields(
                db.escape_fields(update), field_prefix='target.')

        logger.debug('Push ISL properties: %r', update)
        tx.run(q, {'target_id': db.neo_id(target)})

    return origin


def del_props(tx, isl, props):
    logger.info(
        'ISL drop %s props request: %s', isl, ', '.join(repr(x) for x in props))

    remove = ['target.{}'.format(db.escape(x)) for x in props]
    if not remove:
        return

    remove.insert(0, '')
    p = _make_match(isl)
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
          (:switch {name: $dst_switch})""") + '\nREMOVE '.join(remove)
    db.log_query('ISL drop props', q, p)
    stats = tx.run(q, p).stats()
    if 'max_bandwidth' in props:
        db_isl = fetch(tx, isl)
        set_props(tx, isl, {'max_bandwidth': db_isl.get('default_max_bandwidth', 0)})
    return stats['contains_updates']


def _lock_affected_switches(tx, db_links, *extra):
    affected_switches = set(extra)
    for link in db_links:
        affected_switches.add(link['src_switch'])
        affected_switches.add(link['dst_switch'])

    flow_utils.precreate_switches(tx, *affected_switches)


def _make_match(isl):
    return {
        'src_switch': isl.source.dpid,
        'src_port': isl.source.port,
        'dst_switch': isl.dest.dpid,
        'dst_port': isl.dest.port}


def is_active_status(status):
    return status == 'active'
