import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable, Subject } from 'rxjs';
import { IdentityServerModel } from '../data-models/identityserver-model';
import { LinkStoreModel } from '../data-models/linkstore-model';
import { catchError } from 'rxjs/operators';
import { SwitchStoreModel } from '../data-models/switchstore-model';

@Injectable({
  providedIn: 'root'
})
export class StoreSettingtService {
    public switchSettings = new Subject<any>(); /*  */;
    switchSettingReceiver = this.switchSettings.asObservable();

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

    /**Switch Store apis starts */

    getSwitchStoreUrl():Observable<any>{
        return this.httpClient.get<any>(`${environment.apiEndPoint}/url/store/SWITCH_STORE`);
    }

    getSwitchStoreDetails(query? : any):Observable<any>{
        return this.httpClient.get<any>(`${environment.apiEndPoint}/store/switch-store-config`,{params:query});
    }

    checkSwitchStoreDetails(query? : any):void{
        this.httpClient.get<any>(`${environment.apiEndPoint}/store/switch-store-config`,{params:query}).subscribe(settings=>{
            if(settings && settings['urls'] && typeof(settings['urls']['get-all-switches']) !='undefined' &&  typeof(settings['urls']['get-all-switches']['url'])!='undefined'){
                localStorage.setItem('switchStoreSetting',JSON.stringify(settings));
                localStorage.setItem('hasSwtStoreSetting',"1");
              }else{
                localStorage.removeItem('switchStoreSetting');
                localStorage.removeItem('hasSwtStoreSetting');
            }
            this.emitSwitchStoreSettings(settings);
        })
    }

    validateUrlParams(url,params) {
        let return_flag = true;
        if(url=='' || url == null){
          return true;
        }
            for(let i=0; i < params.length; i++){
                if(url.includes(params[i])){
                    return_flag = true;
                }else{
                    return_flag = false;
                    break;
                }
        }
            return return_flag;
      }
      
    validateUrl(url) { 
        if(url=='' || url == null){
          return true;
        }
            var res = url.match(/(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)/g);
            if(res == null)
                return false;
            else
                return true;
    }

    deleteSwitchStore(url){
        return this.httpClient.delete(`${environment.apiEndPoint}${url}`);
    }

    emitSwitchStoreSettings(settings){
        this.switchSettings.next(settings);
    }


}
