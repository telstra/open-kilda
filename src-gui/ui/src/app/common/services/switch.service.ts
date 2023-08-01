import { Injectable } from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Switch } from '../data-models/switch';
import { catchError } from 'rxjs/operators';
import * as _moment from 'moment';
import { CookieManagerService } from './cookie-manager.service';
declare var moment: any;


@Injectable({
  providedIn: "root"
})
export class SwitchService {
  constructor(private httpClient: HttpClient,private cookieManager: CookieManagerService) {}

  getSwitchList(query? : any): Observable<any[]> {
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/switch/list`,{params:query});
  }

  getSwitchLinks(): Observable<any[]>{
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/switch/links`);
  }

  getSwitchRulesList(switchId) : Observable<String>{
    let timestamp = new Date().getTime();
    return this.httpClient.get(`${environment.apiEndPoint}/switch/${switchId}/rules?_=${timestamp}`,{responseType: 'text'});
  }

  getSwitchPortsStats(switchId):Observable<any[]>{
    let timestamp = new Date().getTime();
    var endDate = moment().utc().format("YYYY-MM-DD-HH:mm:ss");
    var startDate = moment().utc().subtract(30,'minutes').format("YYYY-MM-DD-HH:mm:ss");
    let downSample="30s";
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/stats/switchports/${switchId}/${startDate}/${endDate}/${downSample}?_=${timestamp}`); 
}

getNetworkPath(source_switch,target_switch, strategy, max_latency){
  let timestamp = new Date().getTime();
  return this.httpClient.get<any>(`${environment.apiEndPoint}/network/paths?src_switch=${source_switch}&dst_switch=${target_switch}&strategy=${strategy}&max_latency=${max_latency}&_=${timestamp}`);
}

  
  configurePort(switchId, portNumber, status): Observable<{}>{
        const url = `${environment.apiEndPoint}/switch/${switchId}/${portNumber}/config`; 
        return this.httpClient.put(url,{"status":status});
  }

  getSwitchPortFlows(switchId,portNumber,filter): Observable<any[]>{
    const url = `${environment.apiEndPoint}/switch/${switchId}/${portNumber}/flows`; 
    return this.httpClient.get<any[]>(url);
  }

  getSwitchDetail(switchId,filter): Observable<{}>{
    var query:any = {controller:filter=='controller'};
    return this.httpClient.get(`${environment.apiEndPoint}/switch/${switchId}`,{params:query});
  }

  getSwitchFlows(switchId,filter,port): Observable<{}>{
    var url = `${environment.apiEndPoint}/switch/${switchId}/flows?inventory=`+filter;
    if(port){
      url = url + "&port="+port;
    }
    return this.httpClient.get(url);
  }

  getSwitchFlowsForPorts(switchId, ports: Array<number>): Observable<{}> {
    let queryParams = new HttpParams();
    ports.forEach(port => queryParams = queryParams.append('ports', String(port)));
    const url = `${environment.apiEndPoint}/switch/${switchId}/flows-by-port`;
    console.log('calling the API, generated url: ' + url + ', params: ' + queryParams.getAll('ports'));
    return this.httpClient.get(url, {params: queryParams});
  }

  getSwitchMetersList(switchId) : Observable<any[]>{
    let timestamp = new Date().getTime();
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/switch/meters/${switchId}?_=${timestamp}`);
  }

  saveSwitcName(name,switchid){
    return this.httpClient.patch<any>(`${environment.apiEndPoint}/switch/name/${switchid}`,name);
  }

  switchMaintenance(data,switchid){
    return this.httpClient.post<any>(`${environment.apiEndPoint}/switch/under-maintenance/${switchid}`,data);
  }
  updatediscoveryPackets(switchId,portNumber,value){
    const url = `${environment.apiEndPoint}/switch/${switchId}/ports/${portNumber}/properties`; 
    return this.httpClient.put(url,{"discovery_enabled":value});
  }
  getdiscoveryPackets(switchId,portNumber){
    let timestamp = new Date().getTime();
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/switch/${switchId}/ports/${portNumber}/properties?_=${timestamp}`);
  }

  updateSwitch(data,switch_id){
    return this.httpClient.patch<any>(`${environment.apiEndPoint}/switch/location/${switch_id}`,data);
  }

  deleteSwitch(switchId,data,successCb,errorCb): void{
    var requestBody = JSON.stringify(data);
    let token = this.cookieManager.get('XSRF-TOKEN') as string;
    var xhr = new XMLHttpRequest();
    xhr.withCredentials = false;
    xhr.addEventListener("readystatechange", function () {
      if (this.readyState == 4 && this.status == 200) {
        successCb(JSON.parse(this.responseText));
      }else if(this.readyState == 4 && this.status >= 300){
        errorCb(JSON.parse(this.responseText));
      }
    });
    
    xhr.open("DELETE", `${environment.apiEndPoint}/switch/${switchId}`);
    xhr.setRequestHeader("Content-Type", "application/json");
    if (token !== null) {
      xhr.setRequestHeader( "X-XSRF-TOKEN" , token);
    }
    xhr.send(requestBody);
  }

}
