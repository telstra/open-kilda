#!/bin/bash
structure="Destination Mac Address:48,Source Mac Address:48,EtherType:16,Version:4,\
IHL:4,Type of Service:8,Total Length:16,Identification:16,Flags:3,Fragment Offset:13,\
Time to Live:8,Protocol:8,Header Checksum:16,Source IP Address:32,Destination IP Address:32,\
Source Port:16,Destination Port:16,Length:16,Checksum:16,TVL Type = 1:7,length:9,\
Chassis Id:56,TVL Type = 2:7,length:9,Port:24,TVL Type = 3:7,length:9,TTL:16,TVL Type=127:7,\
length:9,Organizationally Unique Identifier:24,Timestamp type:8,Timestamp T0:64,TVL Type=127:7,\
length:9,Organizationally Unique Identifier:24,Timestamp type:8,Timestamp T1:64,TVL Type=127:7,\
length:9,Organizationally Unique Identifier:24,SwitchId type:8,Datapath ID:64,TVL Type=127:7,\
length:9,Organizationally Unique Identifier:24,Timestamp type:8,Floodlight Timestamp:64,\
TVL Type=127:7,length:9,Organizationally Unique Identifier:24,Ordinal type:8,Ordinal:32,\
TVL Type=127:7,length:9,Organizationally Unique Identifier:24,Sigh type:8,Token:131,End of LLDP:16"

protocol "$structure" --bits 48