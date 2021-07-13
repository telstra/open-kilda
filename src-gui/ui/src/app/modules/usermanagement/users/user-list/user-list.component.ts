import { AfterViewInit, Component, OnInit, ViewChild, OnDestroy, Renderer2 } from '@angular/core';
import { ToastrService } from 'ngx-toastr';
import { UserService } from '../../../../common/services/user.service';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { TabService } from '../../../../common/services/tab.service';
import { LoaderService } from '../../../../common/services/loader.service';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalComponent } from "../../../../common/components/modal/modal.component";
import { ModalconfirmationComponent } from "../../../../common/components/modalconfirmation/modalconfirmation.component";
import { Title } from '@angular/platform-browser';
import { CommonService } from '../../../../common/services/common.service';
import { ResetPasswordComponent } from 'src/app/common/components/reset-password/reset-password.component';
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-user-list',
  templateUrl: './user-list.component.html',
  styleUrls: ['./user-list.component.css']
})

export class UserListComponent implements OnDestroy, OnInit, AfterViewInit{
  @ViewChild(DataTableDirective, { static: true })
  datatableElement: DataTableDirective;

  dtOptions : any = {};
  users: any;
  dtTrigger: Subject<any> = new Subject();
  changeStatus: any;
  loggedInUserId: any;
  hide = false;
  loadCount = 0;
  expandedEmail : boolean = false;
  expandedName : boolean = false;
  expandedRole : boolean = false;

  constructor(private userService:UserService, 
    private toastr: ToastrService, 
    private tabService: TabService,
    private loaderService : LoaderService,
    private modalService: NgbModal,
    private titleService: Title,
    private renderer: Renderer2,
    public commonService: CommonService
  ) {  }

  /*
    Method: ngOnInit
    Description: Execute On load
  */
  ngOnInit() {
    let ref = this;
    this.titleService.setTitle('OPEN KILDA - Users');
    
    this.users = [];
    this.dtOptions = {
      pageLength: 10,
      retrieve: true,
      autoWidth: true,
      colResize: false,
      dom: 'tpli',
      "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
      drawCallback:function(){
        if(jQuery('#users_table tbody tr').length < 10){
          jQuery('#users_table_next').addClass('disabled');
        }else{
          jQuery('#users_table_next').removeClass('disabled');
        }
      },
      "aoColumns": [{
          sWidth: '30%',
        },{
          sWidth: '20%',
        },{
          sWidth: '20%',"bSortable": false 
        },{
          sWidth: '30%', "bSortable": false 
        }
      ],
      language: {
        searchPlaceholder: "Search"
      },
      initComplete:function( settings, json ){
        setTimeout(function(){
          ref.loaderService.hide();
          ref.hide = true;
        },500);
      }
      
    };

    this.loggedInUserId = localStorage.getItem('user_id');
    this.getUsers();
  }

  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () {
        const that = this;
        $('input', this.header()).on('keyup change', function () {
          if (that.search() !== this['value']) {
            that.search(this['value']).draw();
          }
        });
      });

    });
  }

  rerender(): void {
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.destroy();
      this.dtTrigger.next();
    });
  }

  ngOnDestroy(): void {
    this.dtTrigger.unsubscribe();
  }

  /*
    Method: getUsers
    Description: Provide the list of users
  */
  getUsers(){
    this.loadCount++;
    this.hide = false;
    this.loaderService.show(MessageObj.loading_users);
    this.userService.getUsers().subscribe((data : Array<object>) =>{
     this.users = data;
     this.rerender();
     this.ngAfterViewInit();
    },error=>{
      
      if(error){
        if(error.status == 0){
          this.toastr.info(MessageObj.connection_refused,'Warning');
        }else if(error.error['error-message']){
          this.toastr.error(error.error['error-message'],'Error');
        }else{
          this.toastr.error(MessageObj.something_wrong,'Error');
        }
      }else{
        this.toastr.error(MessageObj.something_wrong,'Error');
      }
      this.rerender();
      this.ngAfterViewInit();
    });
  }

  /*
    Method: editUser
    Description: Edit a particular user by user id
  */

  editUser(id){
    this.tabService.setSelectedTab('user-edit');
    this.userService.selectedUser(id);
  }

  /*
    Method: deleteUser
    Description: Delete a particular user by user id
  */
  deleteUser(id){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to delete user?';
    
    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show(MessageObj.deleting_user);
        this.userService.deleteUser(id).subscribe(() => {
          this.toastr.success(MessageObj.user_deleted,'Success')
          this.getUsers();
        });
      }
    });
  }

  /*
    Method: activeInactiveUser
    Description: Active / InActive user status
  */
  activeInactiveUser(id, status){
    let statusText;

    if(status == 'Inactive'){
      this.changeStatus =  {"status": "active"}
      statusText = 'active';
    }else if(status == 'Active'){
      this.changeStatus =  {"status": "inactive"}
      statusText = 'Inactive';
    }

    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to '+statusText+' this user ?';

    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show(MessageObj.updating_user_status);
        this.userService.editUser(id, this.changeStatus).subscribe(user => {
          this.toastr.success(MessageObj.user_status_updated,'Success')
          this.getUsers();
         
        });
      }
    });
  }

  /*
    Method: resetpassword
    Description: Reset the user password and send an email with updated imformation.
  */
  resetpassword(id){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to reset password?';
    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show(MessageObj.resetting_password);
        this.userService.resetpasswordByUser(id).subscribe(user => {
          this.toastr.success(MessageObj.reset_pwd_mail_sent,'Success');
          this.loaderService.hide();
        },error => {
          this.toastr.error(error.error['error-message']);
          this.loaderService.hide();
        })
      }
    });
  }

  /*
    Method: resetpasswordByAdmin
    Description: Reset the user password by admin.
  */
  resetpasswordByAdmin(id){
    this.loaderService.show(MessageObj.resetting_pwd_by_admin);
    this.userService.resetpasswordByAdmin(id).subscribe(u => {
      this.loaderService.hide();
      this.toastr.success(MessageObj.pwd_reset,'Success');
      const modalRef = this.modalService.open(ResetPasswordComponent);
      modalRef.componentInstance.title = "User New Password";
      modalRef.componentInstance.content = u['password'];
    },error => {
      this.loaderService.hide();
      this.toastr.error(error.error['error-message']);

    })
  }

  /*
    Method: reset2fa
    Description: Reset the user 2FA authentication.
  */
  reset2fa(id){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to reset 2FA?';
    modalRef.result.then((response) => {
      if(response && response == true){
        this.loaderService.show(MessageObj.resetting_twofa);
        this.userService.reset2fa(id).subscribe(user => {
          this.toastr.success(MessageObj.twofa_reset,'Success');
          this.loaderService.hide();
        },error => {
          if(error.status == '500'){
            this.toastr.error(error.error['error-message']);
          }else{
            this.toastr.error(MessageObj.something_wrong);
          }

          this.loaderService.hide();
         
        })
      }
    });
  }

  /*
    Method: toggleSearch
    Description: Enable / disable of search text
  */
  toggleSearch(e,inputContainer){
    event.stopPropagation();
    this[inputContainer] = this[inputContainer] ? false : true;
    if (this[inputContainer]) {
      setTimeout(() => {
        this.renderer.selectRootElement("#" + inputContainer).focus();
      });
    }else{
      setTimeout(() => {
        this.renderer.selectRootElement('#'+inputContainer).value = "";
        jQuery('#'+inputContainer).trigger('change');
      });
    }
  }

  stopPropagationmethod(e){
    event.stopPropagation();

    if (e.key === "Enter") {
       return false;
    }
  }

}
