import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { TabService } from '../../../../common/services/tab.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { RoleService } from '../../../../common/services/role.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-role-edit',
  templateUrl: './role-edit.component.html',
  styleUrls: ['./role-edit.component.css']
})
export class RoleEditComponent implements OnInit, AfterViewInit {

  roleEditForm: FormGroup;
  openedTab: String;
  subscription: Subscription;
  userEmail: string;
  PermissionData: NgOption[];
  userid:number;
  roleUpdatedData: any;
  selectedRole: number;
  permissions: any;
  rolePermissions:any;
  selectedRolePermissions: any;

  constructor(private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private permissionService: PermissionService,
    private roleService: RoleService,
    private loaderService : LoaderService,
    private toastr : ToastrService,
    private titleService: Title
  ) {
    
  }

  getRoleData(){
    /* Get all permissions for Permission select field */
    this.loaderService.show("Getting Role");
    this.PermissionData = [];
    this.permissions = [];
    this.selectedRolePermissions=[];
    this.permissionService.getPermissions().subscribe((permissions: Array<object>) => {
      this.PermissionData.push(permissions.map((permission:any) => this.permissions.push({ id: permission.permission_id, name: permission.name })));
      this.PermissionData = this.permissions;
      this.roleService.currentRole.subscribe(roleId => {
        if(roleId){
          this.selectedRole = roleId;
          this.roleService.getRoleById(roleId).subscribe(role => {
            this.rolePermissions = role.permissions;
            this.rolePermissions.map((rp) => {
              let permisionExist = this.PermissionData.find(per=>per.id==rp.permission_id);
              if(permisionExist){
                this.selectedRolePermissions.push(rp.permission_id);
              }
            });
            this.roleEditForm.patchValue({
              name: role.name, 
              description: role.description,
              permission: this.selectedRolePermissions
            });
          });
        }
        setTimeout(()=>{
          this.loaderService.hide();
        },2000)
      });
    },
    error => {
      this.loaderService.hide();
    });
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createEditForm() {
    this.roleEditForm = new FormGroup({
      name: new FormControl({value: ''}, Validators.required),
      description: new FormControl({value: ''}, Validators.required),
      permission: new FormControl({value: ''}) 
    });
  }

  /**
   * Method: submitform
   * Description: Update user information
   */
  submitform(){
    this.loaderService.show("Updating Role");
    if (this.roleEditForm.invalid) {
      return;
    }

    this.roleUpdatedData = {
      name: this.roleEditForm.value.name,
      description: this.roleEditForm.value.description,
      permission_id: this.roleEditForm.value.permission
    }

    this.roleService.editRole(this.selectedRole, this.roleUpdatedData).subscribe(role => {
      this.toastr.success("Role updated successfully!",'Success! ');
      this.loaderService.hide();
      this.tabService.setSelectedTab('roles');
    },error =>{
      this.toastr.error(error.error['error-message'],'Error! ');
      this.loaderService.hide();
    });
  }

  close(): void {
    this.tabService.setSelectedTab('roles');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Edit Role');
    this.createEditForm();
    this.getRoleData();
  }

  ngAfterViewInit(){

  }

}
