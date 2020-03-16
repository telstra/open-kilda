import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Switch } from '../data-models/switch';
import { catchError } from 'rxjs/operators';
import * as _moment from 'moment';
declare var moment: any;


@Injectable({
  providedIn: "root"
})
export class SwitchService {
  constructor(private httpClient: HttpClient) {}

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

getNetworkPath(source_switch,target_switch){
  let timestamp = new Date().getTime();
  return this.httpClient.get<any>(`${environment.apiEndPoint}/network/paths?src_switch=${source_switch}&dst_switch=${target_switch}&_=${timestamp}`); 
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

  deleteSwitch(switchId,data,successCb,errorCb): void{
    var requestBody = JSON.stringify(data);
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
    xhr.send(requestBody);
  }

}
