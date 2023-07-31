import { Component, OnInit,Output, EventEmitter } from '@angular/core';
import { FormBuilder, FormGroup } from '@angular/forms';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';

@Component({
  selector: 'app-islmaintenancemodal',
  templateUrl: './islmaintenancemodal.component.html',
  styleUrls: ['./islmaintenancemodal.component.css']
})
export class IslmaintenancemodalComponent implements OnInit {

  title: any;
  content: any;
  descriptionValue: any;
  isEvacuate:boolean=false;
  isMaintenance: boolean;
  isDescription: boolean;
  DescriptionForm: FormGroup;
  @Output()
  emitService = new EventEmitter();
  showDescriptionEditing: boolean = false;
  constructor(public activeModal: NgbActiveModal,public formBuilder: FormBuilder) { }

  ngOnInit() {   
    this.DescriptionForm = this.formBuilder.group({
      description: [this.descriptionValue],
    });
  }

  setEvacuate(e){
    this.isEvacuate = e.target.checked;
  }
  submitConfirmation() {
    this.activeModal.close(true);
    const data={evaluateValue:this.isEvacuate,description:this.DescriptionForm.value.description}
    if (this.isDescription) {
      this.emitService.emit(data);
    } else {
      this.emitService.emit(this.isEvacuate);
    }
   
  }
  editDescription() {
    this.showDescriptionEditing = true;
  }

  cancelEditedDescription() {
    this.showDescriptionEditing = false;
    this.DescriptionForm.controls["description"].setValue(this.descriptionValue);
  }
}
