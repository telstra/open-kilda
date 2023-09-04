import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, NgForm, FormControl } from '@angular/forms';
import { StoreSettingtService } from '../../../common/services/store-setting.service';
import { IdentityServerModel } from '../../../common/data-models/identityserver-model';
import { ToastrService } from 'ngx-toastr';
import { LoaderService } from '../../../common/services/loader.service';
import { CommonService } from 'src/app/common/services/common.service';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-identity-server',
  templateUrl: './identity-server.component.html',
  styleUrls: ['./identity-server.component.css']
})
export class IdentityServerComponent implements OnInit {
  identityServerForm: FormGroup;
  isEdit = false;
  isEditable = false;
  submitted = false;
  urlPattern = '(https?://)?([\\da-z.-]+)\\.([a-z.]{2,6})[/\\w .-]*/?';
  IdentityDetailObj: IdentityServerModel;
  constructor(
    private storesettingservice: StoreSettingtService,
    private formbuilder: FormBuilder,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private commonService: CommonService
    ) { }

  ngOnInit() {
    // const reg =new RegExp('^(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)');
      this.identityServerForm = this.formbuilder.group({
      'authType': 'OAUTH_TWO',
      'username': ['', Validators.required],
      'password': ['', Validators.required],
      'oauth-generate-token-url': this.formbuilder.group({
        'name': [ 'oauth-generate-token'],
        'method-type': ['POST'],
        'url': ['', Validators.compose([
                  Validators.required,
                  (control: FormControl) => {
                    const url = control.value;
                      if (!this.validateUrl(url)) {
                        return {
                          pattern: {
                            url: url
                          }
                        };
                      }
                      return null;
                  },
                ])],
        'header': [ ''],
        'body': [ '']
      }),
      'oauth-refresh-token-url': this.formbuilder.group({
        'name': ['oauth-refresh-token'],
        'method-type': ['POST'],
        'url': ['', Validators.compose([
                Validators.required,
                (control: FormControl) => {
                  const url = control.value;
                    if (!this.validateUrl(url)) {
                      return {
                        pattern: {
                          url: url
                        }
                      };
                    }
                    return null;
                },
              ])],
        'header': [''],
        'body': ['']
      })
    });
    this.loaderService.show(MessageObj.loading_is_detail);
    this.storesettingservice.getIdentityServerConfigurations().subscribe((jsonResponse) => {
      if (jsonResponse && jsonResponse['oauth-generate-token-url'] && typeof(jsonResponse['oauth-generate-token-url']['url']) !== 'undefined' ) {
        this.commonService.setIdentityServer(true);
        this.IdentityDetailObj = jsonResponse;
        this.identityServerForm.setValue(jsonResponse);
        this.identityServerForm.disable();
        this.isEdit = true;
        this.loaderService.hide();
			} else {
        this.loaderService.hide();
      }
    }, (err) => {
      this.loaderService.hide();
    });
  }

   /** getter to get form fields */
   get i() {
     return this.identityServerForm.controls;
   }
   validateUrl(url) {
     if (url == '' || url == null) {
       return true;
     }
		const res = url.match(/(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)/g);
	    if (res == null) {
	        return false;
	    } else {
	        return true;
	    }
	}
   enableEditForm() {
     this.isEditable = true;
     this.identityServerForm.enable();
   }
   cancelEditForm() {
    this.isEditable = false;
    this.isEdit = true;
    this.identityServerForm.reset();
    this.identityServerForm.setValue( this.IdentityDetailObj);
    this.identityServerForm.disable();
   }

   validateIdentityData() {
     this.submitted = true;
     if (this.identityServerForm.invalid) {
        return;
      }
      this.submitted = false;
      const username = this.identityServerForm.value.username;
			const password = this.identityServerForm.value.password;
			const tokenUrl = this.identityServerForm.value['oauth-generate-token-url'].url;
			const refreshTokenUrl = this.identityServerForm.value['oauth-refresh-token-url'].url;
      const postData = decodeURIComponent('grant_type=password&username=' + username + '&password=' + password);
      this.loaderService.show(MessageObj.validating_is_server);
			this.storesettingservice.generateorRefreshToken(tokenUrl, postData).subscribe(
        (response: any) => {
			 if (response && response.access_token) {
						const token = response.access_token;
						const refresh_token = response.refresh_token;
            const postDataForRefresh = decodeURIComponent('grant_type=refresh_token&refresh_token=' + refresh_token);
           this.storesettingservice.generateorRefreshToken(refreshTokenUrl, postDataForRefresh).subscribe(
              (response: any) => {
              // submit data to save
              this.loaderService.hide();
              this.submitIdentityData();
						}, error => {
              this.loaderService.hide();
						this.toastr.error(error['error_description'] ? error['error-message'] : MessageObj.unable_to_validate_is_server, 'Error');
						});
				 }
				}, error => {
          this.loaderService.hide();
					this.toastr.error(error['error_description'] ? error['error-message'] : MessageObj.unable_to_validate_is_server, 'Error');
			});
   }

   submitIdentityData() {
    const obj = this.identityServerForm.value;
    this.loaderService.show('Saving Identity Server Details');
    this.storesettingservice.submitIdentity('/auth/oauth-two-config/save', obj).subscribe((response: any) => {
            this.identityServerForm.setValue(response || {});
            this.loaderService.hide();
						this.toastr.success(MessageObj.is_server_detail_saved, 'Success');
            this.identityServerForm.disable();
            this.isEditable = false;
            this.commonService.setIdentityServer(true);
          }, (err) => {
            this.loaderService.hide();
          this.toastr.error(err['error-auxiliary-message'], 'Error');
    });
   }



}
