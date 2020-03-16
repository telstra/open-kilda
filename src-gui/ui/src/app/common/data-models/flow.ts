
export interface Flow {
    flowid: string;
    source_switch: string;
    src_port: string;
    target_switch: string;
    dst_port: string;
    dst_vlan: string;
    maximum_bandwidth: string;
    status: string;
    description?: string;
    target_switch_name: string;
    source_switch_name: string;
    dummytest:string;
}