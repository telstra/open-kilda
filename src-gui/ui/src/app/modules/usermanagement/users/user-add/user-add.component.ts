import { Component, OnInit, Input, Output, EventEmitter, HostBinding } from '@angular/core';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { NgOption } from '@ng-select/ng-select';
import { TabService } from '../../../../common/services/tab.service';
import { RoleService } from '../../../../common/services/role.service';
import { UserService } from '../../../../common/services/user.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { LoaderService } from '../../../../common/services/loader.service';

@Component({
  selector: 'app-user-add',
  templateUrl: './user-add.component.html',
  styleUrls: ['./user-add.component.css']
})
export class UserAddComponent implements OnInit {

  userAddForm: FormGroup;
  openedTab: String;
  submitted = false;
  roleData: NgOption[];
  userData: any;
  roles:any;

  constructor(
    private formBuilder:FormBuilder, 
    private tabService: TabService, 
    private roleService: RoleService, 
    private userService: UserService, 
    private toastr: ToastrService,
    private titleService: Title,
    private loaderService: LoaderService
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

  /* Create User add form */
  private createForm() {
    this.userAddForm = this.formBuilder.group({
      email : ['',[Validators.required, Validators.email]],
      name : ['', Validators.required],
      roles: ['', Validators.required],
      is2FaEnabled: [true]
    });
  }

  /* Add user form submit function */
  addUser(){
    this.submitted = true;
    if (this.userAddForm.invalid) {
      return;
    }

    this.loaderService.show("Adding User");

    this.userData = {
      'name': this.userAddForm.value.name, 
      'user_name': this.userAddForm.value.email, 
      'email': this.userAddForm.value.email, 
      'role_id': this.userAddForm.value.roles,
      'is2FaEnabled': this.userAddForm.value.is2FaEnabled
    };

    this.userService.addUser(this.userData).subscribe(user => {
      this.loaderService.hide();
      this.toastr.success("User added successfully!",'Success! ');
      this.tabService.setSelectedTab('users');
    },error =>{
      this.loaderService.hide();
      this.toastr.error(error.error['error-message'],'Error! ');
    });
  }

  close(): void {
    // send text to subscribers via observable subject
    this.tabService.setSelectedTab('users');
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Add User');
    this.createForm();
  }

}
