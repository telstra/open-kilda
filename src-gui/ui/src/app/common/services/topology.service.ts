import { Injectable, EventEmitter, } from '@angular/core';
import { Observable, Subject, BehaviorSubject } from "rxjs";
import { TopologyView } from '../data-models/topology-view';
import { CookieManagerService } from './cookie-manager.service';

@Injectable({
  providedIn: 'root'
})
export class TopologyService {

  private settingTransmitter = new EventEmitter;
  private autoRefreshTransmitter = new EventEmitter;

  settingReceiver = this.settingTransmitter.asObservable();
  autoRefreshReceiver = this.autoRefreshTransmitter.asObservable();


  linksdata = [];
  failedIsl = [];
  unidirectionalIsl = [];


  
  topologyDefaultViewOptions: TopologyView =  {
    SWITCH_CHECKED: 0,
    ISL_CHECKED: 1,
    FLOW_CHECKED: 0,
    REFRESH_CHECKED: 0,
    REFRESH_INTERVAL: 1,
    REFRESH_TYPE: "m"
  }

  constructor(private cookieService: CookieManagerService) {

  }

  updateTopologyViewSetting(){

    let currentViewSetting = this.getViewOptions();
    /**Transmit new setting to component */
    this.settingTransmitter.emit(currentViewSetting); 
  }

  updateAutoRefreshSetting(){
    let currentViewSetting = this.getViewOptions();
    /**Transmit new setting to component */
    this.autoRefreshTransmitter.emit(currentViewSetting); 
  }

  getViewOptions() : TopologyView{
    let obj = JSON.parse(this.cookieService.get("topologyDefaultViewOptions"));
    if(obj == null){
      return this.topologyDefaultViewOptions;
    }
    return obj;
  }

  setViewOptinos(obj : TopologyView){
    this.cookieService.set("topologyDefaultViewOptions",JSON.stringify(obj));
    this.updateTopologyViewSetting();
  }

  setCoordinates(positions:any){
    if(positions){
      localStorage.setItem('positions',JSON.stringify(positions));
      localStorage.setItem('isDirtyCoordinates','YES');
    }else{
      localStorage.removeItem('positions'); 
      localStorage.removeItem('isDirtyCoordinates');
    }
      
  }

  setCoordinateChangeStatus(status){
    localStorage.setItem('isDirtyCoordinates',status);
  }

  isCoordinatesChanged(){
    let flag = localStorage.getItem('isDirtyCoordinates');
    return flag == 'YES'  ? true : false;
  }

  getCoordinates(){
    return JSON.parse(localStorage.getItem('positions'));
  }

  setAutoRefreshSetting(obj: TopologyView){
    this.cookieService.set("topologyDefaultViewOptions",JSON.stringify(obj));
    this.updateAutoRefreshSetting();
  }

  setLinksData(data){
    this.linksdata = data;

    this.failedIsl = [];
    this.unidirectionalIsl = [];

		for(var i=0,len=data.length;i<len;i++){
			if(data[i].state && data[i].state.toLowerCase()== "failed"){
				this.failedIsl.push(data[i]);
      } 

      if (data[i].unidirectional && data[i].state && data[i].state.toLowerCase()== "discovered"){
        this.unidirectionalIsl.push(data[i]);
      } 
    }

  }

  getLinksData(){
      return   this.linksdata;
  }

  getFailedIsls(){
    return this.failedIsl;
  }

  getUnidirectionalIsl(){
    return this.unidirectionalIsl;
  }
}
