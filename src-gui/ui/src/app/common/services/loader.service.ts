import { Injectable } from "@angular/core";
import { Observable, Subject, BehaviorSubject } from "rxjs";

@Injectable({
  providedIn: "root"
})
export class LoaderService {
  private messageSender = new Subject<any>();

  messageReciever = this.messageSender.asObservable();
  constructor() {}

  show(message : string = null) {
    this.messageSender.next({show:true,message:message});
  }

  hide(){
    this.messageSender.next({show:false,message:null});
  }

 

  

}
