import { Injectable,EventEmitter } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';

import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class CommonService {

  private linkStoreTransmitter = new EventEmitter;
  linkStoreReceiver = this.linkStoreTransmitter.asObservable();

  private sessionTranmitter = new EventEmitter;
  sessionReceiver = this.sessionTranmitter.asObservable();
  currentUrl = null;

  constructor(private httpClient:HttpClient) { }
  
  groupBy(array , f)
  {
		  var groups = {};
		  array.forEach( function( o )
		  {
		    var group = JSON.stringify( f(o) );
		    groups[group] = groups[group] || [];
		    groups[group].push( o );  
		  });
		  return Object.keys(groups).map( function( group )
		  {
		    return groups[group]; 
		  })
  }
 getCommonColorCode(i,arr){
   var colourArr = ['#e6194b', '#3cb44b', '#ffe119', '#4363d8', '#f58231', '#911eb4', '#46f0f0', '#f032e6', '#bcf60c', '#fabebe', '#008080', '#e6beff', '#9a6324', '#fffac8', '#800000', '#aaffc3', '#808000', '#ffd8b1', '#000075', '#808080', '#ffffff', '#000000'];
   if(i < colourArr.length){
    return colourArr[i];
   }else{
    return this.getColorCode(i,arr);
   } 
   
  }
  getColorCode(j, arr) {
    var chars = "0123456789ABCDE".split("");
    var hex = "#";
    for (var i = 0; i < 6; i++) {
      hex += chars[Math.floor(Math.random() * 14)];
    }
    var colorCode = hex;
    if (arr.indexOf(colorCode) < 0) {
      return colorCode;
    } else {
      this.getColorCode(j, arr);
    }
  }

  pluck(array,key){
   return array.map(function(d){
      return d[key];
    })
  }

  getPercentage(val,baseVal){
    var percentage = (val/baseVal) * 100;
    var percentage_fixed = percentage.toFixed(2);
    var value_percentage = percentage_fixed.split(".");
    if(parseInt(value_percentage[1]) > 0){
      return percentage.toFixed(2);
    }else{
      return value_percentage[0];
    }
      
  }

  hasPermission(permission){
    if(JSON.parse(localStorage.getItem("userPermissions"))) {
        let userPermissions = JSON.parse(localStorage.getItem("userPermissions"));
        if((userPermissions).find(up => up == permission)){
            return true;
        }else{
            return false;
        }
    }
    return false;
  }

  setIdentityServer(value:Boolean){
    this.linkStoreTransmitter.emit(value);
  }

  setCurrentUrl(url){
    this.currentUrl  = url;
  }
  
  getCurrentUrl(){
    return this.currentUrl;
  }

  setUserData(user){
    this.sessionTranmitter.emit(user);
  }

  getLogout():Observable<any>{
    return this.httpClient.get<any>(`${environment.appEndPoint}/logout`);
  }
  getAutoreloadValues(){
    return [
      {value:10,text:'10'},
      {value:15,text:'15'},
      {value:30,text:'30'},
      {value:45,text:'45'},
      {value:60,text:'60'},
    ]
  }
  convertBytesToMbps(value){
    let valInMbps = (value/1000)/1000; // conversion
    return (valInMbps < 1)?Math.ceil(valInMbps * 1000) / 1000:Math.ceil(valInMbps * 100) / 100
  }

  convertNumberToString(data){
    var returnDatajson = data.replace(/([\[:])?(\d+)([,\}\]])/g, "$1\"$2\"$3").replace(/-"/g,'\"-');
   returnDatajson = JSON.parse(returnDatajson);
   return returnDatajson;
  }
  convertDpsToSecond(data){
    var dps = (data.dps && Object.keys(data.dps).length > 0) ? data.dps: [];
    var dpsData = {};
    Object.keys(dps).forEach(function(i,v){
       dpsData[i] = Number(dps[i] / 1000000000).toFixed(4);
    })
    data.dps = dpsData;
    return data;
  }
  convertDpsToMicroSecond(data){
    var dps = (data.dps && Object.keys(data.dps).length > 0) ? data.dps: [];
    var dpsData = {};
    Object.keys(dps).forEach(function(i,v){
       dpsData[i] = Number(dps[i] / 1000).toFixed(4);
    })
    data.dps = dpsData;
    return data;
  }
  convertDpsToMilliSecond(data){
    var dps = (data.dps && Object.keys(data.dps).length > 0) ? data.dps: [];
    var dpsData = {};
    Object.keys(dps).forEach(function(i,v){
       dpsData[i] = Number(dps[i] / 1000000).toFixed(4);
    })
    data.dps = dpsData;
    return data;
  }

  saveSessionTimeoutSetting(timeout){
    return this.httpClient.patch<any>(`${environment.apiEndPoint}/settings/SESSION_TIMEOUT`,timeout);
  }

  getSwitchNameSourceTypes(){
    return this.httpClient.get<any>(`${environment.apiEndPoint}/settings/storagetypes`);
  }
 
  saveSwitchNameSourceSettings(value){
    return this.httpClient.patch<any>(`${environment.apiEndPoint}/settings/SWITCH_NAME_STORAGE_TYPE`,value);
  }
  getAllSettings(){
    return this.httpClient.get<any>(`${environment.apiEndPoint}/settings`);
  }
}
