import { Component, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, NgForm, FormControl, NgModel } from "@angular/forms";
import { StoreSettingtService } from '../../../common/services/store-setting.service';
import {LinkStoreModel} from '../../../common/data-models/linkstore-model';
import { ToastrService } from 'ngx-toastr';
import { LoaderService } from '../../../common/services/loader.service';
import { ModalconfirmationComponent } from 'src/app/common/components/modalconfirmation/modalconfirmation.component';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
@Component({
  selector: 'app-link-store',
  templateUrl: './link-store.component.html',
  styleUrls: ['./link-store.component.css']
})
export class LinkStoreComponent implements OnInit {
  linkStoreForm: FormGroup;
  isEdit:boolean=false;
  isEditable:boolean = false;
  submitted:boolean=false;
  getlinkParamList:any;
  getlinkwithParamList:any;
  getcontractParamList:any;
  deletecontractParamList:any;
  linkStoreObj:LinkStoreModel;
  constructor(
    private storesettingservice: StoreSettingtService,
    private formbuilder:FormBuilder,
    private toastr: ToastrService,
    private loaderService:LoaderService,
    private modalService:NgbModal
  ) { }

  ngOnInit() {
    this.loaderService.show('Loading Link Store Details');
    this.linkStoreForm = this.formbuilder.group({
       "urls":this.formbuilder.group({
              "get-status-list":this.formbuilder.group({
                "name": "get-status-list",
                "method-type": "GET",
                "url": ["",Validators.compose([
                          Validators.required,
                          (control:FormControl)=>{
                            let url = control.value;
                              if(!this.storesettingservice.validateUrl(url)){
                                return {
                                  pattern:{
                                    url:url
                                  }
                                }
                              }
                              return null;
                          },
                        ])],
                "header": "Content-Type:application/json",
                "body": "{}"
            }),
            "get-link": this.formbuilder.group({
                "name": "get-link",
                "method-type": "GET",
                "url":[""],
                "header": "Content-Type:application/json",
                "body": "{}"
            }),
            "get-contract": this.formbuilder.group({
                "name": "get-contract",
                "method-type": "GET",
                "url": [""],
                "header": "Content-Type:application/json",
                "body": "{}"
            }),
            "get-link-with-param": this.formbuilder.group({
                "name": "get-link-with-param",
                "method-type": "GET",
                "url":[""],
                "header": "Content-Type:application/json",
                "body": "{[]}"
            }),
            "delete-contract": this.formbuilder.group({
                "name": "delete-contract",
                "method-type": "DELETE",
                "url": [""],
                "header": "Content-Type:application/json",
                "body": "{}"
            })
        
      })      
    });
    
    this.loaderService.show('Loading Link Store Settings');
    this.storesettingservice.getLinkStoreUrl().subscribe((response)=>{
      if(response && response.length){
				for(var i=0; i < response.length; i++) { 
					 switch(response[i]){ 
              case 'get-link': 
                    this.storesettingservice.getData('/url/params/'+response[i]).subscribe((data)=>{
                      this.getlinkParamList = data.map(function(d){ return d['param-name']});
                      setTimeout(()=>{
                        this.linkStoreForm.controls['urls']['controls']['get-link'].controls['url'].setValidators([
                          Validators.required,
                          (control:FormControl)=>{
                            let url = control.value;
                              if(!this.storesettingservice.validateUrl(url)){
                                return {
                                  pattern:{
                                    url:url
                                  }
                                }
                              }
                              return null;
                          },
                          (control:FormControl)=>{
                          let url = control.value;
                          if(!this.storesettingservice.validateUrlParams(url,this.getlinkParamList)){
                            return {
                              paramError:{
                                url:url
                              }
                            }
                          }
                          return null;
                        }]);
                      },100);
                      
                    })
								  break;
               case 'get-link-with-param':
                      this.storesettingservice.getData('/url/params/'+response[i]).subscribe((data)=>{
                        this.getlinkwithParamList = data.map(function(d){ return d['param-name']});
                        setTimeout(()=>{
                          this.linkStoreForm.controls['urls']['controls']['get-link-with-param'].controls['url'].setValidators([
                            Validators.required,
                            (control:FormControl)=>{
                              let url = control.value;
                                if(!this.storesettingservice.validateUrl(url)){
                                  return {
                                    pattern:{
                                      url:url
                                    }
                                  }
                                }
                                return null;
                            },
                          (control:FormControl)=>{
                            let url = control.value;
                            if(!this.storesettingservice.validateUrlParams(url,this.getlinkwithParamList)){
                              return {
                                paramError:{
                                  url:url
                                }
                              }
                            }
                            return null;
                          }]);
                        },100);
                        
                      })
                   break;
               case 'get-contract' : 
                    this.storesettingservice.getData('/url/params/'+response[i]).subscribe((data)=>{
                      this.getcontractParamList = data.map(function(d){ return d['param-name']});
                      setTimeout(()=>{
                        this.linkStoreForm.controls['urls']['controls']['get-contract'].controls['url'].setValidators([
                          Validators.required,
                          (control:FormControl)=>{
                            let url = control.value;
                              if(!this.storesettingservice.validateUrl(url)){
                                return {
                                  pattern:{
                                    url:url
                                  }
                                }
                              }
                              return null;
                          },
                          (control:FormControl)=>{
                          let url = control.value;
                          if(!this.storesettingservice.validateUrlParams(url,this.getcontractParamList)){
                            return {
                              paramError:{
                                url:url
                              }
                            }
                          }
                          return null;
                        }]);
                      },100);
                      
                    })
                  break;
               case 'delete-contract' : 
                      this.storesettingservice.getData('/url/params/'+response[i]).subscribe((data)=>{
                        this.deletecontractParamList = data.map(function(d){ return d['param-name']});
                        setTimeout(()=>{
                          this.linkStoreForm.controls['urls']['controls']['delete-contract'].controls['url'].setValidators([
                            Validators.required,
                          (control:FormControl)=>{
                            let url = control.value;
                              if(!this.storesettingservice.validateUrl(url)){
                                return {
                                  pattern:{
                                    url:url
                                  }
                                }
                              }
                              return null;
                          },
                          (control:FormControl)=>{
                            let url = control.value;
                            if(!this.storesettingservice.validateUrlParams(url,this.deletecontractParamList)){
                              return {
                                paramError:{
                                  url:url
                                }
                              }
                            }
                            return null;
                          }]);
                        },100);
                        
                      })
							     break;
					 }
        }
        this.loadStoreDetail();
			}
    },(error)=>{
      this.loadStoreDetail();
    });
    
  }


