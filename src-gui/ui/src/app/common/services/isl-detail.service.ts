import { Injectable } from '@angular/core';
import { Observable, Subject } from 'rxjs';
import { environment } from '../../../environments/environment';
import { Flow } from '../data-models/flow';
import { HttpClient,HttpHeaders } from '@angular/common/http';

@Injectable({ providedIn: 'root' })
export class IslDetailService {
    private subject = new Subject<any>();
    constructor(private httpClient: HttpClient) {
    
    }
    setSelectedItem(item: {}) {
        this.subject.next({ item: item });
    }

    getSelectedItem(): Observable<any> {
        return this.subject.asObservable();
    }
    getISLFlowsList(query? : any) : Observable<Flow[]>{
        return this.httpClient.get<Flow[]>(`${environment.apiEndPoint}/switch/links/flows`,{params:query});
  }
  getIslLatencyfromGraph(src_switch,src_port,dst_switch,dst_port,from,to,frequency){
    return this.httpClient.get<any[]>(
        `${
          environment.apiEndPoint
        }/stats/isl/${src_switch}/${src_port}/${dst_switch}/${dst_port}/${from}/${to}/${frequency}/latency`
      );
  }
}