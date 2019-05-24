import { Component, OnInit, Input, OnDestroy } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Router } from "@angular/router";

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
    private loaderService: LoaderService,
    private router:Router
  ) {}

  ngOnInit() {
    if (this.flowId) {
      this.getFlowPath(this.flowId);
    } else {
      console.error("Flow Id required");
    }
  }

  getFlowPath(flowId) {
    this.loaderService.show("Loading Flow Path...");
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
 loadIslDetail(type){
   if(type=='forward'){
    var src_switch = (this.forwardPathData && this.forwardPathData.length && this.forwardPathData[0]['switch_id'] ) ? this.forwardPathData[0]['switch_id']:null;
    var src_port = (this.forwardPathData && this.forwardPathData.length && this.forwardPathData[0]['output_port'] ) ? this.forwardPathData[0]['output_port'] : null;
    var dst_switch = (this.forwardPathData && this.forwardPathData.length && this.forwardPathData[1]['switch_id'] ) ? this.forwardPathData[1]['switch_id'] : null;
    var dst_port = (this.forwardPathData && this.forwardPathData.length && this.forwardPathData[1]['output_port'] ) ? this.forwardPathData[1]['input_port'] : null;
   }else if(type=='reverse'){
    var src_switch = (this.reversePathData && this.reversePathData.length && this.reversePathData[0]['switch_id'] ) ? this.reversePathData[0]['switch_id']:null;
    var src_port = (this.reversePathData && this.reversePathData.length && this.reversePathData[0]['output_port'] ) ? this.reversePathData[0]['output_port'] : null;
    var dst_switch = (this.reversePathData && this.reversePathData.length && this.reversePathData[1]['switch_id'] ) ? this.reversePathData[1]['switch_id'] : null;
    var dst_port = (this.reversePathData && this.reversePathData.length && this.reversePathData[1]['output_port'] ) ? this.reversePathData[1]['input_port'] : null;
  
   }
  this.router.navigate(["/isl/switch/isl/"+src_switch+"/"+src_port+"/"+dst_switch+"/"+dst_port]);
 }


 loadSwitchDetail(switchId){   
     this.router.navigate(["/switches/details/" + switchId]);
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
