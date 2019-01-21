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
}