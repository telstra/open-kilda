import { Component, OnInit,Output,EventEmitter } from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { RoleService } from '../../../common/services/role.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../common/services/loader.service';
import { MessageObj } from 'src/app/common/constants/constants';
import { SamlSettingService } from 'src/app/common/services/saml-setting.service';

@Component({
  selector: 'app-saml-add',
  templateUrl: './saml-add.component.html',
  styleUrls: ['./saml-add.component.css']
})
export class SamlAddComponent implements OnInit {
  @Output() cancel =  new EventEmitter();
  files:FileList;
  samlAddForm: FormGroup;
  submitted = false;
  roleData: NgOption[];
  providerData:any;
  roles:any;
  metadata_required:any=false;
  userCreation:any=false;
  settingStatus:any=true;
  constructor(
    private formBuilder:FormBuilder, 
    private roleService: RoleService, 
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService,
    private samlSettingService:SamlSettingService
  ) { 

     /* Get all roles for Select2 */
     this.roles = [];
     this.roleData = [];
     this.roleService.getRoles().subscribe((role: Array<object>) => {
       role.map((role:any) => this.roles.push({ id: role.role_id, name: role.name }));
       this.roleData = this.roles;
     },
     error => {
      console.log("error", error);
     });
  }

  /* Create provider add form */
  private createForm() {
    this.samlAddForm = this.formBuilder.group({
      name : ['',Validators.required],
      entity_id : ['', Validators.required],
      url:[''],
      file:[''],
      roles: [''],
      status: [true],
      user_creation:[false],
      attribute:['',Validators.required]
    });
  }

  getFile(e){
    this.files = e.target.files;
    if(this.files && this.files.length){
        let reader = new FileReader();
        let self = this;
        reader.onloadend = function(x){
          self.disableFields('url');
        }
        reader.readAsText(this.files[0]); 
    }
    
  }

  setUserCreation(e){
    this.samlAddForm.controls['user_creation'].setValue(e.target.checked);
    this.userCreation = e.target.checked;
  }
  setsettingStatus(e){
    this.samlAddForm.controls['status'].setValue(e.target.checked);
    this.settingStatus = e.target.checked;
  }

  /* Add provider form submit function */
  addProvider(){
    this.submitted = true;
    if (this.samlAddForm.invalid) {
      if(!this.samlAddForm.value.file && !this.samlAddForm.value.url){
        this.metadata_required = true;
      }
      return;
    }

    if(!this.samlAddForm.value.file && !this.samlAddForm.value.url){
      this.metadata_required = true;
      return;
    }
    if(this.userCreation && !this.samlAddForm.value.roles){
      return;
    }
    this.metadata_required = false
    const formData = new FormData();
    formData.append('name',this.samlAddForm.value.name);
    formData.append('entity_id',this.samlAddForm.value.entity_id);
    formData.append('status',this.samlAddForm.value.status);
    formData.append('user_creation',this.samlAddForm.value.user_creation);
    formData.append('role_ids',this.samlAddForm.value.roles);
    formData.append('attribute',this.samlAddForm.value.attribute);
    
    if(this.samlAddForm.value.url){
      formData.append('url',this.samlAddForm.value.url);
    }
    if(this.samlAddForm.value.file){
      formData.append('file',this.files[0]);
    }
    this.loaderService.show(MessageObj.adding_provider);
    this.samlSettingService.saveAuthProvider(formData).subscribe((res:any)=>{
      this.toastr.success(MessageObj.provider_added_success);
        this.loaderService.hide();
        this.cancel.emit();      
    },(error)=>{
      this.loaderService.hide();
      var msg = (error && error.error && error.error['error-message']) ? error.error['error-message'] : 'Error in saving';
      this.toastr.error(msg,'Error');
    });

    
  }
  emptyFile(){
    this.submitted = false;
    this.samlAddForm.controls['file'].setValue(null);
    this.enableField('url');
  }
  disableFields(field){
    this.samlAddForm.controls[field].disable();
  }
  enableField(field){
    this.samlAddForm.controls[field].enable();
  }

  close(): void {
    this.cancel.emit();
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Add Provider');
    this.createForm();
  }

}
