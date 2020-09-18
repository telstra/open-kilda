import { Component, OnInit,OnChanges, Renderer2, SimpleChanges} from '@angular/core';
import { SamlSettingService } from 'src/app/common/services/saml-setting.service';
import { LoaderService } from 'src/app/common/services/loader.service';
import {MessageObj} from '../../../common/constants/constants';
import { ToastrService } from 'ngx-toastr';
import { DataTableDirective } from 'angular-datatables';
import { Subject } from 'rxjs';
import { ModalconfirmationComponent } from '../../../common/components/modalconfirmation/modalconfirmation.component';
import { ModalComponent } from '../../../common/components/modal/modal.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
@Component({
  selector: 'app-saml-setting',
  templateUrl: './saml-setting.component.html',
  styleUrls: ['./saml-setting.component.css']
})
export class SamlSettingComponent implements OnInit,OnChanges {
  AddAuthProvider:any=false;
  EditAuthProvider:any=false;
  EditProviderData:any=null;
  loadProviderData=true;
  authProviders:any=[];  
  constructor( 
      private samlSettingService:SamlSettingService,
      private loaderService:LoaderService,
      private toastr:ToastrService,
      private renderer:Renderer2,
      private modalService: NgbModal,
    ) {
      
     }

  ngOnInit() {
    this.authProviders = [];
    this.getAuthProviders();
  }
  
  
  getAuthProviders(){
    this.loadProviderData = true;
    this.loaderService.show(MessageObj.loading_data);
    this.samlSettingService.getAuthProviders().subscribe((data)=>{
      this.loadProviderData = false;
      this.authProviders = data;
      this.loaderService.hide();
    },(error)=>{
      this.authProviders = [];
      this.loadProviderData = false;
      var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:MessageObj.error_loading_data;
      this.toastr.error(errorMsg,'Error');
      this.loaderService.hide();
    })
  }

  addAuthProvider(){
    this.AddAuthProvider = true;
    this.EditAuthProvider = false;
    this.EditProviderData = null;
  }

  editProvider(row){
    this.EditAuthProvider = true;
    this.AddAuthProvider = false;
    this.EditProviderData = row;
  }
  cancelAction(){
    this.EditAuthProvider = false;
    this.AddAuthProvider = false;
    this.EditProviderData = null;
    this.authProviders = [];
    this.getAuthProviders();
  }
  deleteProvider(row){

    var self = this;
    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Delete Auth Provider";
    modalReff.componentInstance.content = 'Are you sure you want to perform delete action ?';
    
    modalReff.result.then((response) => {
      if(response && response == true){
        this.samlSettingService.deleteAuthProvider(row.uuid).subscribe((res:any)=>{
          modalReff.close();
          this.authProviders = [];
          this.getAuthProviders();
        },(error)=>{
    
        })
        
      }
    });
   
  } 
  ngOnChanges(change:SimpleChanges){
    
  }

  ngOnDestroy(): void {
    
  }

}
