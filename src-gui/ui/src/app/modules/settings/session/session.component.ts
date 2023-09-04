import { Component, OnInit, AfterViewInit, OnChanges, DoCheck, SimpleChange, SimpleChanges } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, Form } from '@angular/forms';
import { ToastrService } from 'ngx-toastr';
import { CommonService } from 'src/app/common/services/common.service';
import { LoaderService } from 'src/app/common/services/loader.service';
import {forkJoin } from 'rxjs';
import { ModalconfirmationComponent } from 'src/app/common/components/modalconfirmation/modalconfirmation.component';
import { Router } from '@angular/router';
import { MessageObj } from 'src/app/common/constants/constants';
@Component({
  selector: 'app-session',
  templateUrl: './session.component.html',
  styleUrls: ['./session.component.css']
})
export class SessionComponent implements OnInit, AfterViewInit, OnChanges, DoCheck {
  sessionForm: FormGroup;
  switchNameSourceForm: FormGroup;
  userUnlockForm: FormGroup;
  loginAttemptForm: FormGroup;
  switchNameSourceTypes: any;
  isEdit = false;
  isSwitchNameSourcEdit = false;
  isloginAttemptEdit = false;
  isUserUnlockEdit = false;
  initialVal = null;
  initialNameSource = null;
  intialLoginAttemptValue = null;
  initialUserUnlockValue = null;
  constructor(
    private formBuilder: FormBuilder,
    private commonService: CommonService,
    private loaderService: LoaderService,
    private toastrService: ToastrService,
    private modalService: NgbModal,
    private toastr: ToastrService,
    private router: Router
  ) {

    if (!this.commonService.hasPermission('application_setting')) {
      this.toastr.error(MessageObj.unauthorised);
       this.router.navigate(['/home']);
      }
  }

  ngOnInit() {
    this.sessionForm = this.formBuilder.group({
      session_time: ['']
    });
    this.sessionForm.disable();

    this.switchNameSourceForm = this.formBuilder.group({
      switch_name_source: ['FILE_STORAGE']
    });
    this.switchNameSourceForm.disable();

    this.loginAttemptForm = this.formBuilder.group({
      login_attempt: ['']
    });
    this.loginAttemptForm.disable();
    this.userUnlockForm = this.formBuilder.group({
      user_unlock_time: ['']
    });
    this.userUnlockForm.disable();

  }

  ngAfterViewInit() {
    this.loaderService.show(MessageObj.loading_app_setting);
    this.loadAllsettings().subscribe((responseList) => {
      const settings = responseList[0];
       this.sessionForm.setValue({'session_time': settings['SESSION_TIMEOUT']});
       this.initialVal  = settings['SESSION_TIMEOUT'];
       this.switchNameSourceTypes = responseList[1];
       this.switchNameSourceForm.setValue({'switch_name_source': settings['SWITCH_NAME_STORAGE_TYPE'] || 'FILE_STORAGE'});
       this.initialNameSource = settings['SWITCH_NAME_STORAGE_TYPE'] || 'FILE_STORAGE';
       this.intialLoginAttemptValue =  settings['INVALID_LOGIN_ATTEMPT'] || 5;
       this.loginAttemptForm.setValue({'login_attempt': settings['INVALID_LOGIN_ATTEMPT']});
       this.initialUserUnlockValue =  settings['USER_ACCOUNT_UNLOCK_TIME'] || 60;
       this.userUnlockForm.setValue({'user_unlock_time': settings['USER_ACCOUNT_UNLOCK_TIME']});
      this.loaderService.hide();
    }, error => {
      const errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message'] : 'Api response error';
      this.toastrService.error(errorMsg, 'Error');
      this.loaderService.hide();
    });

  }

  loadAllsettings() {
    const allSettings = this.commonService.getAllSettings();
     const SwitchNameListTypes = this.commonService.getSwitchNameSourceTypes();
    return forkJoin([allSettings, SwitchNameListTypes]);
  }

  ngDoCheck() {

  }

  ngOnChanges(change: SimpleChanges) {

  }

  get i() {
    return this.sessionForm.controls;
  }
  get s() {
    return this.switchNameSourceForm.controls;
  }

