import { Component, OnInit } from "@angular/core";
import { StoreSettingtService } from "src/app/common/services/store-setting.service";
import {
  FormBuilder,
  FormGroup,
  Validators,
  FormControl
} from "@angular/forms";
import { LoaderService } from "src/app/common/services/loader.service";
import { ToastrService } from "ngx-toastr";
import { ModalconfirmationComponent } from "src/app/common/components/modalconfirmation/modalconfirmation.component";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";

@Component({
  selector: "app-switch-store",
  templateUrl: "./switch-store.component.html",
  styleUrls: ["./switch-store.component.css"]
})
export class SwitchStoreComponent implements OnInit {
  paramsList = {
    switchWithIdParams: ["Loading..."],
    customersWithSwitchId: ["Loading..."],
    portsWithSwitchId: ["Loading..."],
    portWithId: ["Loading..."],
    portCustomersWithLinks: ["Loading..."]
  };
  switchStoreObj: any;
  switchStoreForm: FormGroup;
  isEdit: boolean = false;
  isEditable: boolean = false;
  submitted: boolean = false;

  constructor(
    private storesettingservice: StoreSettingtService,
    private formbuilder: FormBuilder,
    private loaderService: LoaderService,
    private toastr:ToastrService,
    private modalService: NgbModal
  ) {}

  ngOnInit() {
    this.buildSwitchStoreForm();
    this.getSwitchStoreConfig();
    this.getSwitchStoreUrls();
  }

  buildSwitchStoreForm() {
    this.switchStoreForm = this.formbuilder.group({
      urls: this.formbuilder.group({
        "get-all-switches": this.formbuilder.group({
          name: "get-all-switches",
          "method-type": "GET",
          url: [
            "",
            Validators.compose([
              Validators.required,
              (control: FormControl) => {
                let url = control.value;
                if (!this.storesettingservice.validateUrl(url)) {
                  return {
                    pattern: {
                      url: url
                    }
                  };
                }
                return null;
              }
            ])
          ],
          header: "Content-Type:application/json",
          body: "{}"
        }),
       "get-switch": this.formbuilder.group({
          name: "get-switch",
          "method-type": "GET",
          url: [""],
          header: "Content-Type:application/json",
          body: "{}"
        }),
        "get-switch-ports": this.formbuilder.group({
          name: "get-switch-ports",
          "method-type": "GET",
          url: [""],
          header: "Content-Type:application/json",
          body: "{[]}"
        }),
        "get-switch-port-flows": this.formbuilder.group({
          name: "get-switch-port-flows",
          "method-type": "GET",
          url: [""],
          header: "Content-Type:application/json",
          body: "{}"
        })
      })
    });
  }

  get i() {
    return this.switchStoreForm.controls;
  }

  /**Get all switch store urls */
  getSwitchStoreUrls() {
    this.loaderService.show('Loading Switch Store Settings');
    this.storesettingservice.getSwitchStoreUrl().subscribe(response => {
      if (response && response.length) {
        for (var i = 0; i < response.length; i++) {
          switch (response[i]) {
           case "get-switch":
              this.storesettingservice
                .getData("/url/params/get-switch")
                .subscribe(this.processSwitchIdParams, this.errorHanlder);
              break;
            case "get-switch-ports":
              this.storesettingservice
                .getData("/url/params/get-switch-ports")
                .subscribe(this.processPortsWithSwitchId, this.errorHanlder);
              break;
            case "get-switch-port-flows":
              this.storesettingservice
                .getData("/url/params/get-switch-port-flows")
                .subscribe(
                  this.processPortCustomersWithLinks,
                  this.errorHanlder
                );
              break;
          }
        }
      }
      setTimeout(()=>{ this.loaderService.hide();},3000);
    });
  }

  /**Get switch store stored values*/
  getSwitchStoreConfig() { 
    this.storesettingservice.getSwitchStoreDetails().subscribe(
      jsonResponse => {
        if (
          jsonResponse &&
          jsonResponse["urls"] &&
          typeof jsonResponse["urls"]["get-all-switches"] != "undefined" &&
          typeof jsonResponse["urls"]["get-all-switches"]["url"] != "undefined"
        ) {
          let newResponse = {
              "get-all-switches":{
                name:"get-all-switches",
                url:""
              },
              "get-switch":{
                name:"get-switch",
                url:""
              },
              "get-switch-ports":{
                name:"get-switch-ports",
                url:"",
              },
              "get-switch-port-flows":{
                name:"get-switch-port-flows",
                url:""
              }
          };

          let response ={urls: {...newResponse,...jsonResponse.urls}};
          
          this.switchStoreObj = response;
          this.switchStoreForm.setValue(response);
          this.switchStoreForm.disable();      
          this.isEdit = true;
        }
      },
      err => {}
    );
  }

