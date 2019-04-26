import { AfterViewInit, Component, OnInit, ViewChild, OnDestroy, Renderer2 } from '@angular/core';
import { RoleService } from '../../../../common/services/role.service';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { ToastrService } from 'ngx-toastr';
import { LoaderService } from '../../../../common/services/loader.service';
import { TabService } from '../../../../common/services/tab.service';
import { Title } from '@angular/platform-browser';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalconfirmationComponent } from "../../../../common/components/modalconfirmation/modalconfirmation.component";
import { CommonService } from '../../../../common/services/common.service';

@Component({
  selector: 'app-role-list',
  templateUrl: './role-list.component.html',
  styleUrls: ['./role-list.component.css']
})
export class RoleListComponent implements OnDestroy, OnInit, AfterViewInit{
  @ViewChild(DataTableDirective)

  datatableElement: DataTableDirective;
  dtOptions : any = {};
  roleData:any;
  dtTrigger: Subject<any> = new Subject();
  hide = false;
  loadCount = 0;
  expandedName : boolean = false;
  expandedPermission : boolean = false;

  constructor(private roleService:RoleService,
    private toastr: ToastrService,
    private tabService: TabService, 
    private loaderService : LoaderService,
    private titleService: Title,
    private modalService: NgbModal,
    private renderer : Renderer2,
    public commonService: CommonService
  ) {}

  /* Start: Datatable settings */
  ngAfterViewInit(): void {
    this.dtTrigger.next();
    this.datatableElement.dtInstance.then((dtInstance: DataTables.Api) => {
      dtInstance.columns().every(function () {
        const that = this;
        $('input', this.header()).on('keyup change', function () {
          if (that.search() !== this['value']) {
            that
              .search(this['value'])
              .draw();
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

   /* End: Datatable settings */

   /*
    Method: getRoles
    Description: Fetch all the roles records
  */
  getRoles(){
    this.loadCount++;
    this.loaderService.show("Loading Roles");
    this.roleService.getRoles().subscribe((role: Array<object>) => {
      this.roleData = role;
      this.rerender();
      this.ngAfterViewInit();
    },
    error => {
      this.loaderService.hide();
      if(error){
        if(error.status == 0){
          this.toastr.info("Connection Refused",'Warning');
        }else if(error.error['error-message']){
          this.toastr.error(error.error['error-message'],'Error');
        }else{
          this.toastr.error("Something went wrong",'Error');
        }
      }else{
        this.toastr.error("Something went wrong",'Error');
      }
    });
  }

  /*
    Method: editUser
    Description: Edit a particular user by user id
  */

  editRole(id){
    this.roleService.selectedRole(id);
    this.tabService.setSelectedTab('role-edit');
  }

  /*
    Method: deleteRole
    Description: Delete a particular role by role id
  */
  deleteRole(id){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to delete role?';
    
    modalRef.result.then((response) => {
      if(response && response == true){
        this.roleService.deleteRole(id).subscribe(() => {
          this.toastr.success("Role removed successfully!",'Success')
          this.getRoles();
        }, error =>{
          this.toastr.error(error.error['error-auxiliary-message']);
        });
      }
    });
  }

  ngOnInit() {
    let ref = this;
    this.titleService.setTitle('OPEN KILDA - Roles');
    this.loaderService.show();
    this.getRoles();
    this.dtOptions = { 
      pageLength: 10,
      retrieve: true,
      autoWidth: true,
      colResize: false,
      dom: 'tpli',
      "aoColumns": [{
          sWidth: '20%',
        },{
          sWidth: '50%',"bSortable": false,
        },{
          sWidth: '30%',"bSortable": false
        }
      ],
      language: {
        searchPlaceholder: "Search"
      },
      initComplete:function( settings, json ){
        let timerOut = ref.loadCount > 1 ? ref.roleData.length*2.7 : 1500;
        setTimeout(function(){
          ref.loaderService.hide();
          ref.hide = true;
        },timerOut);
      }
    };
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

  /*
    Method: OpenTab
    Description: Used to open tabs with component selector
  */
 
  OpenTab(tab, id){
    this.tabService.clearSelectedTab();
    if(id){
      this.roleService.selectedRole(id); //role
    }
    this.tabService.setSelectedTab(tab);
  }

  stopPropagationmethod(e){
    event.stopPropagation();

    if (e.key === "Enter") {
      return false;
    }
  }

}
