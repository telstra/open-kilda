import { Component, OnInit, AfterViewInit, OnChanges, DoCheck, SimpleChange, SimpleChanges } from "@angular/core";
import { NgbActiveModal, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FormBuilder, FormGroup, Form } from "@angular/forms";
import { ToastrService } from "ngx-toastr";
import { CommonService } from "src/app/common/services/common.service";
import { LoaderService } from "src/app/common/services/loader.service";
import {forkJoin } from "rxjs";
import { ModalconfirmationComponent } from "src/app/common/components/modalconfirmation/modalconfirmation.component";

@Component({
  selector: "app-session",
  templateUrl: "./session.component.html",
  styleUrls: ["./session.component.css"]
})
export class SessionComponent implements OnInit, AfterViewInit, OnChanges,DoCheck {
  sessionForm: FormGroup;
  switchNameSourceForm:FormGroup;
  switchNameSourceTypes:any;
  isEdit = false;
  isSwitchNameSourcEdit = false;
  initialVal = null;
  initialNameSource = null;
  constructor(
    private formBuilder: FormBuilder,
    private commonService: CommonService,
    private loaderService : LoaderService,
    private toastrService : ToastrService,
    private modalService:NgbModal,
  ) {}

  ngOnInit() {
    this.sessionForm = this.formBuilder.group({
      session_time: [""]
    });    
    this.sessionForm.disable();

    this.switchNameSourceForm = this.formBuilder.group({
      switch_name_source: ["FILE_STORAGE"]
    });
    this.switchNameSourceForm.disable();
    
  }

  ngAfterViewInit(){
    this.loaderService.show("Loading Application Setting");
    this.loadAllsettings().subscribe((responseList)=>{
      console.log('responseList',responseList);
      var settings = responseList[0];
       this.sessionForm.setValue({"session_time":settings['SESSION_TIMEOUT']});
       this.initialVal  = settings['SESSION_TIMEOUT'];
       this.switchNameSourceTypes = responseList[1];
       this.switchNameSourceForm.setValue({"switch_name_source":settings['SWITCH_NAME_STORAGE_TYPE'] || 'FILE_STORAGE'});
       this.initialNameSource = settings['SWITCH_NAME_STORAGE_TYPE'] || 'FILE_STORAGE';
      this.loaderService.hide();
    },error=>{
      var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:'Api response error';
      this.toastrService.error(errorMsg,'Error');
      this.loaderService.hide();
    });
   
  }

  loadAllsettings(){
    let allSettings = this.commonService.getAllSettings();
     let SwitchNameListTypes = this.commonService.getSwitchNameSourceTypes();
    return forkJoin([allSettings,SwitchNameListTypes]);
  }

  ngDoCheck(){

  }

  ngOnChanges(change:SimpleChanges){

  }

  get i() {
    return this.sessionForm.controls;
  }
  get s(){
    return this.switchNameSourceForm.controls;
  }

  save(){

    
    let session_time = this.sessionForm.controls['session_time'].value;
    if(session_time < 5){
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to save session settings ?';
    modalReff.result.then((response) => {
      if(response && response == true){
        this.loaderService.show("Saving Session Setting");
          this.commonService.saveSessionTimeoutSetting(session_time).subscribe((response)=>{
            this.toastrService.success("Session Setting saved",'Success');
            this.loaderService.hide();
            this.initialVal = this.sessionForm.controls['session_time'].value;
            this.isEdit = false;
            this.sessionForm.disable();
          },error=>{
            var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:'Unable to save';
            this.toastrService.error(errorMsg,'Error');
            this.loaderService.hide();
          });
      }
    });
    
  }

  editSession(){
    this.isEdit = true;
    this.sessionForm.enable();
  }

  cancelSession(){
    this.sessionForm.setValue({"session_time":this.initialVal});
    this.isEdit = false;
    this.sessionForm.disable();
  }
  
  saveSwitchNameSource(){
    let source_name_source = this.switchNameSourceForm.controls['switch_name_source'].value;
    if(source_name_source == ''){
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to save switch name source settings ?';
    modalReff.result.then((response) => {
      if(response && response == true){
        this.loaderService.show("Saving Switch Name Source Setting");
        this.commonService.saveSwitchNameSourceSettings(source_name_source).subscribe((response)=>{
          this.toastrService.success("Switch Name Source Saved",'Success');
          this.loaderService.hide();
          this.initialNameSource = this.switchNameSourceForm.controls['switch_name_source'].value;
          this.isSwitchNameSourcEdit = false;
          this.switchNameSourceForm.disable();
        },error=>{
          var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:'Unable to save';
          this.toastrService.error(errorMsg,'Error');
          this.loaderService.hide();
        });
      }
    });
  }
  editSwitchNameSource(){
    this.isSwitchNameSourcEdit = true;
    this.switchNameSourceForm.enable();
  }

  cancelSwitchNameSource(){
    this.switchNameSourceForm.setValue({"switch_name_source":this.initialNameSource});
    this.isSwitchNameSourcEdit = false;
    this.switchNameSourceForm.disable();
  }

}
