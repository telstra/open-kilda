import { Component,  OnInit, Input, AfterViewInit, OnDestroy } from '@angular/core';
import { CommonService } from 'src/app/common/services/common.service';
import * as d3 from 'd3';
import { environment } from "../../../../environments/environment";

@Component({
  selector: 'app-flow-diverse-path',
  templateUrl: './flow-diverse-path.component.html',
  styleUrls: ['./flow-diverse-path.component.css']
})
export class FlowDiversePathComponent implements OnInit, AfterViewInit, OnDestroy {

  @Input() data?: any;
  @Input("path-type") type?: string;
  @Input("selected-flow") selectedFlow?:string;
  @Input("colourCodes") colourCodes:any;
  pathData = [] ;
  pathFlows = [];
  loadForwardGraph:boolean = false;
  pathGraphLoader :boolean = false;
  diverseUniqueSwitches = [];
  diverseGroupCommonSwitch = [];
  nodes:any = [];
  links:any = [];
  linksSourceArr = [];
  loadpathGraph : boolean = false; 
  pathLoader : boolean = false;
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
  graphWrapper:string='';
  width:number;
  height:number;
  g: any;
  drag: any;
  zoom:any;
  graphNode:any;
  graphLink:any;
  graphPortCount: any;
  graphNodeGroup:any;
  graphLinkGroup:any;  
  svgElement: any;
  forceSimulation:any;
  isDragMove:true;
  flowPathFlag = {};
  commonSwitchFlag = {};
  size:any;
  min_zoom = 0.15;
  max_zoom = 3;
  zoomLevel = 0.45;
  zoomStep = 0.15;
  translateX = 0;
  translateY = 0;
  positions : any = {};
  constructor(
    private commonService:CommonService,
    ) { }

