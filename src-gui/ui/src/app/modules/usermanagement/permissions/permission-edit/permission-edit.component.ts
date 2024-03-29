import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { TabService } from '../../../../common/services/tab.service';
import { PermissionService } from '../../../../common/services/permission.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';
import { MessageObj } from 'src/app/common/constants/constants';
import {Select2Data} from 'ng-select2-component';

@Component({
  selector: 'app-permission-edit',
  templateUrl: './permission-edit.component.html',
  styleUrls: ['./permission-edit.component.css']
})
export class PermissionEditComponent implements OnInit {

  permissionEditForm: FormGroup;
  openedTab: String;
  roleData: Select2Data;
  subscription: Subscription;
  userEmail: string;
  userid: number;
  submitted: boolean;
  permissionData: any;
  selectedPermission: number;

  constructor(
    private tabService: TabService,
    private permissionService: PermissionService,
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) { }

  getPermissionById() {
    this.permissionService.currentPermission.subscribe(permissionId => {
      if (permissionId) {
        this.selectedPermission = permissionId;
        this.permissionService.getPermissionById(permissionId).subscribe(permission => {
          this.permissionEditForm.patchValue({
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
    this.permissionEditForm = new FormGroup({
      name: new FormControl({value: '', disabled: false}, Validators.required),
      description: new FormControl({value: '', disabled: false})
    });
  }


  /*
    Method: updatePermission
    Description: updated permission data
  */
  updatePermission() {
    this.loaderService.show(MessageObj.updating_permission);
    this.submitted = true;
    if (this.permissionEditForm.invalid) {
      return;
    }

    this.permissionData = {
      'name': this.permissionEditForm.value.name,
      'description': this.permissionEditForm.value.description,
    };

    this.permissionService.editPermission(this.selectedPermission , this.permissionData).subscribe(permission => {
      this.loaderService.hide();
      this.toastr.success(MessageObj.permission_updated, 'Success! ');
      this.tabService.setSelectedTab('permissions');
    }, error => {
      this.loaderService.hide();
      this.toastr.error(error.error['error-message'], 'Error! ');
    });
  }

  close(): void {
    this.tabService.setSelectedTab('permissions');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Edit Permission');
    this.getPermissionById();
    this.createAssignForm();
  }

}
