import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { IslModel } from '../data-models/isl-model';
import { catchError } from 'rxjs/operators';

@Injectable({
  providedIn: 'root'
})
export class IslListService {
  constructor(private httpClient: HttpClient) {}
  getIslList(query?:any) : Observable<IslModel[]>{
    return this.httpClient.get<IslModel[]>(`${environment.apiEndPoint}/switch/links`,{params:query});
  }
  
  getIslDetail(src_switch, src_port, dst_switch, dst_port):Observable<IslModel>{
 	let date = new Date().getTime();
    return this.httpClient.get<IslModel>(`${environment.apiEndPoint}/switch/link/props?src_switch=${src_switch}&src_port=${src_port}&dst_switch=${dst_switch}&dst_port=${dst_port}&_=${date}`);
  }

  updateCost(src_switch, src_port, dst_switch, dst_port, cost): Observable<{}>{
	let requestPayload = [
		{"src_switch":src_switch,
		 "src_port":src_port,
		 "dst_switch":dst_switch,
		 "dst_port":dst_port,
		 "props":{"cost":cost}
		},
		{"src_switch":dst_switch,
		 "src_port":dst_port,
		 "dst_switch":src_switch,
		 "dst_port":src_port,
		 "props":{"cost":cost}
		}
	];
        const url = `${environment.apiEndPoint}/switch/link/props`; 
        return this.httpClient.put(url,requestPayload);
    }
}
