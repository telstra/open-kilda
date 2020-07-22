import { Injectable } from '@angular/core';
import { BehaviorSubject } from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class IslDataService {


  private messageSource = new BehaviorSubject({});
  private graphOptionsObject = new BehaviorSubject({});
  private islFlowObj = new BehaviorSubject({});
  currentMessage = this.messageSource.asObservable();
  currentOptionsObject = this.graphOptionsObject.asObservable();
  IslFlowGraph = this.islFlowObj.asObservable();

  constructor() { }

  changeMessage(message: {}) {
    this.messageSource.next(message)
  }

  changeGraphOptionsObject(optionsObject: {}) {
    this.graphOptionsObject.next(optionsObject)
  }
  changeIslFlowGraph(dataObj:{}){
    this.islFlowObj.next(dataObj);
  }
}
