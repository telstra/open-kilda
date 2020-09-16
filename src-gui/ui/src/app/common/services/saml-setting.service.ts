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
     return this.httpClient.get<any>(`${environment.apiEndPoint}/samlconfig/getAll`);
   }
   getDetail(id){
    return this.httpClient.get<any>(`${environment.apiEndPoint}/samlconfig/${id}`);
   }
    saveAuthProvider(data){
       return this.httpClient.post(`${environment.apiEndPoint}/samlconfig/save`,data);
    }
    updateAuthProvider(data,id){
      return this.httpClient.patch(`${environment.apiEndPoint}/samlconfig/update/${id}`,data);
   }
   loadAuthProviderConfig(idp_id,type,data){
      return this.httpClient.post(`${environment.apiEndPoint}/samlconfig/load/${idp_id}?type=${type}`,data);
    }
    deleteAuthProvider(idp_id){
      return this.httpClient.delete(`${environment.apiEndPoint}/samlconfig/${idp_id}`);
  }
}
