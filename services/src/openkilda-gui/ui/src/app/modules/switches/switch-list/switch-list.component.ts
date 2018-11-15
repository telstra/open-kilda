import {
  Component,
  OnInit,
  OnDestroy,
  AfterViewInit,
  ViewChild,
  Renderer2
} from "@angular/core";
import { DataTableDirective } from "angular-datatables";
import { SwitchService } from "../../../common/services/switch.service";
import { ToastrService } from "ngx-toastr";
import { Subject } from "rxjs";
import { Switch } from "../../../common/data-models/switch";
import { Router } from "@angular/router";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from "@angular/platform-browser";

@Component({
  selector: "app-switch-list",
  templateUrl: "./switch-list.component.html",
  styleUrls: ["./switch-list.component.css"]
})
export class SwitchListComponent implements OnDestroy, OnInit, AfterViewInit {
  dataSet = [];
  
  loadingData = true;
  

  constructor(
    private router: Router,
    private switchService: SwitchService,
    private toastr: ToastrService,
    private loaderService: LoaderService,
    private titleService: Title,
    private renderer: Renderer2
  ) {}

  ngOnInit() {
    let ref  =this;
    this.titleService.setTitle("OPEN KILDA - Switches");
    this.dataSet = [];

    var switchList = JSON.parse(localStorage.getItem("SWITCHES_LIST"));
    if (switchList) {
      this.dataSet = switchList;
      this.loadingData = false;
    } else {
      this.getSwitchList();
    }
  }

  ngAfterViewInit(): void {
    
  }

  ngOnDestroy(): void {
   
  }
  

  getSwitchList() {
    this.loadingData = true;
    this.loaderService.show("Loading Switches");
    let query = {_:new Date().getTime()};
    this.switchService.getSwitchList(query).subscribe(
      (data: any) => {
        localStorage.setItem("SWITCHES_LIST", JSON.stringify(data));
        if (!data || data.length == 0) {
          this.toastr.info("No Switch Available", "Information");
          this.dataSet = [];
        } else {
          this.dataSet = data;
        }
        this.loadingData = false;
        
      },
      error => {
        this.loaderService.hide();
        this.toastr.info("No Switch Available", "Information");
        this.dataSet = []
        this.loadingData = false;
      }
    );
  }

  
}
