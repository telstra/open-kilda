import { Component, OnInit } from '@angular/core';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { TabService } from '../../../../common/services/tab.service';
import { RoleService } from '../../../../common/services/role.service';
import { NgxSpinnerService } from 'ngx-spinner';
import { Title } from '@angular/platform-browser';

@Component({
  selector: 'app-role-view',
  templateUrl: './role-view.component.html',
  styleUrls: ['./role-view.component.css']
})
export class RoleViewComponent implements OnInit {

  roleViewForm: FormGroup;
  users: any;

  constructor(
    private tabService: TabService,
    private roleService: RoleService,
    private loaderService : NgxSpinnerService,
    private titleService: Title
  ) {

    /* Get all roles for Role select field */
    this.roleService.currentRole.subscribe(roleId => {
      if(roleId){
        this.roleService.getUserRoleById(roleId).subscribe(role => {
          this.users = role.users;
          this.roleViewForm.patchValue({
            name: role.name, 
            description: role.description
          });
        });
      }
    });
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createEditForm() {
    this.roleViewForm = new FormGroup({
      name: new FormControl({value: '', disabled: true}, Validators.required),
      description: new FormControl({value: '', disabled: true}, Validators.required)
    });
  }

  close(): void {
    this.tabService.setSelectedTab('roles');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - View User');
    this.createEditForm();
  }

}