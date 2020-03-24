import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { Select2Data } from 'ng-select2-component/lib/select2-utils';
import { TabService } from '../../../../common/services/tab.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-permission-view',
  templateUrl: './permission-view.component.html',
  styleUrls: ['./permission-view.component.css']
})
export class PermissionViewComponent implements OnInit {

  permissionViewForm: FormGroup;
  openedTab: String;
  roleData: Select2Data;
  subscription: Subscription;
  userEmail: string;
  userid:number;
  submitted: boolean;
  roleAssignData: any;
  roles:any;

  constructor(
    private tabService: TabService, 
    private permissionService: PermissionService,
    private titleService: Title
  ) { }

  /* 
    Method: getPermissionById
    Description: Get Particular permission by permission id
  */
  getPermissionById(){
    this.permissionService.currentPermission.subscribe(permissionId => {
      if(permissionId){
        this.permissionService.getPermissionById(permissionId).subscribe(permission => {
          this.roles = permission.roles;
          this.permissionViewForm.patchValue({
            name: permission.name, 
            description: permission.description
          });
        });
      }
    });
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createAssignForm() {
    this.permissionViewForm = new FormGroup({
      name: new FormControl({value: '', disabled: true}, Validators.required),
      description: new FormControl({value: '', disabled: true}, Validators.required)
    });
  }

  close(): void {
    this.tabService.setSelectedTab('permissions');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View Roles');
    this.getPermissionById();
    this.createAssignForm();
  }

}
