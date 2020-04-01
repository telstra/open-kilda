import { Injectable } from '@angular/core';
import { HttpClient,HttpHeaders } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Flow } from '../data-models/flow';

@Injectable({
  providedIn: 'root'
})
export class FlowsService {

  constructor(private httpClient: HttpClient) {
    
  }

  getFlowsList(query? : any) : Observable<Flow[]>{
    return this.httpClient.get<Flow[]>(`${environment.apiEndPoint}/flows/list`,{params:query});
  }


  getFlowDetailById(flowId,filterFlag):Observable<any>{
    let query:any = {controller:filterFlag == 'controller'};
    return this.httpClient.get<any>(`${environment.apiEndPoint}/flows/${flowId}`,{params:query});
  }

  createFlow(data):Observable<any>{
    return this.httpClient.put(`${environment.apiEndPoint}/flows`,data);
  }

  validateFlow(flowId):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/flows/${flowId}/validate`);
  }

  updateFlow(flowId,payload):Observable<any>{
    return this.httpClient.put(`${environment.apiEndPoint}/flows/${flowId}`,payload);
  }

  deleteFlow(flowId,data,successCb,errorCb): void{
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
    
    xhr.open("DELETE", `${environment.apiEndPoint}/flows/${flowId}`);
    xhr.setRequestHeader("Content-Type", "application/json");
    xhr.send(requestBody);
  }

  getFlowPath(flowId):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/flows/path/${flowId}`);
  }


  getReRoutedPath(flowId):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/flows/${flowId}/reroute`);
  }

  getFlowPathStats(jsonPayload):Observable<any>{
    return this.httpClient.post(`${environment.apiEndPoint}/stats/flowpath`,jsonPayload);
  }

  getFlowGraphData(flowid, convertedStartDate, convertedEndDate, downsampling, metric):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/stats/flowid/${flowid}/${convertedStartDate}/${convertedEndDate}/${downsampling}/${metric}`);
  }

  getMeterGraphData(flowid, convertedStartDate, convertedEndDate, downsampling, metric,direction):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/stats/meter/${flowid}/${convertedStartDate}/${convertedEndDate}/${downsampling}/${metric}/${direction}`);
  }

  getFlowPacketGraphData(flowid,convertedStartDate, convertedEndDate, downsampling, direction):Observable<any>{
	  return this.httpClient.get(`${environment.apiEndPoint}/stats/flow/losspackets/${flowid}/${convertedStartDate}/${convertedEndDate}/${downsampling}/${direction}`);
  }

  getFlowCount():Observable<any>{
	  return this.httpClient.get(`${environment.apiEndPoint}/flows/count`);
  }

  resynchFlow(flowId):Observable<any>{
    return this.httpClient.patch(`${environment.apiEndPoint}/flows/${flowId}/sync`,{});
  }

  pingFlow(flowId):Observable<any>{
    return this.httpClient.put(`${environment.apiEndPoint}/flows/${flowId}/ping`,{timeout:3000});
  }

  getcontract(flowid):Observable<any>{
    return this.httpClient.get(`${environment.apiEndPoint}/contracts/list/${flowid}`);
  }

  deletecontract(flowid,contractid){
    return this.httpClient.delete(`${environment.apiEndPoint}/contracts/delete/${flowid}/${contractid}`);
  }

  getStatusList() : Observable<any[]>{
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/flows/status`);
  }

  getFlowStatus(flowid) : Observable<any>{
    return this.httpClient.get<any>(`${environment.apiEndPoint}/flows/${flowid}/status`);
  }
  
}