  loadStoreDetail(){
    var self = this;
    this.storesettingservice.getLinkStoreDetails().subscribe((jsonResponse)=>{
      if(jsonResponse && jsonResponse['urls'] && typeof(jsonResponse['urls']['get-link']) !='undefined' &&  typeof(jsonResponse['urls']['get-link']['url'])!='undefined'){
				this.linkStoreObj = jsonResponse;
				this.linkStoreForm.setValue(jsonResponse);
        this.linkStoreForm.disable();
        this.isEdit = true;
        setTimeout(function(){
          self.loaderService.hide();
        },300);
        
			}else{
        setTimeout(function(){
          self.loaderService.hide();
        },300);
      }
    },(error)=>{
      setTimeout(function(){
        self.loaderService.hide();
      },300);
    })
  }

  get i() {
    return this.linkStoreForm.controls;
  }
  
  enableEditForm(){
    this.isEditable = true;
    this.linkStoreForm.enable();
  }
  cancelEditForm() {
   this.isEditable = false;
   this.isEdit= true;
   this.linkStoreForm.reset();
   this.linkStoreForm.setValue( this.linkStoreObj);
   this.linkStoreForm.disable();
  }

  deleteLinkStore(){

    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to delete link store setting?';
    
    modalReff.result.then((response) => {
      if(response && response == true){
        this.loaderService.show('Deleting Link Store Settings');
        this.storesettingservice.deleteLinkStore('/store/link-store-config/delete').subscribe((res:any)=>{
          this.loaderService.hide();
          this.toastr.success("Link Store Settings Deleted Successfully",'Success');
          setTimeout(function(){
            location.reload();
          },500);
        },(error)=>{
          var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:'Error in deleting link store';
          this.toastr.error(errorMsg,'Error');
        })
      }
    });
  }
  
  
  submitLinkStore(){
    this.submitted = true;
     if (this.linkStoreForm.invalid) {
        return;
      }
      this.submitted = false;
    var obj = this.linkStoreForm.value;
    this.loaderService.show('Saving Link Store Settings');
    this.storesettingservice.submitLinkData('/store/link-store-config/save',obj).subscribe((response:any)=>{
            this.linkStoreForm.setValue(response || {});
            this.loaderService.hide();
						this.toastr.success("Link Store Settings Saved Successfully", 'Success');
            this.linkStoreForm.disable();
            this.isEditable = false;
            this.isEdit = true;
          },(error)=>{
            this.loaderService.hide();
         var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']:'Error in saving link store';
          this.toastr.error(errorMsg,'Error');
    });
  }

}
