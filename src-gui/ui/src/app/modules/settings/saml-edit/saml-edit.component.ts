import { Component, OnInit, Input ,Output,EventEmitter,OnChanges,SimpleChanges,ViewChild,ElementRef} from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { RoleService } from '../../../common/services/role.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../common/services/loader.service';
import { MessageObj } from 'src/app/common/constants/constants';
import { SamlSettingService } from 'src/app/common/services/saml-setting.service';

@Component({
  selector: 'app-saml-edit',
  templateUrl: './saml-edit.component.html',
  styleUrls: ['./saml-edit.component.css']
})
export class SamlEditComponent implements OnInit,OnChanges {

  @Input() data:any;
  @Output() cancel =  new EventEmitter();
  
  @ViewChild("file")
  fileElementRef: ElementRef;
  uuid:any=null;
  files:FileList;
  samlEditForm: FormGroup;
  fileUploaded:any='';
  submitted = false;
  roleData: NgOption[];
  providerData:any;
  metadata_required:any=false;
  roles:any;  
  userCreation:any=false;
  settingStatus:any=false;
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

  setUserCreation(e){
    this.samlEditForm.controls['user_creation'].setValue(e.target.checked);
    this.userCreation = e.target.checked;
  }
  setsettingStatus(e){
    this.samlEditForm.controls['status'].setValue(e.target.checked);
    this.settingStatus = e.target.checked;
  }
  /* update provider add form */
  private updateForm() {
    this.samlEditForm = this.formBuilder.group({
      name : ['',Validators.required],
      entity_id : ['', Validators.required],
      url:new FormControl(null),
      file:new FormControl(null),
      roles: [''],
      status: [true],
      user_creation:[false],
      attribute:['',Validators.required]
    });
   
  }
  emptyFile(){
    this.submitted = false;
    this.fileUploaded = '';
    this.samlEditForm.controls['file'].setValue(null);
    this.enableField('url');
  }
  disableFields(field){
    this.samlEditForm.controls[field].disable();
  }
  enableField(field){
    this.samlEditForm.controls[field].enable();
  }

  getFile(e){
    this.files = e.target.files;
    if(this.files && this.files.length){
        let reader = new FileReader();
        let self = this;
        reader.onloadend = function(x){
          self.disableFields('url');
          self.fileUploaded = self.files[0].name;
        }
        reader.readAsText(this.files[0]); 
    }    
  }

  /* Add provider form submit function */
  updateProvider(){
    this.submitted = true;
    if (this.samlEditForm.invalid) {
      if(!this.samlEditForm.value.file && !this.samlEditForm.value.url  && !this.providerData.metadata){
        this.metadata_required = true;
      }
      return;
    }

    if(!this.samlEditForm.value.file && !this.samlEditForm.value.url  && !this.providerData.metadata){
      this.metadata_required = true;
      return;
    }
    if(this.userCreation && !this.samlEditForm.value.roles){
      return;
    }
    this.metadata_required = false
    const formData = new FormData();
    formData.append('name',this.samlEditForm.value.name);
    formData.append('entity_id',this.samlEditForm.value.entity_id);
    formData.append('status',this.samlEditForm.value.status);
    formData.append('user_creation',this.samlEditForm.value.user_creation);
    formData.append('role_ids',this.samlEditForm.value.roles);    
    formData.append('attribute',this.samlEditForm.value.attribute);
    if(this.samlEditForm.value.url){
      formData.append('url',this.samlEditForm.value.url);
    }
    if(this.samlEditForm.value.file){
      formData.append('file',this.files[0]);
    }
    this.loaderService.show(MessageObj.updating_provider);
    this.samlSettingService.updateAuthProvider(formData,this.uuid).subscribe((res:any)=>{
      this.toastr.success(MessageObj.provider_updated_success);
      this.loaderService.hide();
      this.cancel.emit();
    },(error)=>{
      this.loaderService.hide();
      var msg = (error && error.error && error.error['error-message']) ? error.error['error-message'] : 'Error in saving';
      this.toastr.error(msg,'Error');
    });

    
  }

  ngOnChanges(change:SimpleChanges){
    var ref = this;
    if( typeof(change.data)!='undefined' && change.data){
      if(typeof(change.data)!=='undefined' && change.data.currentValue){
        this.data  = change.data.currentValue;
        this.uuid = this.data.uuid;
      }
    }
  }

  getDetail(id){
    this.loaderService.show(MessageObj.loading_data);
    this.samlSettingService.getDetail(id).subscribe((res)=>{
      this.providerData = res;
      if(res.type =='FILE'){
        this.fileUploaded = 'file.xml';
        this.disableFields('url');
      } 
      this.samlEditForm.controls['name'].setValue(res.name);
      this.samlEditForm.controls['entity_id'].setValue(res.entity_id);
      this.samlEditForm.controls['roles'].setValue(res.roles);
      this.samlEditForm.controls['status'].setValue(res.status);
      this.samlEditForm.controls['user_creation'].setValue(res.user_creation);
      this.samlEditForm.controls['attribute'].setValue(res.attribute);
      this.samlEditForm.controls['url'].setValue(res.type =='URL' ? res.url:'');
      this.loaderService.hide();
      
    },(error)=>{
      this.loaderService.hide();
    })
  }

  close(): void {
    this.cancel.emit();
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Update Provider');
    this.updateForm();
    this.getDetail(this.uuid);
  }
}
