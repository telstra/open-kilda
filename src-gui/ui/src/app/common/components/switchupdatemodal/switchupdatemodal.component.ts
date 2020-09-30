import { Component, OnInit,Output, EventEmitter } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormGroup, FormBuilder } from '@angular/forms';

@Component({
  selector: 'app-switchupdatemodal',
  templateUrl: './switchupdatemodal.component.html',
  styleUrls: ['./switchupdatemodal.component.css']
})
export class SwitchupdatemodalComponent implements OnInit {

  title: any;
  content: any;
  data:any;
  updateData:any;
  switchLocationForm:FormGroup;
  @Output()
  emitService = new EventEmitter();

  constructor(public activeModal: NgbActiveModal,public formBuilder:FormBuilder) { }

  ngOnInit() {
    this.switchLocationForm = this.formBuilder.group({
      pop: [""],
      latitude:[""],
      longitude:[""],
      street:[""],
      city:[""],
      country:[""]
    });  
    if(this.data && this.data.pop){     
       this.switchLocationForm.controls['pop'].setValue(this.data.pop || '');
       this.switchLocationForm.controls['latitude'].setValue(this.data.latitude || 0);
       this.switchLocationForm.controls['longitude'].setValue(this.data.longitude || 0);
       this.switchLocationForm.controls['street'].setValue(this.data.street || '');
       this.switchLocationForm.controls['city'].setValue(this.data.city || '');
       this.switchLocationForm.controls['country'].setValue(this.data.country || '');
    }
  }

  
  submitUpdate(){
    this.updateData={
      pop:this.switchLocationForm.controls['pop'].value,
      location:{
        latitude:this.switchLocationForm.controls['latitude'].value,
        longitude:this.switchLocationForm.controls['longitude'].value,
        street:this.switchLocationForm.controls['street'].value,
        city:this.switchLocationForm.controls['city'].value,
        country:this.switchLocationForm.controls['country'].value,
      }
    }
    //this.activeModal.close(true);
    this.emitService.emit(this.updateData);
  }

}
