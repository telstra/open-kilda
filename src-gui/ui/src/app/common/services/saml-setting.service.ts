import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class SamlSettingService {

  constructor(private httpClient: HttpClient) { }

   getAuthProviders() : Observable<any>{
     return this.httpClient.get<any>(`${environment.apiEndPoint}/samlconfig`);
   }
   getDetail(uuid){
    return this.httpClient.get<any>(`${environment.apiEndPoint}/samlconfig/${uuid}`);
   }
    saveAuthProvider(data){
       return this.httpClient.post(`${environment.apiEndPoint}/samlconfig`,data);
    }
    updateAuthProvider(data,uuid){
      return this.httpClient.patch(`${environment.apiEndPoint}/samlconfig/${uuid}`,data);
   }
    deleteAuthProvider(uuid){
      return this.httpClient.delete(`${environment.apiEndPoint}/samlconfig/${uuid}`);
  }
}
