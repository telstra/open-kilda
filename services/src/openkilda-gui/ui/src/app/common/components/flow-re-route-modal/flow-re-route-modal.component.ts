import { Component, OnInit } from '@angular/core';
import { NgbActiveModal, NgbModal,NgbProgressbarModule } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-flow-re-route-modal',
  templateUrl: './flow-re-route-modal.component.html',
  styleUrls: ['./flow-re-route-modal.component.css']
})
export class FlowReRouteModalComponent implements OnInit {

  title: any;
  reRouteIndex:any;
  responseData:any;
  progressHeight= '25px';
  constructor(public activeModal: NgbActiveModal) { }
  
  ngOnInit() {
    this.responseData = Object.keys(this.reRouteIndex);
  }


}
