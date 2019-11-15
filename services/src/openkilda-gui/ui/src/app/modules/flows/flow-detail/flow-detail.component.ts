import { Component, OnInit, HostListener, AfterViewInit, Renderer2 } from "@angular/core";
import { FlowsService } from "../../../common/services/flows.service";
import { Router, ActivatedRoute } from "@angular/router";
import { ToastrService } from "ngx-toastr";
import { ISL } from "../../../common/enums/isl.enum";
import { SwitchidmaskPipe } from "../../../common/pipes/switchidmask.pipe";
import { ClipboardService } from "ngx-clipboard";
import { LoaderService } from "../../../common/services/loader.service";
import { Title } from '@angular/platform-browser';
import { CommonService } from "../../../common/services/common.service";
import { Location } from "@angular/common";
import * as d3 from "d3";
import { environment } from "../../../../environments/environment";
import { StoreSettingtService } from "src/app/common/services/store-setting.service";
declare var jQuery: any;
 
@Component({
  selector: "app-flow-detail",
  templateUrl: "./flow-detail.component.html",
  styleUrls: ["./flow-detail.component.css"]
})

export class FlowDetailComponent implements OnInit {
  openedTab = "graph";
  flowDetail: any;
  controllerFilter:boolean=false;
  graphOptions = {
    radius: 35,
    text_center: false,
    nominal_text_size: 10,
    nominal_base_node_size: 40,
    nominal_stroke: 1.5,
    max_stroke: 4.5,
    max_base_node_size: 36,
    max_text_size: 24
  };
  width:number;
  height:number;
  g: any;
  drag: any;
  zoom:any;
  nodes:any;
  links:any;
  graphNode:any;
  graphLink:any;
  graphNodeGroup:any;
  graphLinkGroup:any;
  svgElement: any;
  forceSimulation:any;
  isDragMove:true;
  size:any;
  min_zoom = 0.15;
  max_zoom = 3;
  zoomLevel = 0.15;
  zoomStep = 0.15;
  translateX = 0;
  translateY = 0;
  validatedFlow: any = [];
  resyncedFlow : any = [];
  pingedFlow : any = [];
  flowIs = '';
  contracts : any = [];
  loading = false;
  isLoadedcontract = false;
  clipBoardItems = {
    flowName: "",
    sourceSwitchName:"",
    sourceSwitch:"",
    targetSwitchName:"",
    targetSwitch:"",
    validateFlow:"",
    resyncFlow : "",
    pingedFlow:""
  }

  storeLinkSetting = false;
  statusDescrepancy = false;
  bandWidthDescrepancy = false;
  loadStatsGraph = false;
  sourceCheckedValue = false;
  targetCheckedValue = false;
  descrepancyData = {
    status:{
      controller: "-",
      inventory:"-"
    },
    bandwidth:{
      controller: "-",
      inventory:"-"
    }
  }

  property = 'GraphicalView';
  Rawview = false ; 
  GraphicalView = true;
  loadingPing = false;
  public reRoutingInProgress = false;

  constructor(
    private flowService: FlowsService,
    private router: Router,
    private route: ActivatedRoute,
    private toaster: ToastrService,
    private maskPipe: SwitchidmaskPipe,
    private loaderService: LoaderService,
    private clipboardService: ClipboardService,
    private titleService: Title,
    private commonService: CommonService,
    private _location:Location,
    private _render:Renderer2,
    private storeLinkService:StoreSettingtService,
    ) {
    let storeSetting = localStorage.getItem("haslinkStoreSetting") || false;
    this.storeLinkSetting = storeSetting && storeSetting == "1" ? true : false
    
  }
  ngOnInit() {
    this.titleService.setTitle("OPEN KILDA - View Flow")
    this.route.params.subscribe(params => {
      this.loadStatsGraph = false;
      var filterFlag = localStorage.getItem("filterFlag") || 'controller';
      this.controllerFilter = filterFlag == 'controller';
      if(!localStorage.getItem("haslinkStoreSetting")){
        let query = {_:new Date().getTime()};
        this.storeLinkService.getLinkStoreDetails(query).subscribe((settings)=>{
          if(settings && settings['urls'] && typeof(settings['urls']['get-link']) !='undefined' &&  typeof(settings['urls']['get-link']['url'])!='undefined'){
            localStorage.setItem('linkStoreSetting',JSON.stringify(settings));
            localStorage.setItem('haslinkStoreSetting',"1");
            this.storeLinkSetting = true;
            this.getFlowDetail(params['id'],filterFlag);
          }else{
            this.getFlowDetail(params['id'],filterFlag);
          }
        },(err)=>{
          this.getFlowDetail(params['id'],filterFlag);
        });
      }else{
        this.getFlowDetail(params['id'],filterFlag);
      }
      
      this.sourceCheckedValue = false;
      this.targetCheckedValue = false;
   });
    
  }
  
