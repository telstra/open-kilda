
import { Role } from './role';
import { Permission } from "./permission";

export interface User {
    user_id: number;
    name: string;
    user_name: string;
    email: string;
    status: string;
    is2FaEnabled: boolean;
    password:string;
    roles: Role;
    permissions: Permission;
}