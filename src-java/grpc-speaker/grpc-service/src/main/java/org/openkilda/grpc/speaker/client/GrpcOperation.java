/* Copyright 2020 Telstra Open Source
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

package org.openkilda.grpc.speaker.client;

public enum GrpcOperation {
    LOGIN,
    SHOW_SWITCH_STATUS,
    SET_LOGICAL_PORT,
    SHOW_CONFIG_LOGICAL_PORT,
    DUMP_LOGICAL_PORTS,
    DELETE_LOGICAL_PORT,
    SET_LOG_MESSAGES_STATUS,
    SET_LOG_OF_ERRORS_STATUS,
    SHOW_CONFIG_REMOTE_LOG_SERVER,
    SET_CONFIG_REMOTE_LOG_SERVER,
    DELETE_CONFIG_REMOTE_LOG_SERVER,
    SET_PORT_CONFIG,
    SET_CONFIG_LICENSE,
    GET_PACKET_IN_OUT_STATS
}
