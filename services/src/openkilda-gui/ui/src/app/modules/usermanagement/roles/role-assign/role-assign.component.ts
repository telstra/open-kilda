import { Component, OnInit, AfterViewInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { TabService } from '../../../../common/services/tab.service';
import { UserService } from '../../../../common/services/user.service';
import { RoleService } from '../../../../common/services/role.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-role-assign',
  templateUrl: './role-assign.component.html',
  styleUrls: ['./role-assign.component.css']
})
export class RoleAssignComponent implements OnInit, AfterViewInit {

  roleAssignForm: FormGroup;
  openedTab: String;
  subscription: Subscription;
  userEmail: string;
  UserData: NgOption[];
  userid:number;
  submitted: boolean;
  roleAssignData: any;
  selectedRole: number;
  users: any;
  roleUsers:any;
  selectedRoleUsers: any;
  loggedInUserId: any;

  constructor(
    private tabService: TabService, 
    private userService: UserService,
    private roleService: RoleService,
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) { 
    /* Get all permissions for Permission select field */
    this.UserData = [];
    this.users = [];
    this.selectedRoleUsers = [];
    this.loggedInUserId =  localStorage.getItem('user_id');
    this.userService.getUsers().subscribe((users: Array<object>) => {
      users.map((user:any) => {
        if(user.user_id != this.loggedInUserId){
          this.users.push({ id: user.user_id, name: user.name })
        }
      });
      this.UserData = this.users;
      this.roleService.currentRole.subscribe(roleId => {
        if(roleId){
          this.selectedRole = roleId;
          this.roleService.getRoleById(roleId).subscribe(role => {
            users.map((user:any)=>{
              if((user.roles.filter(r => r == role.name)).length > 0){
                let userExist = this.UserData.find(usr=>usr.id==user.user_id);
                if(userExist){
                  this.selectedRoleUsers.push(user.user_id);
                }
              }
            });
            this.roleAssignForm.patchValue({
              name: role.name, 
              description: role.description,
              user: this.selectedRoleUsers
            });
          });
        }
      });
    },
    error => {
      console.log("error", error);
    });
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createAssignForm() {
    this.roleAssignForm = new FormGroup({
      name: new FormControl({value: '', disabled: true}, Validators.required),
      description: new FormControl({value: '', disabled: true}, Validators.required),
      user: new FormControl() 
    });
  }

  /* 
    Method: submitform
    Description: Trigger when click on "Assign Role" Button
  */
  submitform(){
    this.loaderService.show("Assigning User");
    this.submitted = true;
    if (this.roleAssignForm.invalid) {
      return;
    }

    let Assignedusers = this.roleAssignForm.value.user;
    let users = [];

    Assignedusers.map(user => {
      users.push({"user_id":user});
    });

    this.roleAssignData = {"users": users};
    this.roleService.assignRole(this.selectedRole, this.roleAssignData).subscribe(role => {
      this.loaderService.hide();
      this.toastr.success("Role updated successfully!",'Success! ');
      this.tabService.setSelectedTab('roles');
    },error =>{
      this.loaderService.hide();
      this.toastr.error(error.error['error-message'],'Error! ');
    });
  }

  close(): void {
    this.tabService.setSelectedTab('roles');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Assign Users');
    this.submitted = false;
    this.createAssignForm();
  }

  ngAfterViewInit(){
  }

}
