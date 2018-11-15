import { Component, OnInit } from '@angular/core';
import { NgbActiveModal, NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { UserService } from '../../services/user.service';
import { ToastrService } from 'ngx-toastr';


@Component({
  selector: 'app-changepassword',
  templateUrl: './changepassword.component.html',
  styleUrls: ['./changepassword.component.css']
})
export class ChangepasswordComponent implements OnInit {

  changePasswordForm: FormGroup;
  changePasswordFormGroup: FormGroup;
  submitted: boolean;
  formData: any;
  userId: any;
  is2FaEnabled:any;

  constructor(
    public activeModal: NgbActiveModal,
    private formBuilder:FormBuilder, 
    private userService: UserService,
    private toastr: ToastrService,
    private modalService: NgbModal
  ) {

    // Get userId from session
    this.userId = localStorage.getItem('user_id');;
    this.is2FaEnabled = localStorage.getItem('is2FaEnabled');
   }

   /**
  * Method: createForm
  * Description: Create ReActive Form for change Password
  */
  private createForm() {

    this.changePasswordForm = new FormGroup({
      oldPassword : new FormControl(null,[Validators.required]),
      otp : new FormControl(null)
    });

    this.changePasswordFormGroup = new FormGroup({
      newPassword : new FormControl('', [Validators.required, Validators.minLength(8), Validators.maxLength(15)]),
      confirmPassword: new FormControl('',[Validators.required])
    }, this.validateAreEqual);

    if(this.is2FaEnabled == 'true'){
      this.changePasswordForm.setValidators([Validators.required]);
    }
  }

  private validateAreEqual(form: FormGroup) {

    let password = form.get('newPassword').value;
    let confirmPassword = form.get('confirmPassword').value;

    if (confirmPassword.length <= 0) {
      return null;
    }

    if (confirmPassword !== password) {
        return {
            doesMatchPassword: true
        };
    }

    return null;
    
  }

  /**
   * Method: submitForm
   * Description: trigger on change password submit form
  */
  submitForm(){
    this.submitted = true;
    if (this.changePasswordForm.invalid && this.changePasswordFormGroup.invalid) {
      return;
    }

    this.formData = {
      'password': this.changePasswordForm.value.oldPassword, 
      'new_password': this.changePasswordFormGroup.value.newPassword
    };

    if(this.is2FaEnabled == 'true'){
      this.formData.code = this.changePasswordForm.value.otp
    }

    this.userService.changePassword(this.userId, this.formData).subscribe(user => {
      this.toastr.success("Password changed successfully!",'Success! ');
      this.modalService.dismissAll();
    },error =>{
      this.toastr.error(error.error['error-message'],'Error! ');
    });
  }

  ngOnInit() {
    this.createForm();
  }

}
