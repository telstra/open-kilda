import { Component, OnInit, AfterViewInit, OnChanges, DoCheck, SimpleChange, SimpleChanges } from "@angular/core";
import { NgbActiveModal, NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { FormBuilder, FormGroup } from "@angular/forms";
import { ToastrService } from "ngx-toastr";
import { CommonService } from "src/app/common/services/common.service";
import { LoaderService } from "src/app/common/services/loader.service";

@Component({
  selector: "app-session",
  templateUrl: "./session.component.html",
  styleUrls: ["./session.component.css"]
})
export class SessionComponent implements OnInit, AfterViewInit, OnChanges,DoCheck {
  sessionForm: FormGroup;
  isEdit = false;
  initialVal = null;
  constructor(
    private formBuilder: FormBuilder,
    private commonService: CommonService,
    private loaderService : LoaderService,
    private toastrService : ToastrService,
  ) {}

  ngOnInit() {
    this.sessionForm = this.formBuilder.group({
      session_time: [""]
    });

    this.sessionForm.disable();
    
  }

  ngAfterViewInit(){
    this.loaderService.show("Loading Session Setting");
    this.commonService.getSessionTimeoutSetting().subscribe((response)=>{
      this.sessionForm.setValue
      this.sessionForm.setValue({"session_time":response});
      this.initialVal  = response;
      this.loaderService.hide();
    },error=>{
      this.toastrService.error("Api response error",'Error');
      this.loaderService.hide();
    });
  }

  ngDoCheck(){

  }

  ngOnChanges(change:SimpleChanges){

  }

  setInfinite(){
    this.sessionForm.controls['session_time'].setValue(-1);
    this.save();
  }

  save(){

    this.loaderService.show("Saving Session Setting");
    let session_time = this.sessionForm.controls['session_time'].value;
    this.commonService.saveSessionTimeoutSetting(session_time).subscribe((response)=>{
      this.toastrService.success("Session Setting saved",'Success');
      this.loaderService.hide();
      this.initialVal = this.sessionForm.controls['session_time'].value;
      this.isEdit = false;
      this.sessionForm.disable();
    },error=>{
      this.toastrService.error("Unable to save",'Error');
      this.loaderService.hide();
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

}
