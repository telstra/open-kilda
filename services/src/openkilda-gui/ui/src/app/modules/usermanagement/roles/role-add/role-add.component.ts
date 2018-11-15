import { Component, OnInit, Input, Output, EventEmitter, HostBinding, AfterViewInit  } from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { Select2Data } from 'ng-select2-component/lib/select2-utils';
import { TabService } from '../../../../common/services/tab.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { RoleService } from '../../../../common/services/role.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-role-add',
  templateUrl: './role-add.component.html',
  styleUrls: ['./role-add.component.css']
})
export class RoleAddComponent implements OnInit, AfterViewInit {

  roleAddForm: FormGroup;
  openedTab: String;
  submitted = false;
  PermissionData: NgOption[];
  roleData: any;
  permissions: any;


  constructor(
    private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private permissionService: PermissionService, 
    private roleService: RoleService, 
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) {

    /* Get all permissions for Permission select field */
    this.PermissionData = [];
    this.permissions = [];
    this.permissionService.getPermissions().subscribe((permissions: Array<object>) => {
      permissions.map((permission:any) => this.permissions.push({ id: permission.permission_id, name: permission.name }));
      this.PermissionData = this.permissions;
    },
    error => {
      console.log("error", error);
    });
  }

  /* Create User add form */
  private createForm() {
    this.roleAddForm = this.formBuilder.group({
      name : ['',Validators.required],
      description : ['', Validators.required],
      permission: ['', Validators.required]
    });
  }
  /* Add role form  */
  addRole(){
    this.loaderService.show("Adding Role");
    this.submitted = true;
    if (this.roleAddForm.invalid) {
      return;
    }

    this.roleData = {
      'name': this.roleAddForm.value.name, 
      'description': this.roleAddForm.value.description,
      'permission_id': this.roleAddForm.value.permission
    };

    this.roleService.addRole(this.roleData).subscribe(role => {
      this.loaderService.hide();
      this.toastr.success("Role added successfully!",'Success! ');
      this.tabService.setSelectedTab('roles');
    },error =>{
      this.loaderService.hide();
      this.toastr.error(error.error['error-message'],'Error! ');
    });
  }

  close(): void {
    // send text to subscribers via observable subject
    this.tabService.setSelectedTab('roles');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Add Role');
    this.createForm();
  }

  ngAfterViewInit(){

  }

}