  ngOnInit() {
    let self = this;
    var commonSwitches =[];
    var links = [];
      this.pathFlows = Object.keys(this.data);
      Object.keys(this.data).map(function(i,v){
        var j = 0;
        for(let d of self.data[i].forward_path){
        if(j < self.data[i].forward_path.length){  j++;
            var inPort = "sw_"+d.switch_id + "_"+ d.input_port;
            var outPort = "sw_"+d.switch_id + "_"+ d.output_port;
            commonSwitches.push(inPort);
            commonSwitches.push(d.switch_id);
            commonSwitches.push(outPort);
            links.push({flow:i,source:{switch_id:inPort,switch_name:inPort},target:d,colourCode:self.colourCodes[v],type:'port_isl'});
            links.push({flow:i,source:d,target:{switch_id:outPort,switch_name:outPort},colourCode:self.colourCodes[v],type:'port_isl'});
            if(typeof(self.data[i].forward_path[j]) !='undefined'){
              var nextSwitchInPort = "sw_"+self.data[i].forward_path[j].switch_id+"_"+self.data[i].forward_path[j].input_port;
              links.push({flow:i,source:{switch_id:outPort,switch_name:outPort},target:{switch_id:nextSwitchInPort,switch_name:nextSwitchInPort},colourCode:self.colourCodes[v],type:'isl'});
            }
          }
        }
      });
      self.links = links;
    // fetching unique switches in all diverse group
    if(commonSwitches && commonSwitches.length){
        for(let switchid of commonSwitches){
          if(this.diverseUniqueSwitches.indexOf(switchid)==-1){
            this.diverseUniqueSwitches.push(switchid);
          }else{
            this.diverseGroupCommonSwitch.push(switchid);
          }
        }
      }
      // creating nodes object array
     for(let d of this.diverseUniqueSwitches){
       this.nodes.push({switch_id:d});
     } 
     this.loadForwardGraph = true;
     this.pathGraphLoader = true;
     var svgElement = d3.select('#svgForward');
     // creating positions for nodes
     var element =$('#ForwardPathGraphwrapper');
     var yValue =  element[0].clientTop;
     var xValue = element[0].clientLeft; 
     var maxLimit = element[0].clientWidth;
     var reverseXvalue = element[0].clientWidth;
     var reverseYvalue = element[0].clientTop + 200;
     for(var i = 0; i < this.nodes.length; i++){
      this.positions[this.nodes[i].switch_id] = [];
      var arr = this.nodes[i].switch_id.split("_");
      var arr1 = [];
      if(i > 0){
         arr1 = this.nodes[i-1].switch_id.split("_");
      }
      var isSwitch = false;
      if(typeof(arr[1]) == 'undefined'){
        isSwitch = true;
      }
      var isIslPort = false;
      if(typeof(arr[1])!='undefined' && typeof(arr1[1])!='undefined'){
        isIslPort = true;
      }
      if(xValue < maxLimit){
        this.positions[this.nodes[i].switch_id][0] = xValue ;
        this.positions[this.nodes[i].switch_id][1] = yValue;
        if(isSwitch){
          this.positions[this.nodes[i].switch_id][1] = this.positions[this.nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          xValue = xValue + 150;
          this.positions[this.nodes[i].switch_id][0] = xValue;
        }
        xValue = xValue + 80;
      }else if(xValue >= maxLimit && reverseXvalue > 100){
         this.positions[this.nodes[i].switch_id][0] = reverseXvalue;
         this.positions[this.nodes[i].switch_id][1] = reverseYvalue;
         if(isSwitch){
          this.positions[this.nodes[i].switch_id][1] = this.positions[this.nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          reverseXvalue = reverseXvalue - 150;
          this.positions[this.nodes[i].switch_id][0] = reverseXvalue;
        }
         reverseXvalue = reverseXvalue - 80;
      }else{
         yValue =  reverseYvalue + 100;
         xValue = element[0].clientLeft; 
         maxLimit = element[0].clientWidth;
         reverseXvalue = element[0].clientWidth;
         reverseYvalue = yValue + 200;
         this.positions[this.nodes[i].switch_id][0] = xValue ;
         this.positions[this.nodes[i].switch_id][1] = yValue;
         if(isSwitch){
          this.positions[this.nodes[i].switch_id][1] = this.positions[this.nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          xValue = xValue + 150;
          this.positions[this.nodes[i].switch_id][0] = xValue;
        }
        
      }
    } 
     this.initSimulation(this.nodes,this.links,svgElement,"ForwardPathGraphwrapper",'forward');
  }

  showCommonSwitch(){
    
    var commmonSwitch = this.diverseGroupCommonSwitch;
    for(var i = 0; i < commmonSwitch.length; i++){
      var selectedVal = commmonSwitch[i].split("_");
      var switch_id = (typeof(selectedVal[1]) !='undefined') ? selectedVal[1]: selectedVal;
      var node = this.svgElement.selectAll(".node");
      var unmatched = node.filter(function(d, i) {
        var arr = d.switch_id.split("_");
        var switch_id_node = (typeof(arr[1]) == 'undefined') ? d.switch_id:d.index;
        return switch_id_node != switch_id;
      });
      var element = document.getElementById("forward_circle_" + switch_id);
      var classes = "circle blue";
      if(typeof( this.commonSwitchFlag[commmonSwitch[i]])!='undefined' &&  this.commonSwitchFlag[commmonSwitch[i]]){
        classes = "circle blue";
        this.commonSwitchFlag[commmonSwitch[i]] = false;
      }else{
        classes = "circle blue hover";
        this.commonSwitchFlag[commmonSwitch[i]] = true;
      }
      element.setAttribute("class", classes);
    }
    
  }

  showFlowPth(flowid){
    var flows = Object.keys(this.flowPathFlag);
    if(typeof(this.flowPathFlag[flowid]) !='undefined' && this.flowPathFlag[flowid]){
      d3.selectAll(".link_"+flowid)
      .transition()
      .style("stroke-width", "2.5");
      this.flowPathFlag[flowid] = false;
    }else{
      if(flows && flows.length){
        flows.map((i,v)=>{
          if(this.flowPathFlag[i]){
              d3.selectAll(".link_"+i)
              .transition()
              .style("stroke-width", "2.5");
              this.flowPathFlag[i] = false;
          }
          
        });
      }
      var links = this.svgElement.selectAll(".link_"+flowid);
      links.style("stroke-width", "5");
      this.flowPathFlag[flowid] = true;
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

  initSimulation(nodes,links,svgElement,graphWrapper,type){
    var self = this;
    this.graphWrapper = graphWrapper;
    this.type=type;
    this.links = links;
    this.nodes=nodes;
    if (this.links.length > 0) {
      try {
        var result = this.commonService.groupBy(this.links, function(item) {
          return [item.source, item.target];
        });
        for (var i = 0, len = result.length; i < len; i++) {
          var row = result[i];
          if (row.length >= 1) {
            for (var j = 0, len1 = row.length; j < len1; j++) {
              var key = row[j].source.switch_id+ "_" + row[j].target.switch_id;
              var key1 = row[j].target.switch_id + "_" + row[j].source.switch_id;
              var prcessKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== "undefined") ? key:key1;
              if (typeof this.linksSourceArr[prcessKey] !== "undefined") {
                this.linksSourceArr[prcessKey].push(row[j]);
              } else {
                this.linksSourceArr[key] = [];
                this.linksSourceArr[key].push(row[j]);
              }
            }
          }
        }
        
      } catch (e) {}
    }
      this.processLinks();
      this.svgElement = svgElement;
      this.width = window.innerWidth;
      this.height = this.svgElement.attr('height');
      this.svgElement.style('cursor','move');
      this.svgElement.attr("width",this.width);
      this.svgElement.attr("height",this.height);
      this.g = this.svgElement.append("g");
      this.graphLinkGroup = this.g.append("g").attr("id", `links`).attr("class", "links");
      this.graphNodeGroup = this.g.append('g').attr("class",".nodes").attr("id","nodes");
      this.zoom = d3
      .zoom()
      .scaleExtent([this.min_zoom, this.max_zoom])
      .extent([[0, 0], [this.width, this.height]])
      .on("zoom", () => {
        this.g.attr(
          "transform",
          "translate(" +
            d3.event.transform.x +
            "," +
            d3.event.transform.y +
            ") scale(" +
            d3.event.transform.k +
            ")"
        );
        this.zoomLevel = Math.round(d3.event.transform.k*100)/100;
        this.translateX = d3.event.transform.x;
        this.translateY = d3.event.transform.y;
        this.isDragMove = true;
      });
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
      .force("charge_force",d3.forceManyBody().strength(-1000))
      .force("xPos", d3.forceX(this.width /2))
      .force("yPos", d3.forceY(this.height / 2));
      this.forceSimulation.nodes(this.nodes);
      this.forceSimulation.force("link", d3.forceLink().links(this.links).distance((d:any)=>{
         let distance = 20;
        if(d.type=='isl'){
          distance = 150;
        }
      return distance; 
      }).strength(0.1));
      this.forceSimulation.on("tick", () => {  
      this.repositionNodes();
      this.tick();
      
      });
      this.drag = d3
      .drag()
      .on("start", this.dragStart)
      .on("drag", this.dragging)
      .on("end", this.dragEnd);
      this.insertNodes();
      this.insertLinks();     
      this.svgElement.call(this.zoom); 
      this.svgElement.on("dblclick.zoom", null);
      this.forceSimulation.restart();
      this.forceSimulation.on("end",()=>{
        this.zoomFit();
        this.loadForwardGraph = false;
        this.pathGraphLoader = false;
    })
  }

  repositionNodes = () => {
    var ref = this;
    this.graphNode.attr("transform", function(d: any) {
        try {
          d.x = ref.positions[d.switch_id][0];
          d.y = ref.positions[d.switch_id][1];
        } catch (e) {}
        if (d.x && d.y) return "translate(" + d.x + "," + d.y + ")";
      });
  };

  zoomFit = () => {
    var bounds = this.g.node().getBBox();
    var parent = this.g.node().parentElement;
    var fullWidth =  parent.clientWidth || parent.parentNode.clientWidth || $(parent).width(),
      fullHeight =  parent.clientHeight || parent.parentNode.clientHeight || $(parent).height();
    var width = bounds.width,
      height = bounds.height;
    var midX = (bounds.x + width) / 2,
      midY = (bounds.y + height) / 2;
    if (width == 0 || height == 0) return;
    var scale = this.zoomLevel / Math.max(width / fullWidth, height / fullHeight);
    var translate = [fullWidth / 2 - scale * midX, fullHeight / 2 - scale * midY];

    if(this.nodes.length >=50){
      let newtranformation = d3.zoomIdentity
      .scale(this.min_zoom)
     .translate(translate[0], translate[1]); 
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }else{
      let newtranformation = d3.zoomIdentity
      .scale(scale)
     .translate(translate[0], translate[1]); 
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }
    
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
        return "link physical "+"link_"+d.flow;
      })
      .attr("id", (d, index) => {
        
        return ref.type+"_link" + index;
      }).attr('stroke-width', (d) =>{ return 2.5; }).attr("stroke", function(d, index) {
              return d.colourCode;
      }).attr("cursor","pointer")
      .on('mouseover',function(d,index){
        var element = document.getElementById(ref.type+"_link" + index);
        var classes = element.getAttribute("class");
        classes = classes + " overlay";
        element.setAttribute('class',classes);
         var rec: any = element.getBoundingClientRect();
         $('#reverse_flow_value').css('display','none');
          $("#diversepath-hover-txt").css("display", "block");
          $('#forward_flow_value').html(d.flow);
          $('#forward_flow_value').css('display','block');
        

           $(element).on("mousemove", function(e) {
            $("#diversepath-hover-txt").css("top", (e.pageY-50) + "px");
            $("#diversepath-hover-txt").css("left", (e.pageX-60) + "px");
            var bound = ref.horizontallyBound(
              document.getElementById(ref.graphWrapper),
              document.getElementById("diversepath-hover-txt")
            );

            if (bound) {
              $("#diversepath-hover-txt").removeClass("left");
            } else {
              var left = e.pageX; // subtract width of tooltip box + circle radius
              $("#diversepath-hover-txt").css("left", left + "px");
              $("#diversepath-hover-txt").addClass("left");
            }
          });
      }).on('mouseout',function(d,index){
        var element = document.getElementById("link" + index);
        $('#'+ref.type+'_link' + index).removeClass('overlay');
        $("#diversepath-hover-txt").css("display", "none");
        $('#isl_flow_value').css('display','none');
      });
      graphLinksData.exit().remove();
      this.graphLink = graphNewLink.merge(graphLinksData);
  }
  insertNodes(){
    let ref = this;
    let graphNodesData = this.graphNodeGroup.selectAll("g.node").data(this.nodes,d=>d.switch_id);
    let graphNodeElement = graphNodesData.enter().append("g")
    .attr("class", "node")
    .call(
      d3
        .drag()
        .on("start", this.dragStart)
        .on("drag", this.dragging)
        .on("end", this.dragEnd)
  );
    
    graphNodesData.exit().remove();
    graphNodeElement.append("circle").attr("r",function(d){
                          var portNumber = d.switch_id.split("_")[2];
                          if(typeof(portNumber)!='undefined'){
                            return 20;
                          }else{
                            return ref.graphOptions.radius
                          }
                          
                        })
                      .attr("class", function(d, index) {
                        var classes = "circle blue";
                        return classes;
                      })
                      .attr("id", function(d, index) {
                        var valuesArr = d.switch_id.split("_");
                        var switch_id = (typeof(valuesArr[1]) == 'undefined') ? d.switch_id:d.index;
                        return ref.type+"_circle_" + switch_id;
                      }).style("cursor", "pointer");
                      
                      
   let text = graphNodeElement
                      .append("text")
                      .attr("dy",function(d){
                        var portNumber = d.switch_id.split("_")[2];
                        if(typeof(portNumber)!='undefined'){
                          return '.10em';
                        }
                        return ".35em";
                      })
                      .style("font-size",(d) =>{
                        var portNumber = d.switch_id.split("_")[2];
                        if(typeof(portNumber)!='undefined'){
                          return 20;
                        }
                        return this.graphOptions.nominal_text_size + "px";
                      })
                      .attr("class", "switchname");
  if (this.graphOptions.text_center) {
    text
      .text(function(d) { 
        var portNumber = d.switch_id.split("_")[2];
          if(typeof(portNumber)!='undefined'){
            return portNumber;
          }
        return d.switch_id;
      })
      .style("text-anchor", "middle");
  } else {
    text
      .attr("dx", function(d) {
        var portNumber = d.switch_id.split("_")[2];
        if(typeof(portNumber)!='undefined'){
          return -10;
        }
        return ref.size(d.size) || ref.graphOptions.nominal_base_node_size;
      })
      .text(function(d) {
        var portNumber = d.switch_id.split("_")[2];
          if(typeof(portNumber)!='undefined'){
            return portNumber;
          }
        return d.switch_id;
      });
  }
  let images = graphNodeElement.append("svg:image")
                                .attr("xlink:href", function(d) {
                                  var portNumber = d.switch_id.split("_")[2];
                                  if(typeof(portNumber)!='undefined'){
                                    return '';
                                  }
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
                                  return ref.type+"_image_" + index;
                                }).on('mouseover',function(d){
                                  var valuesArr = d.switch_id.split("_");
                                  var switch_id = (typeof(valuesArr[1]) == 'undefined') ? d.switch_id:d.index;
                                  var element = document.getElementById( ref.type+"_circle_"+ switch_id);
                                  var classes = 'circle blue hover';
                                  element.setAttribute('class',classes);
                                }).on('mouseout',function(d){
                                  var valuesArr = d.switch_id.split("_");
                                  var switch_id = (typeof(valuesArr[1]) == 'undefined') ? d.switch_id:d.index;
                                  var element = document.getElementById( ref.type+"_circle_"+ switch_id);
                                  if(typeof(ref.commonSwitchFlag[d.switch_id]) !='undefined' && ref.commonSwitchFlag[d.switch_id]){
                                    var classes = 'circle blue hover';
                                  }else{
                                    var classes = 'circle blue';
                                  }
                                  
                                  element.setAttribute('class',classes);
                                });
                                
      
     this.graphNode = graphNodeElement.merge(graphNodesData);
                        
  }
  isObjEquivalent(a, b) {
    // Create arrays of property names
    var aProps = Object.getOwnPropertyNames(a);
    var bProps = Object.getOwnPropertyNames(b);
    if (aProps.length != bProps.length) {
      return false;
    }

    for (var i = 0; i < aProps.length; i++) {
      var propName = aProps[i];
      if (a[propName] !== b[propName]) {
        return false;
      }
    }

    return true;
  }
  tick(){
    var ref = this;
   // var lookup = {};
    this.graphLink.attr("d", d => {
      // var islCount = 0;
      // var matchedIndex = 1;
      // var key = d.source.switch_id + "_" + d.target.switch_id;
      // var key1 =  d.target.switch_id + "_" + d.source.switch_id;
      // var processKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== "undefined") ? key:key1;
      // if (
      //   this.linksSourceArr &&
      //   typeof this.linksSourceArr[processKey] !== "undefined"
      // ) {
      //   islCount = this.linksSourceArr[processKey].length;
      // }
      // if (islCount > 1) {
      //   this.linksSourceArr[processKey].map(function(o, i) {
      //     if (ref.isObjEquivalent(o, d)) {
      //       matchedIndex = i + 1;
      //       return;
      //     }
      //   });
      // }
      var x1 = d.source.x,
        y1 = d.source.y,
        x2 = d.target.x,
        y2 = d.target.y,
        dx = x2 - x1,
        dy = y2 - y1,
        dr = Math.sqrt(dx * dx + dy * dy);
        // drx = dr,
        // dry = dr - 100,
        // xRotation = 0, // degrees
        // largeArc = 0, // 1 or 0
        // sweep = 1; // 1 or 0
        // var lTotalLinkNum = 2;
        // if (lTotalLinkNum > 1) {
        //   dr = dr / (1 + (1 / lTotalLinkNum) * (d.index));
        // }
      
        //  if (x1 === x2 && y1 === y2) {
        //     xRotation = -45;
        //     largeArc = 1;
        //     drx = 50;
        //     dry = 20;
        //     x2 = x2 + 1;
        //     y2 = y2 + 1;
        //   }

          return (
            "M" +
            d.source.x +
            "," +
            d.source.y +
            "L" +
            d.target.x +
            "," +
            d.target.y
          );
         
    });
     this.graphNode.attr("transform", function(d) {
        if (d.x && d.y) {
          return "translate(" + d.x + "," + d.y + ")";
        }
      });
  }
  

  ngAfterViewInit() {
    
  }
  ngOnDestroy(){
   
  }
}
