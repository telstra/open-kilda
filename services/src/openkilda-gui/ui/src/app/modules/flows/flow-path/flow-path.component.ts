import { Component, OnInit, Input, OnDestroy } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";

@Component({
  selector: "app-flow-path",
  templateUrl: "./flow-path.component.html",
  styleUrls: ["./flow-path.component.css"]
})
export class FlowPathComponent implements OnInit, OnDestroy {
  @Input() flowId;

  flowPathData: any;
  forwardPathGraph: Boolean = false;
  reversePathGraph: Boolean = false;

  reversePathData = [];
  forwardPathData = [];

  constructor(
    private flowService: FlowsService,
    private loaderService: LoaderService
  ) {}

  ngOnInit() {
    if (this.flowId) {
      this.getFlowPath(this.flowId);
    } else {
      console.error("Flow Id required");
    }
  }

  getFlowPath(flowId) {
    this.loaderService.show("Fetching flow path");
    this.flowService.getFlowPath(flowId).subscribe(
      data => {
        this.flowPathData = data;
        this.forwardPathData = data.flowpath_forward;
        this.reversePathData = data.flowpath_reverse;
        this.loaderService.hide();
      },
      error => {
        this.loaderService.hide();
      }
    );
  }

  viewPathGraph(type) {
    if (type == "forward") {
      this.reversePathGraph = false;
      this.forwardPathGraph = this.forwardPathGraph ? false : true;
    } else {
      this.forwardPathGraph = false;
      this.reversePathGraph = this.reversePathGraph ? false : true;
    }
  }

  

  ngOnDestroy(){
    this.reversePathGraph = false;
    this.forwardPathGraph = false;
  }
}
