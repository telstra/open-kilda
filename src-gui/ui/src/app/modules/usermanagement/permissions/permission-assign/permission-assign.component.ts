import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { TabService } from '../../../../common/services/tab.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { ToastrService } from 'ngx-toastr';
import { RoleService } from '../../../../common/services/role.service';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-permission-assign',
  templateUrl: './permission-assign.component.html',
  styleUrls: ['./permission-assign.component.css']
})
export class PermissionAssignComponent implements OnInit {

  permissionAssignForm: FormGroup;
  openedTab: String;
  roleData: NgOption;
  subscription: Subscription;
  userEmail: string;
  selectedPermission:number;
  submitted: boolean;
  roleAssignData: any;
  roles: any;
  selectedPermissionRoles: any;
  

  constructor(
    private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private permissionService: PermissionService,
    private roleService: RoleService, 
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) { 

    this.roles = [];
    this.roleData = [];
    this.selectedPermissionRoles = [];
    this.roleService.getRoles().subscribe((role: Array<object>) => {
      role.map((role:any) => {
        this.roles.push({ id: role.role_id, name: role.name })
      });
      this.roleData = this.roles;
      this.permissionService.currentPermission.subscribe(permissionId => {
        if(permissionId){
          this.selectedPermission = permissionId;
          this.permissionService.getPermissionById(permissionId).subscribe(permission => {
            role.map((role:any)=>{
              if((role.permissions.filter(rp => rp.name == permission.name)).length > 0){
                this.selectedPermissionRoles.push(role.role_id);
              }
            });
            this.permissionAssignForm.patchValue({
              name: permission.name, 
              description: permission.description,
              role: this.selectedPermissionRoles
            });
          });
        }
      });
    },error => {
      console.log("error", error);
    });  
 
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createAssignForm() {
    this.permissionAssignForm = new FormGroup({
      name: new FormControl({value: '', disabled: true}, Validators.required),
      description: new FormControl({value: '', disabled: true}, Validators.required),
      role: new FormControl() 
    });
  }

  /* 
    Method: submitform
    Description: Trigger when click on "Assign Role" Button
  */
  submitform(){
    this.loaderService.show("Assigning Roles");
    this.submitted = true;
    if (this.permissionAssignForm.invalid) {
      return;
    }

    let assignedRoles = this.permissionAssignForm.value.role;
    let roles = [];

    assignedRoles.map(role => {
      roles.push({"role_id":role});
    });

    this.roleAssignData = {"roles": roles};
    this.permissionService.assignPermission(this.selectedPermission, this.roleAssignData).subscribe(role => {
      this.loaderService.hide();
      this.toastr.success("Role assigned successfully!",'Success! ');
      this.tabService.setSelectedTab('permissions');
    },error =>{
      this.loaderService.hide();
      this.toastr.error(error.error['error-auxiliary-message'],'Error! ');
    });
  }

  close(): void {
    this.tabService.setSelectedTab('permissions');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Assign Roles');
    this.createAssignForm();
  }

}