  save() {


    const session_time = this.sessionForm.controls['session_time'].value;
    if (session_time < 5) {
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = 'Confirmation';
    modalReff.componentInstance.content = 'Are you sure you want to save session settings ?';
    modalReff.result.then((response) => {
      if (response && response == true) {
        this.loaderService.show(MessageObj.saving_session_setting);
          this.commonService.saveSessionTimeoutSetting(session_time).subscribe((response) => {
            this.toastrService.success(MessageObj.session_setting_saved, 'Success');
            this.loaderService.hide();
            this.initialVal = this.sessionForm.controls['session_time'].value;
            this.isEdit = false;
            this.sessionForm.disable();
          }, error => {
            const errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message'] : 'Unable to save';
            this.toastrService.error(errorMsg, 'Error');
            this.loaderService.hide();
          });
      }
    });

  }

  editSession() {
    this.isEdit = true;
    this.sessionForm.enable();
  }

  cancelSession() {
    this.sessionForm.setValue({'session_time': this.initialVal});
    this.isEdit = false;
    this.sessionForm.disable();
  }

  saveSwitchNameSource() {
    const source_name_source = this.switchNameSourceForm.controls['switch_name_source'].value;
    if (source_name_source == '') {
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = 'Confirmation';
    modalReff.componentInstance.content = 'Are you sure you want to save switch name source settings ?';
    modalReff.result.then((response) => {
      if (response && response == true) {
        this.loaderService.show(MessageObj.saving_switch_name_store_setting);
        this.commonService.saveSwitchNameSourceSettings(source_name_source).subscribe((response) => {
          this.toastrService.success(MessageObj.switch_name_store_setting_saved, 'Success');
          this.loaderService.hide();
          this.initialNameSource = this.switchNameSourceForm.controls['switch_name_source'].value;
          this.isSwitchNameSourcEdit = false;
          this.switchNameSourceForm.disable();
        }, error => {
          const errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message'] : 'Unable to save';
          this.toastrService.error(errorMsg, 'Error');
          this.loaderService.hide();
        });
      }
    });
  }
  editSwitchNameSource() {
    this.isSwitchNameSourcEdit = true;
    this.switchNameSourceForm.enable();
  }

  cancelSwitchNameSource() {
    this.switchNameSourceForm.setValue({'switch_name_source': this.initialNameSource});
    this.isSwitchNameSourcEdit = false;
    this.switchNameSourceForm.disable();
  }

  saveUserUnlockTimeSetting() {
    const unlock_time = this.userUnlockForm.controls['user_unlock_time'].value;
    if (unlock_time == '') {
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = 'Confirmation';
    modalReff.componentInstance.content = 'Are you sure you want to save user unlock time settings ?';
    modalReff.result.then((response) => {
      if (response && response == true) {
        this.loaderService.show(MessageObj.saving_unlock_time_setting);
        this.commonService.saveUserAccountUnlockTimeSettings(unlock_time).subscribe((response) => {
          this.toastrService.success(MessageObj.user_unlock_time_setting_saved, 'Success');
          this.loaderService.hide();
          this.initialUserUnlockValue = this.userUnlockForm.controls['user_unlock_time'].value;
          this.isUserUnlockEdit = false;
          this.userUnlockForm.disable();
        }, error => {
          const errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message'] : 'Unable to save';
          this.toastrService.error(errorMsg, 'Error');
          this.loaderService.hide();
        });
      }
    });
  }

  editUserUnlockTimeSetting() {
    this.isUserUnlockEdit = true;
    this.userUnlockForm.enable();
  }

  cancelUserUnlockTimeSetting() {
    this.userUnlockForm.setValue({'user_unlock_time': this.initialUserUnlockValue});
    this.isUserUnlockEdit = false;
    this.userUnlockForm.disable();
  }


  saveLoginAttemptSetting() {
    const login_attempt = this.loginAttemptForm.controls['login_attempt'].value;
    if (login_attempt == '') {
      return false;
    }
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = 'Confirmation';
    modalReff.componentInstance.content = 'Are you sure you want to save user login attempt settings ?';
    modalReff.result.then((response) => {
      if (response && response == true) {
        this.loaderService.show(MessageObj.saving_login_attempt_setting);
        this.commonService.saveInvalidLoginAttemptSettings(login_attempt).subscribe((response) => {
          this.toastrService.success(MessageObj.user_login_attempt_setting_saved, 'Success');
          this.loaderService.hide();
          this.intialLoginAttemptValue = this.loginAttemptForm.controls['login_attempt'].value;
          this.isloginAttemptEdit = false;
          this.loginAttemptForm.disable();
        }, error => {
          const errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message'] : 'Unable to save';
          this.toastrService.error(errorMsg, 'Error');
          this.loaderService.hide();
        });
      }
    });
  }

  editLoginAttemptSetting() {
    this.isloginAttemptEdit = true;
    this.loginAttemptForm.enable();
  }

  cancelLoginAttemptSetting() {
    this.loginAttemptForm.setValue({'login_attempt': this.intialLoginAttemptValue});
    this.isloginAttemptEdit = false;
    this.loginAttemptForm.disable();
  }
}