  openTab(tab) {
    this.openedTab = tab;
    $('#pingGraph').html("");
    if(tab == 'contracts'){
      this.loaderService.show('Loading Contracts..');
      this.flowService.getcontract(this.flowDetail.flowid).subscribe(data=>{
        this.contracts  = data || [];
        this.isLoadedcontract = true;
        this.loaderService.hide();
      },(err)=>{
        this.isLoadedcontract = true;
        this.loaderService.hide();
           var Err = err.error;
           var msg  = (Err && typeof(Err['error-auxiliary-message'])!='undefined') ? Err['error-auxiliary-message']:'';
          this.toaster.error(msg,"Error");
      })  
    }else if(tab =='ping'){
      this.pingedFlow = null;
      this.GraphicalView = true;
      this.Rawview = false;
      this.property = 'GraphicalView';
      this.initPingSimulation();
    }
  }

  dragStart = () => {
    if (!d3.event.active) this.forceSimulation.alphaTarget(1).stop();
  };

  dragging = (d: any, i) => {
    this.isDragMove = true;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy;
    this.tick();
  };

  dragEnd = (d: any, i) => {
    if (!d3.event.active) this.forceSimulation.alphaTarget(0);
  };

  horizontallyBound = (parentDiv, childDiv) => {
    let parentRect: any = parentDiv.getBoundingClientRect();
    let childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  };
 
