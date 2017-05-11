package org.bitbucket.openkilda.wfm;

/**
 * This is the main Storm Topology for ISL Discovery and management
 *
 * Rough Algorithm for ISL Management:
 *
 *
 *      port up/add - add_port_for_discover(switch_id, port) (ie add to set)
 *      switch down - remove_switch_for_discover(switch_id)  (ie remove associated ISLs)
 *      port down/delete   - remove_port_for_discover(switch_id, port)
 *
 * Pseudocode:
 * ==========
 * class IslDiscover(threading.Thread):
 *    for isl in isls:
 *        send_isl_discover_packet(isl['switch_id'], isl['port_no'])
 *
 * class IslPoll(threading.Thread):
 *    if len(isls) > 0:
 *        IslDiscover().start()
 *            time.sleep(30)
 *
 */
public class ISLTopology {


    // server - kafka
    // topic - to listen on?




    public static final class ISLBolt {


//        def send_isl_discover_packet(switch_id, port):
//
//        data = {"destination": "CONTROLLER",
//                "command": "discover_isl",
//                "switch_id": switch_id,
//                "port_no": port}
//        message = {"type": "COMMAND",
//                "timestamp": long(time.time()*1000),
//                "payload": data}
//
//            logger.info(message)
//                queue.put(message)


    }
}
