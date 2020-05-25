# TBD

# pipework
from https://github.com/jpetazzo/pipework
wget https://raw.githubusercontent.com/jpetazzo/pipework/master/pipework && chmod +x pipework

# how to run
`docker run -it --privileged -v /sys/bus/pci/drivers:/sys/bus/pci/drivers -v /sys/kernel/mm/hugepages:/sys/kernel/mm/hugepages -v /sys/devices/system/node:/sys/devices/system/node -v /dev:/dev --name server42-dpdk kilda/server42dpdk:latest`

# python script for calc cores mask
`hex(int('1111', 2)) = 0xf`

# check memory channels for -m option of EAL
`sudo dmidecode | grep Interleaved -- memory channels`

# connect to kilda setup
`sudo ./pipework br_kilda_int  -i eth1 server42-dpdk    10.0.77.2/24
sudo ./pipework br_kilda_test -i eth2 server42-dpdk    10.0.88.1/24
sudo ./pipework br_kilda_int  -i eth1 server42-control 10.0.77.1/24
sudo ./pipework br_kilda_int  -i eth1 server42-stats   10.0.77.3/24
`

#check traffic on external loop
`sudo tcpdump -elnXXi br_kilda_test`

# local loop inside container
`ip link add eth42 type veth peer name eth24
ip link set eth24 up
ip link set eth42 up`

# run
`./server42 -c 0x1f --vdev=net_pcap0,iface=eth42 --vdev=net_pcap1,iface=eth24 --no-huge -- --debug`

# status
`watch -d -n 1 cat /tmp/server42-status.txt`
