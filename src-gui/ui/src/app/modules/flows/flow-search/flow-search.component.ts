import { Component, OnInit, Input } from "@angular/core";
import { Router } from "@angular/router";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";
import { FlowsService } from "src/app/common/services/flows.service";
import { ToastrService } from "ngx-toastr";
import { Location } from "@angular/common";
import { LoaderService } from "src/app/common/services/loader.service";

@Component({
  selector: "app-flow-search",
  templateUrl: "./flow-search.component.html",
  styleUrls: ["./flow-search.component.css"]
})
export class FlowSearchComponent implements OnInit {
  flowSearchForm: FormGroup;
  submitted = false;

  constructor(private router: Router, private formBuilder: FormBuilder,private flowService:FlowsService,
    private toaster: ToastrService,private loaderService:LoaderService,
    private _location:Location) {}

  ngOnInit() {
    this.flowSearchForm = this.formBuilder.group({
      flowID: ["", Validators.required]
    });
  }

  /** getter to get form fields */
  get f() {
    return this.flowSearchForm.controls;
  }

  searchFlow() {
    this.submitted = true;
    if (this.flowSearchForm.invalid) {
      return;
    }
    localStorage.setItem("filterFlag",'controller');
    var filterFlag = 'controller';
    this.loaderService.show('searching flow..');
    this.flowService.getFlowDetailById(this.flowSearchForm.controls.flowID.value,filterFlag).subscribe(
      flow => {
        this.router.navigate([
          "/flows/details/" + this.flowSearchForm.controls.flowID.value
        ]);
      },
      error => {
        var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No Flow found';
        this.toaster.error(errorMsg, "Error");
       // this._location.back();
        this.loaderService.hide();
      })
    
  }
}
