/* Copyright 2021 Telstra Open Source
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

package org.openkilda.rulemanager;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.ToString;

public final class ProtoConstants {

    public static final class EthType {
        public static final long IPv4 = 0x0800;
        public static final long LLDP = 0x88CC;
        public static final long ARP = 0x0806;
    }

    public static final class IpProto {
        public static final long UDP = 17;
    }

    public static final class Mask {
        public static final long NO_MASK = -1;
    }

    @JsonSerialize
    @Getter
    @EqualsAndHashCode(of = {"portNumber", "portType"})
    @ToString
    public static class PortNumber {
        private int portNumber;
        private SpecialPortType portType;

        public PortNumber(int number) {
            portNumber = number;
        }

        public PortNumber(SpecialPortType type) {
            portType = type;
        }

        public enum SpecialPortType {
            IN_PORT,
            CONTROLLER,
            LOCAL
        }

    }

    private ProtoConstants() {
    }
}
