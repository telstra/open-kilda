import { Component, OnInit, Input, OnDestroy } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { NgxSpinnerService } from "ngx-spinner";
import { LoaderService } from "../../../common/services/loader.service";
import { Router } from "@angular/router";
import * as d3 from "d3";
import { CommonService } from "src/app/common/services/common.service";
import { FlowpathService } from "src/app/common/services/flowpath.service";

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
  reversePathLoader : boolean = false;
  loadreversePath : boolean = false;
  reverseGraphSvg : boolean = false;
  forwardGraphSvg : boolean = false;
  forwardPathLoader: boolean = false;
  loadforwardPath : boolean = false;
  showReversePath : boolean = true;
  showForwardPath : boolean = true;
  showFlowsForward : boolean = false;
  showFlowsReverse : boolean = false;
  isDiverseForward : boolean = false;
  isDiverseReverse : boolean = false;
  flowPathFlagForward : any = [];
  flowPathFlagReverse : any = [];
  commonSwitchFlagReverse:any = [];
  commonSwitchFlagForward:any = [];
  forwardLabelText = "FORWARD PATH";
  reverseLabelText = "REVERSE PATH";
  diversePath = {};
  diverseGroup = [];
  colourCodes=[];
  pathFlows = [];
  hasDiverseGroup:boolean=false;
  forwardPathSwitches = [];
  reversePathSwitches = [];
  reversePathData = [];
  forwardPathData = [];
  showDiverseGroupReverse :boolean = false;
  showDiverseGroupForward :boolean = false;
  diverseUniqueSwitches = [];
  diverseGroupCommonSwitch = [];

  pathData = [] ;
  

  constructor(
    private flowService: FlowsService,
    private loaderService: LoaderService,
    private commonService: CommonService,
    private flowpathService:FlowpathService
  ) {}

  ngOnInit() {
    if (this.flowId) {
      this.getFlowPath(this.flowId);
    } else {
      console.error("Flow Id required");
    }
  }

  getFlowPath(flowId) {
    var self = this;
    this.loaderService.show("Loading Flow Path...");
    this.flowService.getFlowPath(flowId).subscribe(
      data => {
        this.flowPathData = data;
        this.forwardPathData = data.flowpath_forward;
        this.reversePathData = data.flowpath_reverse;
        this.plotForwardPath(data);
        this.plotReversePath(data);
        this.loadDiverseGroup();        
        this.loaderService.hide();
      },
      error => {
        this.loaderService.hide();
      }
    );
  }

  plotForwardPath(data){
    let self = this;
    var commonSwitches =[];
    var links = [];
    var j = 0;
    var flowId = data.flowid;
        for(let d of self.forwardPathData){ 
             j++;
            var inPort = "sw_"+d.switch_id + "_"+ d.input_port;
            var outPort = "sw_"+d.switch_id + "_"+ d.output_port;
            commonSwitches.push(inPort);
            commonSwitches.push(d.switch_id);
            commonSwitches.push(outPort);
            links.push({flow:flowId,source:{switch_id:inPort,switch_name:inPort},target:d,colourCode:'#00baff',type:'port_isl'});
            links.push({flow:flowId,source:d,target:{switch_id:outPort,switch_name:outPort},colourCode:'#00baff',type:'port_isl'});
            if(typeof(self.forwardPathData[j]) !='undefined'){
              var nextSwitchInPort = "sw_"+self.forwardPathData[j].switch_id+"_"+self.forwardPathData[j].input_port;
               links.push({flow:flowId,source_detail:{out_port:d.output_port,in_port:d.input_port,id:d.switch_id},target_detail:{out_port:self.forwardPathData[j].output_port,in_port:self.forwardPathData[j].input_port,id:self.forwardPathData[j].switch_id},source:{switch_id:outPort,switch_name:outPort},target:{switch_id:nextSwitchInPort,switch_name:nextSwitchInPort},colourCode:'#00baff',type:'isl'});
            }
        }
    // fetching unique switches in all diverse group
    if(commonSwitches && commonSwitches.length){
        for(let switchid of commonSwitches){
          if(this.forwardPathSwitches.indexOf(switchid)==-1){
            this.forwardPathSwitches.push(switchid);
          }
        }
      }
      var nodes = [];
      // creating nodes object array
     for(let d of this.forwardPathSwitches){
       nodes.push({switch_id:d});
     } 
     var svgElement = d3.select('#svgForwardPath');
     var element =$('#forwardPathWrapper');
     var positions = this.flowpathService.generatePositionForNodes(element,nodes);
      
      this.forwardPathLoader= true;
      this.loadforwardPath = true;
      
      this.flowpathService.initSimulation(nodes,links,svgElement,"forwardPathWrapper",'forward',positions,'flowpath-hover-txt','forwardpath_flow_value','reversepath_flow_value');
      this.flowpathService.forwardpathLoadedChange.subscribe((value:any)=>{
        this.forwardPathLoader= value;
        this.loadforwardPath = value;
      })
  }

 
  plotReversePath(data){
    let self = this;
    var commonSwitches =[];
    var links = [];
    var j = 0;
    var flowId = data.flowid;
        for(let d of self.reversePathData){ 
             j++;
            var inPort = "swr_"+d.switch_id + "_"+ d.input_port;
            var outPort = "swr_"+d.switch_id + "_"+ d.output_port;
            commonSwitches.push(inPort);
            commonSwitches.push(d.switch_id);
            commonSwitches.push(outPort);
            links.push({flow:flowId,source:{switch_id:inPort,switch_name:inPort},target:d,colourCode:'#00baff',type:'port_isl'});
            links.push({flow:flowId,source:d,target:{switch_id:outPort,switch_name:outPort},colourCode:'#00baff',type:'port_isl'});
            if(typeof(self.reversePathData[j]) !='undefined'){
              var nextSwitchInPort = "swr_"+self.reversePathData[j].switch_id+"_"+self.reversePathData[j].input_port;
              links.push({flow:flowId,source_detail:{out_port:d.output_port,in_port:d.input_port,id:d.switch_id},target_detail:{out_port:self.reversePathData[j].output_port,in_port:self.reversePathData[j].input_port,id:self.reversePathData[j].switch_id},source:{switch_id:outPort,switch_name:outPort},target:{switch_id:nextSwitchInPort,switch_name:nextSwitchInPort},colourCode:'#00baff',type:'isl'});
            }
        }
    // fetching unique switches in all diverse group
    if(commonSwitches && commonSwitches.length){
        for(let switchid of commonSwitches){
          if(this.reversePathSwitches.indexOf(switchid)==-1){
            this.reversePathSwitches.push(switchid);
          }
        }
      }
      // creating nodes object array
      var nodes = [];
     for(let d of this.reversePathSwitches){
       nodes.push({switch_id:d});
     } 
     var svgElement = d3.select('#svgReversePath');
     var element =$('#reversePathWrapper');
     var positions = this.flowpathService.generatePositionForNodes(element,nodes);
      this.reversePathLoader = true;
      this.loadreversePath = true;
     this.flowpathService.initSimulation(nodes,links,svgElement,"reversePathWrapper",'reverse',positions,'flowpath-hover-txt','reversepath_flow_value','forwardpath_flow_value');
     this.flowpathService.reversepathLoadedChange.subscribe((value:any)=>{
      this.reversePathLoader= value;
      this.loadreversePath = value;
    })
  }


  plotForwardDiverse(){
    let self = this;
    var commonSwitches =[];
    var links = [];
    var diverseUniqueSwitches =[];
    var nodes = [];
    var diverseGroupCommonSwitch = [];
      Object.keys(this.diversePath).map(function(i,v){
        var j = 0;
        for(let d of self.diversePath[i].forward_path){
        if(j < self.diversePath[i].forward_path.length){  j++;
            var inPort = "sw_"+d.switch_id + "_"+ d.input_port;
            var outPort = "sw_"+d.switch_id + "_"+ d.output_port;
            commonSwitches.push(inPort);
            commonSwitches.push(d.switch_id);
            commonSwitches.push(outPort);
            links.push({flow:i,source:{switch_id:inPort,switch_name:inPort},target:d,colourCode:self.colourCodes[v],type:'port_isl'});
            links.push({flow:i,source:d,target:{switch_id:outPort,switch_name:outPort},colourCode:self.colourCodes[v],type:'port_isl'});
            if(typeof(self.diversePath[i].forward_path[j]) !='undefined'){
              var nextSwitchInPort = "sw_"+self.diversePath[i].forward_path[j].switch_id+"_"+self.diversePath[i].forward_path[j].input_port;
              links.push({flow:i,source_detail:{out_port:d.output_port,in_port:d.input_port,id:d.switch_id},target_detail:{out_port:self.diversePath[i].forward_path[j].output_port,in_port:self.diversePath[i].forward_path[j].input_port,id:self.diversePath[i].forward_path[j].switch_id},source:{switch_id:outPort,switch_name:outPort},target:{switch_id:nextSwitchInPort,switch_name:nextSwitchInPort},colourCode:self.colourCodes[v],type:'isl'});
            }
          }
        }
      });
    // fetching unique switches in all diverse group
    if(commonSwitches && commonSwitches.length){
        for(let switchid of commonSwitches){
          if(diverseUniqueSwitches.indexOf(switchid)==-1){
            diverseUniqueSwitches.push(switchid);
          }else{
            var value = switchid.split("_");
            if(typeof(value[1])=='undefined' && diverseGroupCommonSwitch.indexOf(switchid) == -1){
              diverseGroupCommonSwitch.push(switchid);
            }
          }
        }
      }
      this.flowpathService.setCommonSwitch('forward',diverseGroupCommonSwitch);
      // creating nodes object array
     for(let d of diverseUniqueSwitches){
       nodes.push({switch_id:d});
     } 
     var svgElement = d3.select('#svgForwardPath');
     var element =$('#forwardPathWrapper');
     var positions = this.flowpathService.generatePositionForNodes(element,nodes);
     this.forwardPathLoader= true;
      this.loadforwardPath = true;
     this.flowpathService.initSimulation(nodes,links,svgElement,"forwardPathWrapper",'forwardDiverse',positions,"diversepath-hover-txt","forward_flow_value","reverse_flow_value")
     this.flowpathService.forwardpathLoadedChange.subscribe((value:any)=>{
      this.forwardPathLoader= value;
      this.loadforwardPath = value;
    })
  }
  plotReverseDiverse(){
    let self = this;
    var commonSwitches =[];
    var links = [];
    var nodes = [];
    var diverseUniqueSwitchesReverse = [];
    var diverseGroupCommonSwitchReverse = [];
    Object.keys(this.diversePath).map(function(i,v){
        var j = 0;
        for(let d of self.diversePath[i].reverse_path){
        if(j < self.diversePath[i].reverse_path.length){  j++;
            var inPort = "swr_"+d.switch_id + "_"+ d.input_port;
            var outPort = "swr_"+d.switch_id + "_"+ d.output_port;
            commonSwitches.push(inPort);
            commonSwitches.push(d.switch_id);
            commonSwitches.push(outPort);
            links.push({flow:i,source:{switch_id:inPort,switch_name:inPort},target:d,colourCode:self.colourCodes[v],type:'port_isl'});
            links.push({flow:i,source:d,target:{switch_id:outPort,switch_name:outPort},colourCode:self.colourCodes[v],type:'port_isl'});
            if(typeof(self.diversePath[i].reverse_path[j]) !='undefined'){
              var nextSwitchInPort = "swr_"+self.diversePath[i].reverse_path[j].switch_id+"_"+self.diversePath[i].reverse_path[j].input_port;
              links.push({flow:i,sourcce_detail:{out_port:d.output_port,in_port:d.input_port,id:d.switch_id},target_detail:{out_port:self.diversePath[i].reverse_path[j].output_port,in_port:self.diversePath[i].reverse_path[j].input_port,id:self.diversePath[i].reverse_path[j].switch_id},source:{switch_id:outPort,switch_name:outPort},target:{switch_id:nextSwitchInPort,switch_name:nextSwitchInPort},colourCode:self.colourCodes[v],type:'isl'});
            }
          }
        }
      });
    // fetching unique switches in all diverse group
    if(commonSwitches && commonSwitches.length){
        for(let switchid of commonSwitches){
          if(diverseUniqueSwitchesReverse.indexOf(switchid)==-1){
            diverseUniqueSwitchesReverse.push(switchid);
          }else{
            var value = switchid.split("_");
            if(typeof(value[1])=='undefined' && diverseGroupCommonSwitchReverse.indexOf(switchid) == -1){
              diverseGroupCommonSwitchReverse.push(switchid);
            }
            
          }
        }
      }
      this.flowpathService.setCommonSwitch('reverse',diverseGroupCommonSwitchReverse);
      // creating nodes object array
     for(let d of diverseUniqueSwitchesReverse){
       nodes.push({switch_id:d});
     }
     var svgElement = d3.select('#svgReversePath');
     var element =$('#reversePathWrapper');
     this.reversePathLoader = true;
      this.loadreversePath = true;
       var positions = this.flowpathService.generatePositionForNodes(element,nodes);
       this.flowpathService.initSimulation(nodes,links,svgElement,"reversePathWrapper",'reverseDiverse',positions,"diversepath-hover-txt","reverse_flow_value","forward_flow_value")
       this.flowpathService.reversepathLoadedChange.subscribe((value:any)=>{
        this.reversePathLoader = value;
        this.loadreversePath = value;
      })
  }
  

  zoomFn(type,dir){
    if(type == 'forward'){
      this.showFlowsForward = false;
      var svgElement = d3.select('#svgForwardPath');
    }else if(type == 'forwardDiverse'){
      var svgElement = d3.select('#svgForwardPath');
      this.showFlowsForward = false;
    }else if(type == 'reverse'){
      var svgElement = d3.select('#svgReversePath');
      this.showFlowsReverse = false;
    }else if(type == 'reverseDiverse'){
      var svgElement = d3.select('#svgReversePath');
      this.showFlowsReverse = false;
    }
    
    var direction = (dir =='in') ? 1 : -1;
    this.flowpathService.zoomFn(direction,svgElement,type)
  }
  
  showFlowPath(flowid,type){
    if(type=='forward'){
      var svgElement = d3.select('#svgForwardPath');
        var flows = Object.keys(this.flowPathFlagForward);
        if(typeof(this.flowPathFlagForward[flowid]) !='undefined' && this.flowPathFlagForward[flowid]){
          d3.selectAll(".forwardDiverse_link_"+flowid)
          .transition()
          .style("stroke-width", "2.5");
          var allLinks = svgElement.selectAll(".link");
          allLinks.style("stroke-width", "2.5");
          this.flowPathFlagForward[flowid] = false;
        }else{
          if(flows && flows.length){
            flows.map((i,v)=>{
              if(this.flowPathFlagForward[i]){
                  d3.selectAll(".forwardDiverse_link_"+i)
                  .transition()
                  .style("stroke-width", "2.5");
                  this.flowPathFlagForward[i] = false;
              }
              
            });
          }
          var allLinks = svgElement.selectAll(".link");
          allLinks.style("stroke-width", "1");
          var links = svgElement.selectAll(".forwardDiverse_link_"+flowid);
          links.style("stroke-width", "5");
          this.flowPathFlagForward[flowid] = true;
        }
    }else{
      var flows = Object.keys(this.flowPathFlagReverse);
      var svgElement = d3.select('#svgReversePath');
      if(typeof(this.flowPathFlagReverse[flowid]) !='undefined' && this.flowPathFlagReverse[flowid]){
        d3.selectAll(".reverseDiverse_link_"+flowid)
        .transition()
        .style("stroke-width", "2.5");
        var allLinks = svgElement.selectAll(".link");
          allLinks.style("stroke-width", "2.5");
        this.flowPathFlagReverse[flowid] = false;
      }else{
        if(flows && flows.length){
          flows.map((i,v)=>{
            if(this.flowPathFlagReverse[i]){
                d3.selectAll(".reverseDiverse_link_"+i)
                .transition()
                .style("stroke-width", "2.5");
                this.flowPathFlagReverse[i] = false;
            }
            
          });
        }
        var allLinks = svgElement.selectAll(".link");
          allLinks.style("stroke-width", "1");
        var links = svgElement.selectAll(".reverseDiverse_link_"+flowid);
        links.style("stroke-width", "5");
        this.flowPathFlagReverse[flowid] = true;
      }
    }
  }

 
  showCommonSwitch(type){
    if(type=='forward'){
      var commmonSwitch = this.flowpathService.getcommonSwitches('forward');
      for(var i = 0; i < commmonSwitch.length; i++){
        var selectedVal = commmonSwitch[i].split("_");
        var switch_id = (typeof(selectedVal[1]) !='undefined') ? selectedVal[1]: selectedVal;
         var element = document.getElementById("forwardDiverse_circle_" + switch_id);
        var classes = "circle blue";
        if(typeof( this.commonSwitchFlagForward[commmonSwitch[i]])!='undefined' &&  this.commonSwitchFlagForward[commmonSwitch[i]]){
          classes = "circle blue";
          this.commonSwitchFlagForward[commmonSwitch[i]] = false;
        }else{
          classes = "circle blue hover";
          this.commonSwitchFlagForward[commmonSwitch[i]] = true;
        }
        element.setAttribute("class", classes);
      }
    }else{
      var commmonSwitch = this.flowpathService.getcommonSwitches('reverse');
       for(var i = 0; i < commmonSwitch.length; i++){
        var selectedVal = commmonSwitch[i].split("_");
        var switch_id = (typeof(selectedVal[1]) !='undefined') ? selectedVal[1]: selectedVal;
        var element = document.getElementById("reverseDiverse_circle_" + switch_id);
        var classes = "circle blue";
        if(typeof( this.commonSwitchFlagReverse[commmonSwitch[i]])!='undefined' &&  this.commonSwitchFlagReverse[commmonSwitch[i]]){
          classes = "circle blue";
          this.commonSwitchFlagReverse[commmonSwitch[i]] = false;
        }else{
          classes = "circle blue hover";
          this.commonSwitchFlagReverse[commmonSwitch[i]] = true;
        }
        element.setAttribute("class", classes);
      }
      
    }
  }
  
  showFlowList(type){
    if(type == 'forward'){
        this.showFlowsForward = this.showFlowsForward ? false: true;
    }else{
        this.showFlowsReverse = this.showFlowsReverse ? false : true;
    }
  }
  

 loadDiverseGroup(){
    var self = this;
    var currentFlow = {flowid:this.flowPathData.flowid,flowpath_forward:this.flowPathData.flowpath_forward,flowpath_reverse:this.flowPathData.flowpath_reverse};
    var otherFLows = this.flowPathData && this.flowPathData['diverse_group'] && this.flowPathData['diverse_group']['other_flows'] ? this.flowPathData['diverse_group']['other_flows'] :  null;
    this.hasDiverseGroup = this.flowPathData && this.flowPathData['diverse_group'] && this.flowPathData['diverse_group']['other_flows'];
    if(otherFLows && otherFLows.length){
      otherFLows.push(currentFlow);
      for(let flow in otherFLows){
         var coloCode = this.commonService.getCommonColorCode(flow,self.colourCodes);
          this.colourCodes.push(coloCode);
          if(otherFLows[flow] && otherFLows[flow]['flowpath_forward']){
            var flowid = otherFLows[flow]['flowid'];
            if(this.diversePath && this.diversePath[flowid]){
              this.diversePath[flowid]['forward_path'] = otherFLows[flow]['flowpath_forward'];
            }else{
              this.diversePath[flowid] = {};
              this.diversePath[flowid]['forward_path'] = otherFLows[flow]['flowpath_forward'];
            }            
          }
          if(otherFLows[flow] && otherFLows[flow]['flowpath_reverse']){
            var flowid = otherFLows[flow]['flowid'];
            if(this.diversePath && this.diversePath[flowid]){
              this.diversePath[flowid]['reverse_path'] = otherFLows[flow]['flowpath_reverse'];
            }else{
              this.diversePath[flowid] = {};
              this.diversePath[flowid]['reverse_path'] = otherFLows[flow]['flowpath_reverse'];
            }
            
          }
        }
        // add flows to diverse group
        Object.keys(this.diversePath).map(function(i,v){
          self.diverseGroup.push(i);
        })
        
      }

      this.pathFlows = Object.keys(this.diversePath);
      
  }

  viewDiverseGroup(type){
   if(type == 'forward'){
      this.forwardLabelText = 'FORWARD DIVERSITY';
      this.showDiverseGroupForward = this.showDiverseGroupForward ? false : true;
      this.showDiverseGroupReverse = false;
      this.showForwardPath =  this.showDiverseGroupForward ? false: true;      
      this.forwardPathGraph = false;
      setTimeout(()=>{
        this.plotForwardDiverse();
      });
      
    }else{
      this.reverseLabelText = "REVERSE DIVERSITY";
      this.showDiverseGroupForward = false;
      this.showDiverseGroupReverse = this.showDiverseGroupReverse ? false : true;
      this.showReversePath =  this.showDiverseGroupReverse ? false: true;     
      this.reversePathGraph = false;
      setTimeout(()=>{
        this.plotReverseDiverse();
      })
     
    }
    
  }
  toggleDiversePath(type,e){
    if(type == 'forward'){
      this.showFlowsForward = false;
      this.forwardGraphSvg = false 
      if(e.target.checked){
         this.isDiverseForward = true;
          this.viewDiverseGroup(type);
      }else{
        this.isDiverseForward = false;
       this.viewPath(type);
      }
    }else{
      this.showFlowsReverse = false;
      this.reverseGraphSvg =  false;
      if(e.target.checked){
        this.isDiverseReverse = true;
        this.viewDiverseGroup(type);
      }else{
        this.isDiverseReverse = false;
        this.viewPath(type);
      }
    }
  }
  viewPath(type){
    if(type == 'forward'){
      this.forwardLabelText = "FORWARD PATH";
      this.showFlowsForward = false;
      this.showForwardPath =  true;
      this.showDiverseGroupForward = false;
      this.forwardPathGraph  = false;
      setTimeout(()=>{
        this.plotForwardPath(this.flowPathData);
      })
      
    }else{
      this.reverseLabelText = "REVERSE PATH";
      this.showFlowsReverse = false;
      this.showReversePath =  true;
      this.showDiverseGroupReverse = false;
      this.reversePathGraph  = false;
      setTimeout(()=>{
        this.plotReversePath(this.flowPathData);
      })
      
    }
  }

 
  viewPathGraph(type) {
    
    if (type == "forward") {
      this.isDiverseForward = false;
      this.forwardLabelText = "FORWARD PATH GRAPH";
      this.forwardPathGraph = this.forwardPathGraph ? false : true;
      this.showForwardPath =  this.forwardPathGraph ? false: true; 
      this.showDiverseGroupForward = this.isDiverseForward;
      this.forwardGraphSvg = this.forwardGraphSvg ? false : true;
      if(!this.forwardPathGraph && this.isDiverseForward){
        this.forwardLabelText = "FORWARD DIVERSITY";
      }else if(!this.forwardPathGraph){
        this.forwardLabelText = "FORWARD PATH";
        this.viewPath('forward');
      }
      
    } else {
      this.isDiverseReverse = false;
      this.reverseLabelText = "REVERSE PATH GRAPH";
      this.reversePathGraph = this.reversePathGraph ? false : true;
      this.showReversePath =  this.reversePathGraph ? false: true;   
      this.showDiverseGroupReverse = this.isDiverseReverse;
      this.reverseGraphSvg = this.reverseGraphSvg ? false : true;
      if(!this.reversePathGraph && this.isDiverseReverse){
        this.reverseLabelText = "REVERSE DIVERSITY";
      }else if(!this.reversePathGraph){
        this.reverseLabelText = "REVERSE PATH";
        this.viewPath('reverse');
      }
    }
  }

  

  ngOnDestroy(){
    this.reversePathGraph = false;
    this.forwardPathGraph = false;
    this.showDiverseGroupForward = false;
    this.showDiverseGroupReverse = false;
  }
}
