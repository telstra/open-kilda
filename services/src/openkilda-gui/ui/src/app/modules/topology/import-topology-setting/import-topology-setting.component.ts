import { Component, OnInit } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { UserService } from '../../../common/services/user.service';
import { ToastrService } from 'ngx-toastr';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
@Component({
  selector: 'app-import-topology-setting',
  templateUrl: './import-topology-setting.component.html',
  styleUrls: ['./import-topology-setting.component.css']
})
export class ImportTopologySettingComponent implements OnInit {
  files:FileList;
  jsondata:any;
  importsettingForm:FormGroup;
  formSubmitted:boolean=false;
  invalidJson:boolean=false;
  constructor(public activeModal: NgbActiveModal,
            private userService:UserService,
            private toastr:ToastrService,
            private modalService:NgbModal,
            private formBuilder:FormBuilder
            ) { 

  }

  ngOnInit() {
    this.importsettingForm = new FormGroup({
      files : new FormControl(null),
      jsondata : new FormControl(null)
    });
  }

  getFile(e){
    this.files = e.target.files;
    if(this.files && this.files.length){
        let reader = new FileReader();
        let self = this;
        reader.onloadend = function(x){
          self.jsondata = reader.result;
        }
        reader.readAsText(this.files[0]);        
        this.disableField('jsondata','files');
    }else{
      this.enableField('jsondata');
      this.enableField('files');
    }
    
  }
  emptyFile(){
    this.formSubmitted = false;
    this.importsettingForm.controls['files'].setValue(null);
    this.disableField('jsondata','files');
  }
  enableField(field){
    this.importsettingForm.controls[field].enable();
  }
  disableField(field,enablefield){
    if(this.importsettingForm.controls[enablefield].value){
      this.importsettingForm.controls[field].disable();
      this.enableField(enablefield);
    }else{
      this.enableField(field);
      this.enableField(enablefield);
    }
    
  }
  submitForm(){ 
    this.formSubmitted = true;
    if(this.jsondata && this.importsettingForm.value.files){
      this.jsondata = JSON.parse(this.jsondata);     
    }else{
      this.jsondata = (this.importsettingForm && this.importsettingForm.value && this.importsettingForm.value.jsondata) ? this.importsettingForm.value.jsondata : null;
    }
    this.invalidJson = !this.IsJsonString(this.jsondata);
    if(this.jsondata && !this.invalidJson){
      this.userService
      .saveSettings(this.jsondata)
      .subscribe(() => {
        this.toastr.success("Setting saved successfully!");
        this.modalService.dismissAll();
        let url = "topology";
        window.location.href = url;
      }, error => {

      });
    }
    return false;
    
  }

  IsJsonString(str) {
    try {
      if(typeof(str) == 'object'){
        str = JSON.stringify(str);
      }
     JSON.parse(str);
    } catch (e) {
        return false;
    }
    return true;
}
  

}
