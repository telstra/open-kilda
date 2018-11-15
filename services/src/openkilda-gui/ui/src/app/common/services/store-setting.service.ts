import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { IdentityServerModel } from '../data-models/identityserver-model';
import { LinkStoreModel } from '../data-models/linkstore-model';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class StoreSettingtService {
  constructor(private httpClient: HttpClient) {}
    getIdentityServerConfigurations() : Observable<IdentityServerModel>{
     return this.httpClient.get<IdentityServerModel>(`${environment.apiEndPoint}/auth/oauth-two-config`);
    }
  
    getLinkStoreDetails(query? : any):Observable<LinkStoreModel>{
 	     return this.httpClient.get<LinkStoreModel>(`${environment.apiEndPoint}/store/link-store-config`,{params:query});
    }

    getLinkStoreUrl():Observable<any>{
     return this.httpClient.get<any>(`${environment.apiEndPoint}/url/store/LINK_STORE`);
    }
    generateorRefreshToken(tokenUrl,postData){
        let headers = {
            "Content-Type":'application/x-www-form-urlencoded'
        }
        return this.httpClient.post(tokenUrl,postData,{headers:headers});
    }
    getData(url){
        return this.httpClient.get<any>(`${environment.apiEndPoint}${url}`);
    }
    submitIdentity(url,data){
        return this.httpClient.post(`${environment.apiEndPoint}${url}`,data);
    }
    submitLinkData(url,data){
        return this.httpClient.post(`${environment.apiEndPoint}${url}`,data);
    }
    deleteLinkStore(url){
        return this.httpClient.delete(`${environment.apiEndPoint}${url}`);
    }
}
