import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { Flow } from '../data-models/flow';
import { CookieManagerService } from './cookie-manager.service';

@Injectable({
  providedIn: 'root'
})
export class FlowsService {

  constructor(private httpClient: HttpClient, private cookieManager: CookieManagerService) {

  }

  getFlowsList(query?: any): Observable<Flow[]> {
    return this.httpClient.get<Flow[]>(`${environment.apiEndPoint}/flows/list`, {params: query});
  }


  getFlowDetailById(flowId, filterFlag): Observable<any> {
    const query: any = {controller: filterFlag == 'controller'};
    return this.httpClient.get<any>(`${environment.apiEndPoint}/flows/${flowId}`, {params: query});
  }

  createFlow(data): Observable<any> {
    return this.httpClient.put(`${environment.apiEndPoint}/flows`, data);
  }

  validateFlow(flowId): Observable<any> {
    return this.httpClient.get(`${environment.apiEndPoint}/flows/${flowId}/validate`);
  }

  getFlowHistory(flowId, fromDate, toDate): Observable<any> {
    const query: any = {timeFrom: fromDate, timeTo: toDate};
    return this.httpClient.get(`${environment.apiEndPoint}/flows/all/history/${flowId}`, {params: query});
  }


  updateFlow(flowId, payload): Observable<any> {
    return this.httpClient.put(`${environment.apiEndPoint}/flows/${flowId}`, payload);
  }

  deleteFlow(flowId, data, successCb, errorCb): void {
    const requestBody = JSON.stringify(data);
    const token = this.cookieManager.get('XSRF-TOKEN') as string;

    const xhr = new XMLHttpRequest();
    xhr.withCredentials = false;
    xhr.addEventListener('readystatechange', function () {
      if (this.readyState == 4 && this.status == 200) {
        successCb(JSON.parse(this.responseText));
      } else if (this.readyState == 4 && this.status >= 300) {
        errorCb(JSON.parse(this.responseText));
      }
    });

    xhr.open('DELETE', `${environment.apiEndPoint}/flows/${flowId}`);
    xhr.setRequestHeader('Content-Type', 'application/json');
    if (token !== null) {
      xhr.setRequestHeader( 'X-XSRF-TOKEN' , token);
    }
    xhr.send(requestBody);
  }

  getFlowPath(flowId): Observable<any> {
    return this.httpClient.get(`${environment.apiEndPoint}/flows/path/${flowId}`);
  }

  getConnectedDevices(flowId): Observable<any> {
    return this.httpClient.get(`${environment.apiEndPoint}/flows/connected/devices/${flowId}`);
  }


  getReRoutedPath(flowId): Observable<any> {
    return this.httpClient.get(`${environment.apiEndPoint}/flows/${flowId}/reroute`);
  }

    getYFlowReRoutedPath(yFlowId): Observable<any> {
        return this.httpClient.get(`${environment.apiEndPoint}/y-flows/${yFlowId}/reroute`);
    }

  getFlowCount(): Observable<any> {
	  return this.httpClient.get(`${environment.apiEndPoint}/flows/count`);
  }

  resynchFlow(flowId): Observable<any> {
    return this.httpClient.patch(`${environment.apiEndPoint}/flows/${flowId}/sync`, {});
  }

  pingFlow(flowId): Observable<any> {
    return this.httpClient.put(`${environment.apiEndPoint}/flows/${flowId}/ping`, {timeout: 3000});
  }

  getcontract(flowid): Observable<any> {
    return this.httpClient.get(`${environment.apiEndPoint}/contracts/list/${flowid}`);
  }

  deletecontract(flowid, contractid) {
    return this.httpClient.delete(`${environment.apiEndPoint}/contracts/delete/${flowid}/${contractid}`);
  }

  getStatusList(): Observable<any[]> {
    return this.httpClient.get<any[]>(`${environment.apiEndPoint}/flows/status`);
  }

  getFlowStatus(flowid): Observable<any> {
    return this.httpClient.get<any>(`${environment.apiEndPoint}/flows/${flowid}/status`);
  }

}
