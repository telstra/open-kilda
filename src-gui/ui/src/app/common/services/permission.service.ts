import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { Permission } from '../data-models/permission';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root'
  })
  export class PermissionService {

    configUrl: string;
    public subject = new Subject<any>();
    private permissionSource = new BehaviorSubject(null);
    currentPermission = this.permissionSource.asObservable();

    constructor(private http: HttpClient) {
        this.configUrl = `${environment.apiEndPoint}`;
    }
    
    getPermissions(): Observable<Permission[]>{
        return this.http.get<Permission[]>(this.configUrl+'/permission');
    }

    /** POST: add permission to the server */
    addPermission(permission: Permission): Observable<Permission[]>{
        return this.http.post<Permission[]>(this.configUrl+'/permission', permission);
    }

    getPermissionById(id: number): Observable<Permission>{
        const url = `${this.configUrl}/permission/${id}`; 
        return this.http.get<Permission>(url);
    }

    editPermission(id: number, permission: Permission): Observable<Permission>{
        const url = `${this.configUrl}/permission/${id}`; 
        return this.http.put<Permission>(url, permission);
    }

    /** DELETE: delete the permission from the server */
    deletePermission(id: number): Observable<{}> {
        const url = `${this.configUrl}/permission/${id}`; 
        return this.http.delete(url);
    }

    assignPermission(id: number, role: Permission): Observable<Permission>{
        const url = `${this.configUrl}/role/permission/${id}`; 
        return this.http.put<Permission>(url, role);
    }

    getPermissionRoleById(id: number): Observable<Permission>{
        const url = `${this.configUrl}/role/permission/${id}`; 
        return this.http.get<Permission>(url);
    }

    selectedPermission(userId: number) {
        this.permissionSource.next(userId)
    }

    clearselectedPermission() {
        this.subject.next();
    }
}