  initPingSimulation(){
    this.nodes = [{ "x": -208.992345, "y": -6556.9998 ,switch_id_value:this.flowDetail.source_switch,switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name,port:this.flowDetail.src_port,vlan:this.flowDetail.src_vlan},
                  { "x": 595.98896,  "y":  -6556.9998,switch_id_value:this.flowDetail.target_switch,switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name,port:this.flowDetail.dst_port,vlan:this.flowDetail.dst_vlan }
                ];
    this.links = [{
                source:{switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name},
                target:{switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name}
               },
                {
                source:{switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name},
                target:{switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name}
               }
            ];
    this.processLinks();
    this.svgElement = d3.select("svg");
    this.width = this.svgElement.attr('width');
    this.height = this.svgElement.attr('height');
    this.svgElement.style('cursor','move');
    this.svgElement.attr("width",this.width);
    this.svgElement.attr("height",this.height);
    this.g = this.svgElement.append("g");
    this.graphLinkGroup = this.g.append("g").attr("id", `links`).attr("class", "links");
    this.graphNodeGroup = this.g.append('g').attr("class",".nodes").attr("id","nodes");
      this.size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));
    this.forceSimulation = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
        return 20;
      }))
      .force("charge_force",d3.forceManyBody().strength(-50000))
      .force("xPos", d3.forceX(this.width /2))
      .force("yPos", d3.forceY(this.height / 2));
    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force("link", d3.forceLink().links(this.links).distance((d:any)=>{
      let distance = 150;
        return distance; 
     }).strength(0.1));
     this.forceSimulation.on("tick", () => {  
      this.tick();
     });
    this.insertNodes();
    this.insertLinks();     
    this.svgElement.on("dblclick.zoom", null);
    this.forceSimulation.restart();
      
  }
  processLinks(){
    var nodelength = this.nodes.length;
    var linklength = this.links.length;
    for (var i = 0; i < nodelength; i++) {
     for (var j = 0; j < linklength; j++) { 
       if (
         this.nodes[i].switch_id == this.links[j]["source"]["switch_id"] &&
         this.nodes[i].switch_id == this.links[j]["target"]["switch_id"]
       ) { 
          this.links[j].source = i;
          this.links[j].target = i;
       } else {
         if (this.nodes[i].switch_id == this.links[j]["source"]["switch_id"]) { 
           this.links[j].source = i;
           } else if (
             this.nodes[i].switch_id == this.links[j]["target"]["switch_id"]
           ) { 
             this.links[j].target = i;
           }
       }
     }
   }
  }
  insertLinks(){
    var ref =this;
    let graphLinksData = this.graphLinkGroup.selectAll("path.link").data(this.links);
     let graphNewLink = graphLinksData
      .enter()
      .append("path")
      .attr("class", function(d, index) {
        return "link physical";
      })
      .attr("id", (d, index) => {
        return "link" + index;
      }).attr('stroke-width', (d) =>{ return 2.5; }).attr("stroke", function(d, index) {
              return ISL.DISCOVERED;
      }).attr("cursor","pointer")
      .on('mouseover',function(d,index){
        var element = document.getElementById("link" + index);
        var classes = element.getAttribute("class");
        classes = classes + " overlay";
        element.setAttribute('class',classes);
         var rec: any = element.getBoundingClientRect();
        
         if(classes.includes("failed_ping_flowline") || classes.includes("ping_success_flow")){
           if(index ==0){
            $("#ping-hover-txt").css("display", "block");
            $('#forward_ping_errors').css('display','block');
            $('#reverse_ping_errors').css('display','none');
           }else{
            $("#ping-hover-txt").css("display", "block");
            $('#forward_ping_errors').css('display','none');
            $('#reverse_ping_errors').css('display','block');
           }

           $(element).on("mousemove", function(e) {
            $("#ping-hover-txt").css("top", (e.pageY-50) + "px");
            $("#ping-hover-txt").css("left", (e.pageX) + "px");
            var bound = ref.horizontallyBound(
              document.getElementById("pingGraphwrapper"),
              document.getElementById("ping-hover-txt")
            );

            if (bound) {
              $("#ping-hover-txt").removeClass("left");
            } else {
              var left = e.pageX; // subtract width of tooltip box + circle radius
              $("#ping-hover-txt").css("left", left + "px");
              $("#ping-hover-txt").addClass("left");
            }
          });
          
         }
      }).on('mouseout',function(d,index){
        var element = document.getElementById("link" + index);
        $('#link' + index).removeClass('overlay');
        $("#ping-hover-txt").css("display", "none");
        $('#forward_ping_errors').css('display','none');
        $('#reverse_ping_errors').css('display','none');
      });
      graphLinksData.exit().remove();
      this.graphLink = graphNewLink.merge(graphLinksData);
  }
  insertNodes(){
    let ref = this;
    let graphNodesData = this.graphNodeGroup.selectAll("g.node").data(this.nodes);
    let graphNodeElement = graphNodesData.enter().append("g").attr("class", "node");
    
    graphNodesData.exit().remove();
    graphNodeElement.append("circle").attr("r", this.graphOptions.radius)
                      .attr("class", function(d, index) {
                        var classes = "circle blue hover";
                        return classes;
                      })
                      .attr("id", function(d, index) {
                        return "circle_" + d.index;
                      }).style("cursor", "pointer");
                      
   let text = graphNodeElement
                      .append("text")
                      .attr("dy", ".35em")
                      .style("font-size", this.graphOptions.nominal_text_size + "px")
                      .attr("class", "switchname");
  if (this.graphOptions.text_center) {
    text
      .text(function(d) { 
        return d.name;
      })
      .style("text-anchor", "middle");
  } else {
    text
      .attr("dx", function(d) {
        return ref.size(d.size) || ref.graphOptions.nominal_base_node_size;
      })
      .text(function(d) {
        return d.name;
      });
  }
  let images = graphNodeElement.append("svg:image")
                                .attr("xlink:href", function(d) {
                                  return environment.assetsPath + "/images/switch.png";
                                })
                                .attr("x", function(d) {
                                  return -29;
                                })
                                .attr("y", function(d) {
                                  return -29;
                                })
                                .attr("height", 58)
                                .attr("width", 58)
                                .attr("id", function(d, index) {
                                  return "image_" + index;
                                }).attr("cursor","pointer").on('mouseover',function(d,index){
                                  var element = document.getElementById("circle_" + index);
                                    var rec: any = element.getBoundingClientRect();
                                    $("#ping-hover-txt,#switch_hover").css("display", "block");
                                    $("#ping-hover-txt").css("top", rec.y + "px");
                                    $("#ping-hover-txt").css("left", (rec.x) + "px");
          
                                    d3.select(".switchdetails_div_switch_name").html(
                                      "<span>" + d.name + "</span>"
                                    );
                                    d3.select(".switchdetails_div_switchid").html(
                                      "<span>" + d.switch_id_value + "</span>"
                                    );
                                    d3.select(".switchdetails_div_port").html(
                                      "<span>" + d.port + "</span>"
                                    );
                                    d3.select(".switchdetails_div_vlan").html(
                                      "<span>" + d.vlan + "</span>"
                                    );
                                    
                                    var bound = ref.horizontallyBound(
                                      document.getElementById("pingGraphwrapper"),
                                      document.getElementById("ping-hover-txt")
                                    );
                                    if (bound) {
                                      $("#ping-hover-txt").removeClass("left");
                                    } else {
                                      var left = rec.x - (300 + 100); 
                                      $("#ping-hover-txt").css("left", left + "px");
                                      $("#ping-hover-txt").addClass("left");
                                    }
                                }).on('mouseout',function(d,index){
                                  $("#ping-hover-txt,#switch_hover").css("display", "none");
                                });
      
     this.graphNode = graphNodeElement.merge(graphNodesData);
                        
  }
  tick(){
    this.graphLink.attr("d", d => {
      var x1 = d.source.x,
        y1 = d.source.y,
        x2 = d.target.x,
        y2 = d.target.y,
        dx = x2 - x1,
        dy = y2 - y1,
        dr = Math.sqrt(dx * dx + dy * dy),
        drx = dr,
        dry = dr - 100,
        xRotation = 0, // degrees
        largeArc = 0, // 1 or 0
        sweep = 1; // 1 or 0
        var lTotalLinkNum = 2;
        if (lTotalLinkNum > 1) {
          dr = dr / (1 + (1 / lTotalLinkNum) * (d.index));
        }
         if (x1 === x2 && y1 === y2) {
            xRotation = -45;
            largeArc = 1;
            drx = 50;
            dry = 20;
            x2 = x2 + 1;
            y2 = y2 + 1;
          }

          return (
            "M" +
            x1 +
            "," +
            y1 +
            "A" +
            drx +
            "," +
            dry +
            " " +
            xRotation +
            "," +
            largeArc +
            "," +
            sweep +
            " " +
            x2 +
            "," +
            y2
          );
    });
     this.graphNode.attr("transform", function(d) {
        if (d.x && d.y) {
          return "translate(" + d.x + "," + d.y + ")";
        }
      });
  }
  /**fetching flow detail via API call */
  getFlowDetail(flowId,filterFlag) {
    this.openedTab = 'graph';
    this.loadStatsGraph = true;
    this.clearResyncedFlow();
    this.clearValidatedFlow();
    this.loaderService.show("Loading Flow Detail");
    this.bandWidthDescrepancy  = false;
    this.statusDescrepancy = false;
    var flowDetail = null;
    if(filterFlag == 'controller'){
      let flowData  = JSON.parse(localStorage.getItem('flows')) || {};
      let flowList = typeof(flowData.list_data) != 'undefined' ? flowData.list_data: [];
      if(flowList && flowList.length){
        flowList.forEach(element => { 
         if(element.flowid == flowId){
           flowDetail = element;
           return;
         }
        });
      }
    }else{
      var flowData = JSON.parse(localStorage.getItem('flowsinventory')) || {};
      let flowList = typeof(flowData.list_data) != 'undefined' ? flowData.list_data: [];
      if(flowList && flowList.length){
        flowList.forEach(element => { 
         if(element.flowid == flowId){
           flowDetail = element;
           return;
         }
        });
    }
  }
  if(flowDetail && flowDetail.flowid){
       flowDetail["source_switch"] = this.convertSwitchPattern(flowDetail["source_switch"]);
        flowDetail["target_switch"] = this.convertSwitchPattern(flowDetail["target_switch"]);
        this.flowDetail = flowDetail;
        this.clipBoardItems = Object.assign(this.clipBoardItems,{
          flowName: flowDetail.flowid,
          sourceSwitchName: flowDetail["source_switch_name"],
          sourceSwitch: flowDetail["source_switch"],
          targetSwitchName: flowDetail["target_switch_name"],
          targetSwitch: flowDetail["target_switch"]
        });

        if(flowDetail['discrepancy'] && (flowDetail['discrepancy']['status'] || flowDetail['discrepancy']['bandwidth'])){
          if(flowDetail['discrepancy']['status']){
            this.statusDescrepancy  = true;
            this.descrepancyData.status.controller = (typeof(flowDetail['discrepancy']['status-value']['controller-status'])!='undefined') ?  flowDetail['discrepancy']['status-value']['controller-status'] : "-";
            this.descrepancyData.status.inventory = (typeof(flowDetail['discrepancy']['status-value']['inventory-status'])!='undefined') ?  flowDetail['discrepancy']['status-value']['inventory-status'] : "-";
          }
          if(flowDetail['discrepancy']['bandwidth']){
            this.bandWidthDescrepancy = true;
            this.descrepancyData.bandwidth.controller = (typeof(flowDetail['discrepancy']['bandwidth-value']['controller-bandwidth'])!='undefined') ?  flowDetail['discrepancy']['bandwidth-value']['controller-bandwidth'] : "-";
            this.descrepancyData.bandwidth.inventory = (typeof(flowDetail['discrepancy']['bandwidth-value']['inventory-bandwidth'])!='undefined') ?  flowDetail['discrepancy']['bandwidth-value']['inventory-bandwidth'] : "-";
          }
        }
        
        this.loaderService.hide();
  }else{
    this.flowService.getFlowDetailById(flowId,filterFlag).subscribe(
      flow => {
        flow["source_switch"] = this.convertSwitchPattern(flow["source_switch"]);
        flow["target_switch"] = this.convertSwitchPattern(flow["target_switch"]);
        this.flowDetail = flow;
        this.clipBoardItems = Object.assign(this.clipBoardItems,{
          flowName: flow.flowid,
          sourceSwitchName: flow["source_switch_name"],
          sourceSwitch: flow["source_switch"],
          targetSwitchName: flow["target_switch_name"],
          targetSwitch: flow["target_switch"]
        });

        if(flow['discrepancy'] && (flow['discrepancy']['status'] || flow['discrepancy']['bandwidth'])){
          if(flow['discrepancy']['status']){
            this.statusDescrepancy  = true;
            this.descrepancyData.status.controller = (typeof(flow['discrepancy']['status-value']['controller-status'])!='undefined') ?  flow['discrepancy']['status-value']['controller-status'] : "-";
            this.descrepancyData.status.inventory = (typeof(flow['discrepancy']['status-value']['inventory-status'])!='undefined') ?  flow['discrepancy']['status-value']['inventory-status'] : "-";
          }
          if(flow['discrepancy']['bandwidth']){
            this.bandWidthDescrepancy = true;
            this.descrepancyData.bandwidth.controller = (typeof(flow['discrepancy']['bandwidth-value']['controller-bandwidth'])!='undefined') ?  flow['discrepancy']['bandwidth-value']['controller-bandwidth'] : "-";
            this.descrepancyData.bandwidth.inventory = (typeof(flow['discrepancy']['bandwidth-value']['inventory-bandwidth'])!='undefined') ?  flow['discrepancy']['bandwidth-value']['inventory-bandwidth'] : "-";
          }
        }
        
        this.loaderService.hide();
      },
      error => {
        var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No Flow found';
        this.toaster.error(errorMsg, "Error");
        this._location.back();
        this.loaderService.hide();
      }
    );
  }
   
  }

  convertSwitchPattern(switchId){
    if(switchId){
      if(switchId.startsWith("SW") || switchId.startsWith("sw")){
        switchId = switchId.substring(2);
        if(!switchId.includes(":")){
          return this.maskPipe.addCharacter(switchId,2).join(":").toLowerCase();
        }else{
          return switchId;
        }
      }else{
        return switchId;
      }
    }

  }
  /** Switch Id masking using toggle button */
  maskSwitchId(switchType, e) {
    if (e.target.checked) {
      this.flowDetail[switchType+"_switch"] = this.maskPipe.transform(
        this.flowDetail[switchType+"_switch"],
        "legacy"
      );
    } else {
      this.flowDetail[switchType+"_switch"] = this.maskPipe.transform(
        this.flowDetail[switchType+"_switch"],
        "kilda"
      );
    }

    if(switchType == 'source'){
      this.clipBoardItems.sourceSwitch = this.flowDetail[switchType+"_switch"];
      this.sourceCheckedValue = e.target.checked ? true : false;
    }else{
      this.clipBoardItems.targetSwitch = this.flowDetail[switchType+"_switch"];
      this.targetCheckedValue = e.target.checked ? true : false;
    }
  }
  toggleGraphView(e){
    if (e.target.checked) {
      this.property = 'Rawview';
      this.GraphicalView = false ;
    } else {  
      this.property = 'GraphicalView';   
      this.Rawview = false;  
   }
    this[this.property] = true;
  }
  /** Validate flow */
  validateFlow() {
    this.validatedFlow = null;
    this.flowIs  ='validate';
    this.loading = true;
    this.flowService.validateFlow(this.flowDetail.flowid).subscribe(
      data => {
        this.validatedFlow = data;
        this.clipBoardItems.validateFlow = data;
        this.loading = false;
      },
      error => {
        this.flowIs  ='';
        this.loading = false;
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
  }

  /** Validate flow */
  resyncFlow() {
    this.resyncedFlow = null;
    this.flowIs  ='resync';
    this.loading = true;
    this.flowService.resynchFlow(this.flowDetail.flowid).subscribe(
      data => {
        this.resyncedFlow = data;
        this.clipBoardItems.resyncFlow = data;
        this.loading = false;
        /** Re-load flow status after resync */
        setTimeout(() => {
          this.flowService.getFlowStatus(this.flowDetail.flowid).subscribe(
            flowStatus =>{
               this.flowDetail.status = (flowStatus && flowStatus.status) ?  flowStatus.status : this.flowDetail.status;
            },
            error => {
              var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No Flow found';
              //this.toaster.error(errorMsg, "Error");
             }
          )
        }, 10000);
      },
      error => {
        this.flowIs  ='';
        this.loading = false;
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
  }

  /** Re-route flow path for best route  */
  reRouteFlow() {
    this.reRoutingInProgress = true;
    this.loaderService.show("Re-routing");
    this.flowService.getReRoutedPath(this.flowDetail.flowid).subscribe(
      data => {
        this.loaderService.hide();
        if(data && typeof(data.rerouted)!=='undefined' && data.rerouted){
          this.toaster.success('Flow : '+this.flowDetail.flowid+" successfully re-routed!","success");
        } else {
          this.toaster.info('Flow : '+this.flowDetail.flowid+" already on best route!");
        }
        this.loaderService.show('Reloading status and flow path after re-route..');
        /** Re-load flow path components */
        setTimeout(() => {
          this.reRoutingInProgress = false;
          this.loaderService.hide();
          this.flowService.getFlowStatus(this.flowDetail.flowid).subscribe(
            flowStatus =>{
              this.flowDetail.status = (flowStatus && flowStatus.status) ?  flowStatus.status : this.flowDetail.status;
            },
            error => {
              var errorMsg = error && error.error && error.error['error-auxiliary-message'] ? error.error['error-auxiliary-message']: 'No Flow found';
              //this.toaster.error(errorMsg, "Error");
             }
          )
        }, 10000);
      },
      error => {
        this.loaderService.hide();
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
    
  }
  addPingToLinks(){
    this.links.forEach(function(d,index){
      $('#link'+index).addClass('flowline');
    })
    
  }

  removePingFromLinks(forward_ping,reverse_ping){
    this.links.forEach(function(d,index){
      if(index == 0){
        if(!forward_ping){
          $('#link'+index).removeClass('flowline').addClass('failed_ping_flowline');
        }else{
          $('#link'+index).removeClass('flowline').removeClass('failed_ping_flowline').addClass('ping_success_flow');
        }
      }else if(index !=0){
        if(!reverse_ping){
          $('#link'+index).removeClass('flowline').addClass('failed_ping_flowline');
        }else{
          $('#link'+index).removeClass('flowline').removeClass('failed_ping_flowline').addClass('ping_success_flow');
        }
      }
      
    })
  }
  /** Ping flow */
  pingFlow() {   
    this.addPingToLinks();
    this.pingedFlow = null;
    this.flowIs  ='ping';
    this.loadingPing = true;
    this.flowService.pingFlow(this.flowDetail.flowid).subscribe(
      data => {
        var forward_ping = (data && data['forward'] && data['forward']['ping_success']) ?data['forward']['ping_success'] : false;
        var reverse_ping = (data && data['reverse'] && data['reverse']['ping_success']) ?data['reverse']['ping_success'] : false;
        if(!forward_ping){
          $('#forward_ping_errors').html('<p>'+data['forward']['error']+'</p>');
        }else{
          $('#forward_ping_errors').html('<p> Latency: '+data['forward']['latency']+'</p>');
        }
        if(!reverse_ping){
          $('#reverse_ping_errors').html('<p>'+data['reverse']['error']+'</p>');
        }else{
          $('#reverse_ping_errors').html('<p> Latency: '+data['reverse']['latency']+'</p>');
        }
        this.removePingFromLinks(forward_ping,reverse_ping);
        this.pingedFlow = data;
        this.clipBoardItems.pingedFlow = JSON.stringify(data);
        this.loadingPing = false;
        if(this.property == "Rawview"){
          setTimeout(function(){ $('#onoffflowping').trigger('click')});
        }
      },
      error => {
        var forward_ping = false,reverse_ping = false;
        this.removePingFromLinks(forward_ping,reverse_ping);
        this.flowIs  ='';
        this.loadingPing = false;
        this.toaster.error(error["error-auxiliary-message"], "Error!");
      }
    );
  }

  showMenu(e) {
    e.preventDefault();
    $(".clip-board-button").hide();
    $(".clip-board-button")
      .css({
        top: e.pageY + "px",
        left: e.pageX - 220 + "px",
        "z-index": 2
      })
      .toggle();
  }

  copyToClip(event, copyItem) {
    this.clipboardService.copyFromContent(this.clipBoardItems[copyItem]);
  }

  copyToClipHtml(event, copyHtmlItem){
    this.clipboardService.copyFromContent(jQuery('.code').text());
  }

  clearResyncedFlow(){
    this.resyncedFlow = [];
    this.flowIs ="";
  }

  clearValidatedFlow(){
    this.validatedFlow = [];
    this.flowIs ="";
  }

  clearPingedFlow(){
    this.pingedFlow = [];
    this.flowIs = "";
  }

}
