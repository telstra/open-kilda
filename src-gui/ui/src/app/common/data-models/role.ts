import { NULL_INJECTOR } from "@angular/core/src/render3/component";
import { Permission } from './permission';
import { User } from "./user";

export interface Role {
    role_id: number;
    name: string;
    roles: string;
    description: string;
    status: string;
    permissions: Permission;
    users: User
}