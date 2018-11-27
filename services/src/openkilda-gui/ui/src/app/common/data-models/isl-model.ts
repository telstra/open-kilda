import { NULL_INJECTOR } from "@angular/core/src/render3/component";

export interface IslModel {
    src_port: number,
    latency: number,
    source_switch: string,
    available_bandwidth: number,
    dst_port: number,
    target_switch: string,
    speed: number,
    state: string,
    cost: string,
    unidirectional: boolean,
    source_switch_name: string,
    target_switch_name: string,
    state1: string,
    affected: boolean
}