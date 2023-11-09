export interface PortInfo {
    assignmenttype: string;
    'assignment-state': string;
    'assignment-date': number;
    interfacetype: string;
    status: string;
    crossconnect: string;
    customeruuid: string;
    switch_id: string;
    port_name: string;
    port_number: any;
    stats: Record<string, any>;
    'unique-id': string;
    'pop-location': PopLocation;
    'inventory-port-uuid': string;
    customer: Customer;
    notes: string;
    odfmdf: string;
    mmr: string;
    'is-active': string;
    is_logical_port: boolean;
    logical_group_name: string;
    lag_group: string[];
    discrepancy: PortDiscrepancy;
}

export interface PopLocation {
    'state-code': string;
    'country-code': string;
    'pop-uuid': string;
    'pop-name': string;
    'pop-code': string;
}


export interface Customer {
    'customer-uuid': string;
    'company-name': string;
    'customer-account-number': string;
    'customer-type': string;
    'domain-id': string;
    'switch-id': string;
    'port-no': number;
    flows: PortFlow[];
}

export interface PortFlow {
    'flow-id': string;
    bandwidth: number;
}


export interface PortDiscrepancy {
    'assignment-type': boolean;
    'controller-assignment-type': string;
    'inventory-assignment-type': string;
    'controller-discrepancy': boolean;
    'inventory-discrepancy': boolean;
}

