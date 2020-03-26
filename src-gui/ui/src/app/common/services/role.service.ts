
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { Role } from '../data-models/role';
import { BehaviorSubject } from 'rxjs';

@Injectable({
    providedIn: 'root'
  })
  export class RoleService {

    configUrl: string;
    public subject = new Subject<any>();
    private roleSource = new BehaviorSubject(null);
    currentRole = this.roleSource.asObservable();

    constructor(private http: HttpClient) {
        this.configUrl = `${environment.apiEndPoint}`;
    }
    
    getRoles(): Observable<Role[]>{
        return this.http.get<Role[]>(this.configUrl+'/role');
    }

    /** POST: add role to the server */
    addRole(role: Role): Observable<Role[]>{
        return this.http.post<Role[]>(this.configUrl+'/role', role);
    }

    getRoleById(id: number): Observable<Role>{
        const url = `${this.configUrl}/role/${id}`; 
        return this.http.get<Role>(url);
    }

    editRole(id: number, role: Role): Observable<Role>{
        const url = `${this.configUrl}/role/${id}`; 
        return this.http.put<Role>(url, role);
    }

    assignRole(id: number, role: Role): Observable<Role>{
        const url = `${this.configUrl}/user/role/${id}`; 
        return this.http.put<Role>(url, role);
    }

    getUserRoleById(id: number): Observable<Role>{
        const url = `${this.configUrl}/user/role/${id}`; 
        return this.http.get<Role>(url);
    }

    /** DELETE: delete the role from the server */
    deleteRole(id: number): Observable<{}> {
        const url = `${this.configUrl}/role/${id}`; 
        return this.http.delete(url);
    }

    selectedRole(userId: number) {
        this.roleSource.next(userId)
    }

    clearSelectedRole() {
        this.subject.next();
    }

  }