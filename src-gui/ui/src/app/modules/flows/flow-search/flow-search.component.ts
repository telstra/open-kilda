import { Component, OnInit, Input } from "@angular/core";
import { Router } from "@angular/router";
import { FormBuilder, FormGroup, Validators } from "@angular/forms";

@Component({
  selector: "app-flow-search",
  templateUrl: "./flow-search.component.html",
  styleUrls: ["./flow-search.component.css"]
})
export class FlowSearchComponent implements OnInit {
  flowSearchForm: FormGroup;
  submitted = false;

  constructor(private router: Router, private formBuilder: FormBuilder) {}

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
    this.router.navigate([
      "/flows/details/" + this.flowSearchForm.controls.flowID.value
    ]);
  }
}
