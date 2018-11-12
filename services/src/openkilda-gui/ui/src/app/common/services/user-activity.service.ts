import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { environment } from '../../../environments/environment';
import { Observable } from 'rxjs';
import { UserActivityModel } from '../data-models/user-activity-model';


@Injectable({
  providedIn: 'root'
})
export class UserActivityService {

  constructor(private httpClient: HttpClient) { }

  getUserActivityList() : Observable<UserActivityModel[]>{
    let date = new Date().getTime();
   	return this.httpClient.get<UserActivityModel[]>(`${environment.apiEndPoint}/useractivity/log?_=${date}`);
  }

   getFilteredUserActivityList(username, type, startDate, endDate) : Observable<UserActivityModel[]>{
   let url: string = '';
   let replacement ="";
   let currentDate =  new Date().getTime();
   if(type.length || username.length || startDate !='' || endDate!=''){
        url+="";
        if(type.length){
           for(let i=0;i<type.length;i++){
                 url+='activity='+type[i]+"&";
            }}
         if(username.length){
           for(let i=0;i<username.length;i++){
           url+='userId='+username[i]+"&";
           }
         }
         if(startDate!='' && startDate!= undefined){
           url+='startTime='+new Date(startDate).getTime()+"&";
         }
         if(endDate!='' && endDate!= undefined){
           url+='endTime='+new Date(endDate).getTime();
         }
    }    
	return this.httpClient.get<UserActivityModel[]>(`${environment.apiEndPoint}/useractivity/log?${url}&_=${currentDate}`);
  }


  getUserDropdownList() : Observable<UserActivityModel[]>{
    let date = new Date().getTime();
	return this.httpClient.get<UserActivityModel[]>(`${environment.apiEndPoint}/user?_=${date}`);
  }

  getTypeDropdownList() : Observable<UserActivityModel[]>{
    let date = new Date().getTime();
	return this.httpClient.get<UserActivityModel[]>(`${environment.apiEndPoint}/useractivity/types?_=${date}`);
  }

}
