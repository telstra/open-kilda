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
    this.linkStoreForm = this.formbuilder.group({
       "urls":this.formbuilder.group({
              "get-all-link":this.formbuilder.group({
                "name": "get-all-link",
                "method-type": "GET",
                "url": ["",Validators.compose([
                          Validators.required,
                          (control:FormControl)=>{
                            let url = control.value;
                              if(!this.validateUrl(url)){
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
                              if(!this.validateUrl(url)){
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
                          if(!this.validateUrlParams(url,this.getlinkParamList)){
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
                                if(!this.validateUrl(url)){
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
                            if(!this.validateUrlParams(url,this.getlinkwithParamList)){
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
                              if(!this.validateUrl(url)){
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
                          if(!this.validateUrlParams(url,this.getcontractParamList)){
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
                              if(!this.validateUrl(url)){
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
                            if(!this.validateUrlParams(url,this.deletecontractParamList)){
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
			}
    },(error)=>{

    })
    this.storesettingservice.getLinkStoreDetails().subscribe((jsonResponse)=>{
      if(jsonResponse && jsonResponse['urls'] && typeof(jsonResponse['urls']['get-link']) !='undefined' &&  typeof(jsonResponse['urls']['get-link']['url'])!='undefined'){
				this.linkStoreObj = jsonResponse;
				this.linkStoreForm.setValue(jsonResponse);
        this.linkStoreForm.disable();
        this.isEdit = true;
			}
    },(error)=>{

    })
  }

  get i() {
    return this.linkStoreForm.controls;
  }

  validateUrlParams(url,params) {
    let return_flag = true;
    if(url=='' || url == null){
      return true;
    }
		for(let i=0; i < params.length; i++){
			if(url.includes(params[i])){
				return_flag = true;
			}else{
				return_flag = false;
				break;
			}
    }
		return return_flag;
  }
  
  validateUrl(url) { 
    if(url=='' || url == null){
      return true;
    }
		var res = url.match(/(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)/g);
	    if(res == null)
	        return false;
	    else
	        return true;
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
        this.loaderService.show('Deleting link store details...');
        this.storesettingservice.deleteLinkStore('/store/link-store-config/delete').subscribe((res:any)=>{
          this.loaderService.hide();
          this.toastr.success("Link store setting deleted successfully",'Success');
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
    this.loaderService.show('saving identity server details');
    this.storesettingservice.submitLinkData('/store/link-store-config/save',obj).subscribe((response:any)=>{
            this.linkStoreForm.setValue(response || {});
            this.loaderService.hide();
						this.toastr.success("Link store details saved successfully", 'Success');
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
