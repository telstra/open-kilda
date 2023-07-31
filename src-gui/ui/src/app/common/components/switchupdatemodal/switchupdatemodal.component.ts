import { Component, OnInit, Output, EventEmitter } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormGroup, FormBuilder } from '@angular/forms';
import { CommonService } from '../../services/common.service';
import { ToastrService } from 'ngx-toastr';

@Component({
  selector: 'app-switchupdatemodal',
  templateUrl: './switchupdatemodal.component.html',
  styleUrls: ['./switchupdatemodal.component.css']
})
export class SwitchupdatemodalComponent implements OnInit {

  title: any;
  content: any;
  data: any;
  updateData: any;
  errorsObj: any;
  switchLocationForm: FormGroup;
  @Output()
  emitService = new EventEmitter();
  submitted = false;

  constructor(public activeModal: NgbActiveModal, public formBuilder: FormBuilder, private commonService: CommonService, private toaster: ToastrService) { }

  ngOnInit() {
    this.errorsObj = {};
    this.switchLocationForm = this.formBuilder.group({
      pop: [''],
      latitude: [''],
      longitude: [''],
      street: [''],
      city: [''],
      country: ['']
    });
     if (this.data && this.data.pop) {
      this.switchLocationForm.controls['pop'].setValue(this.data.pop || '');
     }
     if (this.data && (this.data.latitude || this.data.latitude == 0)) {
      this.switchLocationForm.controls['latitude'].setValue(this.data.latitude || 0);
    }
    if (this.data && (this.data.longitude || this.data.longitude == 0)) {
      this.switchLocationForm.controls['longitude'].setValue(this.data.longitude || 0);
    }
    if (this.data && this.data.street) {
      this.switchLocationForm.controls['street'].setValue(this.data.street || '');
    }
    if (this.data && this.data.city) {
      this.switchLocationForm.controls['city'].setValue(this.data.city || '');
    }
    if (this.data && this.data.country) {
      this.switchLocationForm.controls['country'].setValue(this.data.country || '');
    }

  }

  validateLatLong(field) {
    if (field == 'latitude') {
      if (!this.commonService.isInt(this.switchLocationForm.controls['latitude'].value) && !this.commonService.isFloat(this.switchLocationForm.controls['latitude'].value)) {
        this.errorsObj['latitude'] = true;
      } else {
        this.errorsObj['latitude'] = false;
      }
    }
    if (field == 'longitude') {
      if (!this.commonService.isInt(this.switchLocationForm.controls['longitude'].value) && !this.commonService.isFloat(this.switchLocationForm.controls['longitude'].value)) {
        this.errorsObj['longitude'] = true;
      } else {
        this.errorsObj['longitude'] = false;
      }
    }
  }

  submitUpdate() {
    this.errorsObj = {};
    this.submitted = true;
    this.updateData = {
      pop: this.switchLocationForm.controls['pop'].value,
      location: {
        latitude: this.switchLocationForm.controls['latitude'].value,
        longitude: this.switchLocationForm.controls['longitude'].value,
        street: this.switchLocationForm.controls['street'].value,
        city: this.switchLocationForm.controls['city'].value,
        country: this.switchLocationForm.controls['country'].value,
      }
    };

    let errorFlag = false;
    if (!this.commonService.isInt(this.updateData.location.latitude) && !this.commonService.isFloat(this.updateData.location.latitude)) {
      errorFlag = true;
      this.errorsObj['latitude'] = true;
    }

    if (!this.commonService.isInt(this.updateData.location.longitude) && !this.commonService.isFloat(this.updateData.location.longitude)) {
      errorFlag = true;
      this.errorsObj['longitude'] = true;
    }
     if (errorFlag) {
      return false;
    } else {
    this.submitted = false;
    this.emitService.emit(this.updateData);
    }
  }

}
