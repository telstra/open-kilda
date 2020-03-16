import { Component, OnInit,Output, EventEmitter } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-islmaintenancemodal',
  templateUrl: './islmaintenancemodal.component.html',
  styleUrls: ['./islmaintenancemodal.component.css']
})
export class IslmaintenancemodalComponent implements OnInit {

  title: any;
  content: any;
  isEvacuate:boolean=false;
  isMaintenance:boolean;

  @Output()
  emitService = new EventEmitter();

  constructor(public activeModal: NgbActiveModal) { }

  ngOnInit() {
  }

  setEvacuate(e){
    this.isEvacuate = e.target.checked;
  }
  submitConfirmation(){
    this.activeModal.close(true);
    this.emitService.emit(this.isEvacuate);
  }
}
