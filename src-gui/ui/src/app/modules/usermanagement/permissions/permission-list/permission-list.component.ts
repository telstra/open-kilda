import { AfterViewInit, Component, OnInit, ViewChild, OnDestroy, Renderer2 } from '@angular/core';
import { PermissionService } from '../../../../common/services/permission.service';
import { Subject } from 'rxjs';
import { DataTableDirective } from 'angular-datatables';
import { ToastrService } from '../../../../../../node_modules/ngx-toastr';
import { LoaderService } from '../../../../common/services/loader.service';
import { TabService } from '../../../../common/services/tab.service';
import { Title } from '@angular/platform-browser';
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ModalconfirmationComponent } from "../../../../common/components/modalconfirmation/modalconfirmation.component";
import { CommonService } from '../../../../common/services/common.service';

@Component({
  selector: 'app-permission-list',
  templateUrl: './permission-list.component.html',
  styleUrls: ['./permission-list.component.css']
})
export class PermissionListComponent implements OnDestroy, OnInit, AfterViewInit {
  @ViewChild(DataTableDirective)

  datatableElement: DataTableDirective;
  dtOptions : any = {};
  allPermissions:any;
  dtTrigger: Subject<any> = new Subject();
  changeStatus: any;
  hide = false;
  loadCount = 0;
  expandedName : boolean = false;
  expandedDescription : boolean = false;

  constructor(private permissionService:PermissionService,
    private toastr: ToastrService,
    private loaderService : LoaderService,
    private tabService: TabService,
    private titleService: Title,
    private modalService: NgbModal,
    private renderer: Renderer2,
    public commonService: CommonService
  ) { }

  getPermissions(){
    this.loadCount++;
    this.hide = false;
    this.loaderService.show("Loading Permissions");
    this.permissionService.getPermissions().subscribe((permission: Array<object>) => {
      this.allPermissions = permission;
      this.rerender();
      this.ngAfterViewInit();
    },
    error => {
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
      this.rerender();
    });
  }

   /*
    Method: activeInactiveUser
    Description: Active / InActive user status
  */
  activeInactivePermission(id, status){
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
    modalRef.componentInstance.content = 'Are you sure you want to ' + statusText + ' this Permission ?';
    
    modalRef.result.then((response) => {
      if(response && response == true){
        this.permissionService.editPermission(id, this.changeStatus).subscribe(permission => {
          this.toastr.success("Permission status changed successfully!",'Success');
          this.getPermissions();
        }, error => {
          this.toastr.error(error.error['error-message']);
        })
      }
    });
  }


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

  ngOnInit() {
    let ref = this;
    this.titleService.setTitle('OPEN KILDA - Permissions');
    this.loaderService.show("Loading Permissions");
    this.dtOptions = { 
      pageLength: 10,
      retrieve: true,
      autoWidth: true,
      colResize: false,
      dom: 'tpli',
      "aoColumns": [{
          sWidth: '20%',
        },{
          sWidth: '50%',
        },{
          sWidth: '30%',"bSortable": false
        }
      ],
      language: {
        searchPlaceholder: "Search"
      },
      initComplete:function( settings, json ){
        let timerOut = ref.loadCount > 1 ? ref.allPermissions.length*2.7 : 1500;
        setTimeout(function(){
          ref.loaderService.hide();
          ref.hide = true;
        },1500);
      }
    };
    this.getPermissions();
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
    Method: deletePemission
    Description: Delete particular permission with selected permission id
  */
  deletePemission(id){
    const modalRef = this.modalService.open(ModalconfirmationComponent);
    modalRef.componentInstance.title = "Confirmation";
    modalRef.componentInstance.content = 'Are you sure you want to delete permission?';
    
    modalRef.result.then((response) => {
      if(response && response == true){
        this.permissionService.deletePermission(id).subscribe(() => {
          this.toastr.success("Permission removed successfully!",'Success')
          this.getPermissions();
        }, error =>{
          this.toastr.error(error.error['error-auxiliary-message']);
        });
      }
    });
  }

  /*
    Method: OpenTab
    Description: Used to open tabs with component selector
  */
 
  OpenTab(tab, id){
    if(id){
      this.permissionService.selectedPermission(id);
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
