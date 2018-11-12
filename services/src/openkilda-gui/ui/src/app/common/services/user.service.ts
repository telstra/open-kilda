
import { Injectable } from '@angular/core';
import { HttpClient, HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { User } from '../data-models/user';
import { BehaviorSubject } from 'rxjs';

const httpOptions = {
    headers: new HttpHeaders({
      'Content-Type':  'application/json',
      'dataType': "json"
    })
  };

@Injectable({
    providedIn: 'root'
  })
  export class UserService {

    configUrl: string;
    public subject = new Subject<any>();
    private userSource = new BehaviorSubject(null);
    currentUser = this.userSource.asObservable();

    constructor(private http: HttpClient) {
        this.configUrl = `${environment.apiEndPoint}`;
    }

    /** GET: fetch all the users from server */
    getUsers(): Observable<User[]>{
        return this.http.get<User[]>(this.configUrl+'/user');
    }

    /** POST: add user to the server */
    addUser(user: User): Observable<User[]>{
        return this.http.post<User[]>(this.configUrl+'/user', user);
    }

    getUserById(id: number): Observable<User>{
        const url = `${this.configUrl}/user/${id}`; 
        return this.http.get<User>(url);
    }

    editUser(id: number, user: User): Observable<User>{
        const url = `${this.configUrl}/user/${id}`; 
        return this.http.put<User>(url, user);
    }

    /** DELETE: delete the user from the server */
    deleteUser(id: number): Observable<{}> {
        const url = `${this.configUrl}/user/${id}`; 
        return this.http.delete(url);
    }

    /** GET: Reset Password and send password via Email */
    resetpasswordByUser(id: number): Observable<{}>{
        const url = `${this.configUrl}/user/resetpassword/${id}`; 
        return this.http.get(url);
    }

    /** GET: Reset Password by Admin */
    resetpasswordByAdmin(id: number): Observable<{}>{
        const url = `${this.configUrl}/user/admin/resetpassword/${id}`; 
        return this.http.get(url);
    }

    /** PUT: Reset2FA */
    reset2fa(id: number): Observable<{}>{
        const url = `${this.configUrl}/user/reset2fa/${id}`; 
        return this.http.put(url,id);
    }

    /** Set user for use user id in different components */
    selectedUser(userId: number) {
        this.userSource.next(userId)
    }

    /** Clear user id from selectedUser service  */
    clearSelectedUser() {
        this.subject.next();
    }

    /** PUT: change Password */
    changePassword(id: number, user: User): Observable<User>{
        const url = `${this.configUrl}/user/changePassword/${id}`; 
        return this.http.put<User>(url, user);
    }

    getSettings(query?:any):Observable<any[]>{
        return this.http.get<any[]>(`${this.configUrl}/user/settings`,{params:query});
    }

    getLoggedInUserInfo():Observable<User>{
        return this.http.get<User>(`${this.configUrl}/user/loggedInUserInfo`);
    }
    
    saveSettings(payload):Observable<any[]>{
        return this.http.patch<any[]>(`${this.configUrl}/user/settings`,payload);
    }

  }