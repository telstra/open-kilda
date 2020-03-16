import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { TabService } from '../../../../common/services/tab.service';
import { RoleService } from '../../../../common/services/role.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-permission-add',
  templateUrl: './permission-add.component.html',
  styleUrls: ['./permission-add.component.css']
})
export class PermissionAddComponent implements OnInit {

  permissionAddForm: FormGroup;
  openedTab: String;
  submitted = false;
  permissionData: any;

  constructor(
    private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private permissionService: PermissionService, 
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) { }

   /* Create User add form */
   private createForm() {
    this.permissionAddForm = this.formBuilder.group({
      name : ['',Validators.required],
      description : []
    });
  }

  /* 
    Method: addPermission
    Description: Add new permission
  */
  addPermission(){
    this.loaderService.show("Adding Permission");
    this.submitted = true;
    if (this.permissionAddForm.invalid) {
      return;
    }

    this.permissionData = {
      'name': this.permissionAddForm.value.name, 
      'description': this.permissionAddForm.value.description,
    };

    this.permissionService.addPermission(this.permissionData).subscribe(permission => {
      this.loaderService.hide();
      this.toastr.success("Permission added successfully!",'Success! ');
      this.tabService.setSelectedTab('permissions');
    },error =>{
      this.loaderService.hide();
      this.toastr.error(error.error['error-message'],'Error! ');
    });
  }

  close(): void {
    // send text to subscribers via observable subject
    this.tabService.setSelectedTab('Add Permission');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Permissions');
    this.createForm();
  }

}
