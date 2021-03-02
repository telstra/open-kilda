import { Component, OnInit } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-flow-ping-modal',
  templateUrl: './flow-ping-modal.component.html',
  styleUrls: ['./flow-ping-modal.component.css']
})
export class FlowPingModalComponent implements OnInit {
  title: any;
  pingFlowIndex:any;
  selectedFlowList:any;
  responseData:any;
  progressHeight= '25px';
  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit() {
  }

}
