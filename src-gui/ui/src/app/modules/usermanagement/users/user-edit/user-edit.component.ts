import { Component, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { TabService } from '../../../../common/services/tab.service';
import { ToastrService } from 'ngx-toastr';
import { RoleService } from '../../../../common/services/role.service';
import { UserService } from '../../../../common/services/user.service';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-user-edit',
  templateUrl: './user-edit.component.html',
  styleUrls: ['./user-edit.component.css']
})
export class UserEditComponent implements OnInit {

  userEditForm: FormGroup;
  openedTab: String;
  subscription: Subscription;
  userEmail: string;
  userid:number;
  value:number;
  submitted: boolean;
  selectedUser: number;
  userUpdatedData: any;
  roleData: NgOption[];
  roles: any;
  r:any;
  selectedRoles: any;

  constructor(private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private roleService: RoleService, 
    private userService: UserService,
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
  ) {
    
    this.roles = [];
    this.selectedRoles = [];
    this.loaderService.show("Loading Details");
    this.roleService.getRoles().subscribe((role: Array<object>) => {
      role.map((role:any) => {
        this.roles.push({ id: role.role_id, name: role.name })
      });
      this.roleData = this.roles;
      this.userService.currentUser.subscribe(userId => {
        if(userId){
          this.selectedUser = userId;
          this.userService.getUserById(userId).subscribe(user => {
            this.r = user.roles;
            this.r.map((u) => {
              role.map((role:any) => {
                if(role.name == u){
                  this.selectedRoles.push(role.role_id);
                }
              });
            });
            this.userEditForm.patchValue({
                    email: user.email, 
                    name: user.name,
                    roles: this.selectedRoles,
                    is2FaEnabled: user.is2FaEnabled
            },{emitEvent: false});
            setTimeout(()=>{
              this.loaderService.hide();
            },2000)
           
          });
        }
      });
    },error => {
      
    });  
  
  }

  /* 
    Method: createEditForm
    Description: Create User edit form
  */
  private createEditForm() {
    this.userEditForm = this.formBuilder.group({
      email : [{value:'', disabled: true},Validators.required],
      name : ['', Validators.required],
      roles: ['', Validators.required],
      is2FaEnabled: []
    });
  }

  /**
   * Method: submitform
   * Description: Update user information
   */
  submitform(){
    this.submitted = true;
    if (this.userEditForm.invalid) {
      return;
    }
    
    this.loaderService.show("Updating User");

    this.userUpdatedData = {
      name: this.userEditForm.value.name,
      role_id: this.userEditForm.value.roles,
      is2FaEnabled: this.userEditForm.value.is2FaEnabled
    }

    this.userService.editUser(this.selectedUser, this.userUpdatedData).subscribe(user => {
      this.toastr.success("User data updated successfully!",'Success! ');
      this.tabService.setSelectedTab('users');
      this.loaderService.hide();
    },error =>{
      this.toastr.error(error.error['error-message'],'Error! ');
      this.loaderService.hide();
    });
  }

  close(): void {
    this.tabService.clearSelectedTab();
    // send text to subscribers via observable subject
    this.tabService.setSelectedTab('users');
    this.userService.clearSelectedUser();
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Edit User');
    this.createEditForm();
  }

}