  /**BEGIN : Url param request handlers */
  processParamsDataList = data => {
    return data.map(function(d) {
      return d["param-name"];
    });
  };
  processSwitchIdParams = data => {
    this.paramsList.switchWithIdParams = this.processParamsDataList(data);
    setTimeout(() => {
      this.bindUrlValidatorsWithParams(
        "get-switch",
        this.paramsList.switchWithIdParams
      );
    }, 100);

    /**/
  };

  processCustomersWithSwitchIdParams = data => {
    this.paramsList.customersWithSwitchId = this.processParamsDataList(data);
    setTimeout(() => {
      this.bindUrlValidatorsWithParams(
        "get-customers-with-switch-id",
        this.paramsList.customersWithSwitchId
      );
    }, 100);
  };

  processPortsWithSwitchId = data => {
    this.paramsList.portsWithSwitchId = this.processParamsDataList(data);
    setTimeout(() => {
      this.bindUrlValidatorsWithParams(
        "get-switch-ports",
        this.paramsList.portsWithSwitchId
      );
    }, 100);
  };

  processPortWithId = data => {
    this.paramsList.portWithId = this.processParamsDataList(data);
    setTimeout(() => {
      this.bindUrlValidatorsWithParams(
        "get-port-with-id",
        this.paramsList.portWithId
      );
    }, 100);
  };

  processPortCustomersWithLinks = data => {
    this.paramsList.portCustomersWithLinks = this.processParamsDataList(data);
    setTimeout(() => {
      this.bindUrlValidatorsWithParams(
        "get-switch-port-flows",
        this.paramsList.portCustomersWithLinks
      );
    }, 100);
  };

  errorHanlder = err => {};
  /**END */

  bindUrlValidatorsWithParams(controlName, paramsList) {
    this.switchStoreForm.controls["urls"]["controls"][controlName].controls[
      "url"
    ].setValidators([
      Validators.required,
      (control: FormControl) => {
        let url = control.value;
        if (!this.storesettingservice.validateUrl(url)) {
          return {
            pattern: {
              url: url
            }
          };
        }
        return null;
      },
      (control: FormControl) => {
        let url = control.value;
        if (!this.storesettingservice.validateUrlParams(url, paramsList)) {
          return {
            paramError: {
              url: url
            }
          };
        }
        return null;
      }
    ]);
  }

  enableEditForm() {
    this.isEditable = true;
    this.switchStoreForm.enable();
  }
  cancelEditForm() {
    this.isEditable = false;
    this.isEdit = true;
    this.switchStoreForm.reset();
    this.switchStoreForm.setValue(this.switchStoreObj);
    this.switchStoreForm.disable();
  }

  submitSwitchStore(){
    this.submitted = true;
    if (this.switchStoreForm.invalid){
      return;
    }
    this.submitted = false;
    var obj = this.switchStoreForm.value;
    this.loaderService.show("Saving Switch Store Settings");
    this.storesettingservice
      .submitLinkData("/store/switch-store-config/save", obj)
      .subscribe(
        (response: any) => {
          this.switchStoreForm.setValue(response || {});
          this.loaderService.hide();
          this.toastr.success(
            "Switch Store Settings Saved Successfully",
            "Success"
          );
          this.switchStoreForm.disable();
          this.isEditable = false;
          this.isEdit = true;
        },
        error => {
          this.loaderService.hide();
          var errorMsg =
            error && error.error && error.error["error-auxiliary-message"]
              ? error.error["error-auxiliary-message"]
              : "Error in saving link store";
          this.toastr.error(errorMsg, "Error");
        }
      );
  }

  deleteSwitchStore(){

    const modalReff = this.modalService.open(ModalconfirmationComponent);
    modalReff.componentInstance.title = "Confirmation";
    modalReff.componentInstance.content = 'Are you sure you want to delete switch store setting?';
    
    modalReff.result.then((response) => {
      if(response && response == true){
        this.loaderService.show('Deleting Switch Store Settings');
        this.storesettingservice.deleteSwitchStore('/store/switch-store-config/delete').subscribe((res:any)=>{
          this.loaderService.hide();
          this.toastr.success("Switch Store Settings Deleted Successfully",'Success');
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


}
