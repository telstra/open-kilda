import { Injectable } from '@angular/core';
import { CommonService } from './common.service';
import * as d3 from "d3";
import { environment } from "src/environments/environment";
import { Subject } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class FlowpathService {
  forwardPathLoaded :boolean = false;
  reversepathLoaded : boolean = false;
  forwardpathLoadedChange: Subject<boolean> = new Subject<boolean>();
  reversepathLoadedChange: Subject<boolean> = new Subject<boolean>();
  diverseGroupCommonSwitch:any = [];
  diverseGroupCommonSwitchReverse:any = [];
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
  min_zoom = 0.15;
  max_zoom = 3;
  zoomStep = 0.15;
  simulationArr = {};
  zoomArr = {};
  zoomLevelArr = {};
  constructor(private commonService:CommonService,    
    private router:Router
    ) { }


  setCommonSwitch (type,data){
    if(type=='forward'){
      this.diverseGroupCommonSwitch= data;
    }else{
      this.diverseGroupCommonSwitchReverse= data;
    }
  }
  getcommonSwitches(type){
    if(type=='forward'){
      return this.diverseGroupCommonSwitch;
    }else{
      return this.diverseGroupCommonSwitchReverse;
    }
  }
  loadIslDetail(src_switch,src_port,dst_switch,dst_port){  
    this.router.navigate(["/isl/switch/isl/"+src_switch+"/"+src_port+"/"+dst_switch+"/"+dst_port]);
   }
  
  
   loadSwitchDetail(switchId){   
       this.router.navigate(["/switches/details/" + switchId]);
   }
  forwardLoaderChange(){
      this.forwardPathLoaded = false;
      this.forwardpathLoadedChange.next(this.forwardPathLoaded);
  }
  reverseLoaderChange(){
    this.reversepathLoaded = false;
    this.reversepathLoadedChange.next(this.reversepathLoaded);
 }
  horizontallyBound = (parentDiv, childDiv) => {
    let parentRect: any = parentDiv.getBoundingClientRect();
    let childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  };
  initSimulation(nodes,links,svgElement,graphWrapper,type,positions,hoverTextID,showValueID,hideValueID){
    var linksSourceArr = [];
    var self = this;
      if (links.length > 0) {
        try {
          var result = this.commonService.groupBy(links, function(item) {
            return [item.source, item.target];
          });
          for (var i = 0, len = result.length; i < len; i++) {
            var row = result[i];
            if (row.length >= 1) {
              for (var j = 0, len1 = row.length; j < len1; j++) {
                var key = row[j].source.switch_id+ "_" + row[j].target.switch_id;
                var key1 = row[j].target.switch_id + "_" + row[j].source.switch_id;
                var prcessKey = ( linksSourceArr && typeof linksSourceArr[key] !== "undefined") ? key:key1;
                if (typeof linksSourceArr[prcessKey] !== "undefined") {
                  linksSourceArr[prcessKey].push(row[j]);
                } else {
                  linksSourceArr[key] = [];
                  linksSourceArr[key].push(row[j]);
                }
              }
            }
          }
          
        } catch (e) {}
      }
      var processedlinks = this.processLinks(nodes,links);
      var  zoomLevel = 0.45;     
      svgElement.html(""); 
      var width = window.innerWidth;
      var height = svgElement.attr('height');
      svgElement.style('cursor','move');
      svgElement.attr("width",width);
      svgElement.attr("height",height);
      var g = svgElement.append("g");
      var graphLinkGroup = g.append("g").attr("id", `links`).attr("class", "links");
      var graphNodeGroup = g.append('g').attr("class",".nodes").attr("id","nodes");
      var size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));
      var zoom = this.zoomArr[type] =  d3
      .zoom()
      .scaleExtent([this.min_zoom, this.max_zoom])
      .extent([[0, 0], [width - 200, height-50]])
      .on("zoom", () => {
        zoomLevel = Math.round(d3.event.transform.k*100)/100;
        self.zoomLevelArr[type] = zoomLevel;        
        g.attr(
          "transform",
          "translate(" +
            d3.event.transform.x +
            "," +
            d3.event.transform.y +
            ") scale(" +
            d3.event.transform.k +
            ")"
        );
        
      });
      var mLinkNum =  this.setLinkIndexAndNum(processedlinks);
      var forceSimulation = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
      return 20;
      }))
      .force("charge_force",d3.forceManyBody().strength(-1000))
      .force("xPos", d3.forceX(width /2))
      .force("yPos", d3.forceY(height / 2));
      forceSimulation.nodes(nodes);
      forceSimulation.force("link", d3.forceLink().links(processedlinks).distance((d:any)=>{
         let distance = 20;
        if(d.type=='isl'){
          distance = 150;
        }
      return distance; 
      }).strength(0.1));
      var graphNode = this.insertNodes(graphNodeGroup,nodes,type,size,hoverTextID,showValueID);
      var graphLink = this.insertLinks(graphWrapper,graphLinkGroup,processedlinks,type,hoverTextID,showValueID,hideValueID); 
      forceSimulation.on("tick", () => {  
      this.repositionNodes(graphNode,positions);
      this.tick(graphLink,graphNode,linksSourceArr,mLinkNum);
      
      });
      svgElement.call(zoom); 
      svgElement.on("dblclick.zoom", null);
      forceSimulation.restart();
      forceSimulation.on("end",()=>{
        this.zoomFit(g,svgElement,zoomLevel,zoom,nodes,type);
    });
    this.simulationArr[type] = forceSimulation;
  }

  repositionNodes = (graphNode,positions) => {
    graphNode.attr("transform", function(d: any) {
        try {
          d.x = positions[d.switch_id][0];
          d.y = positions[d.switch_id][1];
        } catch (e) {}
        if (typeof(d.x) != 'undefined' && typeof(d.y)!='undefined'){
          return "translate(" + d.x + "," + d.y + ")";
        }
      });
  };

  zoomFit = (g,svgElement,zoomLevel,zoom,nodes,type) => {
    var bounds = g.node().getBBox();
    var parent = g.node().parentElement;
    var fullWidth =  parent.clientWidth || parent.parentNode.clientWidth || $(parent).width(),
      fullHeight =  parent.clientHeight || parent.parentNode.clientHeight || $(parent).height();
    var width = bounds.width,
      height = bounds.height;
    var midX = (bounds.x + width) / 2,
      midY = (bounds.y + height) / 2;
    if (width == 0 || height == 0) return;
    var scale = zoomLevel / Math.max(width / fullWidth, height / fullHeight);
    if(nodes.length >= 70){
      var translate = [(fullWidth/2  - scale * midX)/scale, (fullHeight/2  - scale * midY)/scale];
    }else{
      var translate = [(fullWidth / 2   - scale * midX) , (fullHeight/2  - scale * midY) ];
    }
    let newtranformation = d3.zoomIdentity
      .scale(scale)
     .translate(translate[0], translate[1]); 
      svgElement.transition().duration(300).call(zoom.transform, newtranformation);
      if(type=='forwardDiverse' || type=='forward'){
        this.forwardLoaderChange();
      }else{
        this.reverseLoaderChange();
      }
    }

  zoomFn(direction,svgElement,type){
    
    var simulation = this.simulationArr[type];
    var zoom  = this.zoomArr[type];
    var zoomLevel = this.zoomLevelArr[type];
    if (direction == 1) {
      simulation.stop();
      if (zoomLevel + this.zoomStep <= this.max_zoom) {
        svgElement
          .transition()
          .duration(350)
          .call(zoom.scaleTo, zoomLevel + this.zoomStep);
      }
    } else if (direction == -1) {
      simulation.stop();
      if (zoomLevel - this.zoomStep >= this.min_zoom) {
        svgElement
          .transition()
          .duration(350)
          .call(zoom.scaleTo, zoomLevel - this.zoomStep);
      }
    }
  };

  processLinks(nodes,links){
    var nodelength = nodes.length;
    var linklength = links.length;
    for (var i = 0; i < nodelength; i++) {
     for (var j = 0; j < linklength; j++) { 
       if (
         nodes[i].switch_id == links[j]["source"]["switch_id"] &&
         nodes[i].switch_id == links[j]["target"]["switch_id"]
       ) { 
          links[j].source = i;
          links[j].target = i;
       } else {
         if (nodes[i].switch_id == links[j]["source"]["switch_id"]) { 
           links[j].source = i;
           } else if (
             nodes[i].switch_id == links[j]["target"]["switch_id"]
           ) { 
             links[j].target = i;
           }
       }
     }
   }
   return links;
  }

  
  generatePositionForNodes(element,nodes){
    var positions = [];
     var yValue =  element[0].clientTop;
     var xValue = element[0].clientLeft; 
      if(yValue == 0){
          yValue = 100;
      }
      if(xValue == 0){
          xValue = 150;
      }

      var maxLimit = element[0].clientWidth;
     var reverseXvalue = element[0].clientWidth;
     var reverseYvalue = element[0].clientTop + 200;
     for(var i = 0; i < nodes.length; i++){
      positions[nodes[i].switch_id] = [];
      var arr = nodes[i].switch_id.split("_");
      var arr1 = [];
      if(i > 0){
         arr1 = nodes[i-1].switch_id.split("_");
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
        
        positions[nodes[i].switch_id][0] = xValue ;
        positions[nodes[i].switch_id][1] = yValue;
        if(isSwitch){
          positions[nodes[i].switch_id][1] = positions[nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          xValue = xValue + 150;
          positions[nodes[i].switch_id][0] = xValue;
        }
        xValue = xValue + 80;
      }else if(xValue >= maxLimit && reverseXvalue > 100){
         positions[nodes[i].switch_id][0] = reverseXvalue;
         positions[nodes[i].switch_id][1] = reverseYvalue;
         if(isSwitch){
          positions[nodes[i].switch_id][1] = positions[nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          reverseXvalue = reverseXvalue - 150;
          positions[nodes[i].switch_id][0] = reverseXvalue;
        }
         reverseXvalue = reverseXvalue - 80;
      }else{
         yValue =  reverseYvalue + 100;
         xValue = element[0].clientLeft; 
         maxLimit = element[0].clientWidth;
         reverseXvalue = element[0].clientWidth;
         reverseYvalue = yValue + 200;
         positions[nodes[i].switch_id][0] = xValue ;
         positions[nodes[i].switch_id][1] = yValue;
         if(isSwitch){
          positions[nodes[i].switch_id][1] = positions[nodes[i].switch_id][1] - 50;
        }
        if(isIslPort){
          xValue = xValue + 150;
          positions[nodes[i].switch_id][0] = xValue;
        }
        
      }
      
    } 
    return positions;
  }
  insertLinks(graphWrapper,graphLinkGroup,links,type,hoverTextID,showValueID,hideValueID){
    var ref = this;
    let graphLinksData = graphLinkGroup.selectAll("path.link").data(links);
     let graphNewLink = graphLinksData
      .enter()
      .append("path")
      .attr("class", function(d, index) {
        return "link physical "+type+"_link_"+d.flow;
      })
      .attr("id", (d, index) => {
        
        return type+"_link" + index;
      }).attr('stroke-width', (d) =>{ return 4.5; }).attr("stroke", function(d, index) {
              return d.colourCode;
      }).attr("cursor","pointer")
      .on('mouseover',function(d,index){
        var element = document.getElementById(type+"_link" + index);
        var classes = element.getAttribute("class");
        classes = classes + " overlay";
        element.setAttribute('class',classes);
         var rec: any = element.getBoundingClientRect();
         $('#'+hideValueID).css('display','none');
          $("#"+hoverTextID).css("display", "block");
          $('#'+showValueID).html(d.flow);
          $('#'+showValueID).css('display','block');
        

           $(element).on("mousemove", function(e) {
            $("#"+hoverTextID).css("top", (e.pageY-50) + "px");
            $("#"+hoverTextID).css("left", (e.pageX-60) + "px");
            var bound = ref.horizontallyBound(
              document.getElementById(graphWrapper),
              document.getElementById(hoverTextID)
            );

            if (bound) {
              $("#"+hoverTextID).removeClass("left");
            } else {
              var left = e.pageX; // subtract width of tooltip box + circle radius
              $("#"+hoverTextID).css("left", left + "px");
              $("#"+hoverTextID).addClass("left");
            }
          });
      }).on('mouseout',function(d,index){
        var element = document.getElementById("link" + index);
        $('#'+type+'_link' + index).removeClass('overlay');
        $("#"+hoverTextID).css("display", "none");
      }).on('click',function(d){
        if(d.type == 'isl'){
          var src_switch = d.source_detail.id,
          src_port = d.source_detail.out_port,
          dst_switch = d.target_detail.id,
          dst_port = d.target_detail.in_port;
          ref.loadIslDetail(src_switch,src_port,dst_switch,dst_port);
        }
        
      });
      graphLinksData.exit().remove();
     return  graphNewLink.merge(graphLinksData);
  }
  insertNodes(graphNodeGroup,nodes,type,size,hoverTextID,showValueID){
    let ref = this;
    let graphNodesData = graphNodeGroup.selectAll("g.node").data(nodes,d=>d.switch_id);
    let graphNodeElement = graphNodesData.enter().append("g")
    .attr("class", "node");
    
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
                        return type+"_circle_" + switch_id;
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
        return size(d.size) || ref.graphOptions.nominal_base_node_size;
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
                                  return type+"_image_" + index;
                                }).on('mouseover',function(d){
                                  var valuesArr = d.switch_id.split("_");
                                  var switch_id = (typeof(valuesArr[1]) == 'undefined') ? d.switch_id:d.index;
                                  var element = document.getElementById( type+"_circle_"+ switch_id);
                                  var classes = 'circle blue hover';
                                  element.setAttribute('class',classes);
                                  var portNumber = d.switch_id.split("_")[2];
                                  var switch_id =  d.switch_id.split("_")[1];
                                  if(typeof(portNumber)!='undefined'){
                                    var rec: any = element.getBoundingClientRect();
                                     $("#"+hoverTextID).css("top", (rec.y - 40) + "px");
                                     $("#"+hoverTextID).css("left", (rec.x - 60) + "px");                                     
                                     $("#"+hoverTextID).css("display", "block");
                                     $('#'+showValueID).html(switch_id);
                                     $('#'+showValueID).css('display','block');
                                   }
                                  
                                }).on('mouseout',function(d){
                                  var valuesArr = d.switch_id.split("_");
                                  var switch_id = (typeof(valuesArr[1]) == 'undefined') ? d.switch_id:d.index;
                                  var element = document.getElementById( type+"_circle_"+ switch_id);
                                  var classes = 'circle blue';
                                  
                                  element.setAttribute('class',classes);
                                  var portNumber = d.switch_id.split("_")[2];
                                  if(typeof(portNumber)!='undefined'){
                                    $("#"+hoverTextID).css("display", "none");
                                  }
                                }).on('click',function(d){
                                  var portNumber = d.switch_id.split("_")[2];
                                  var switch_id =  d.switch_id.split("_")[1];
                                  if(typeof(portNumber)=='undefined'){
                                    ref.loadSwitchDetail(switch_id);
                                   }
                                });;
                                
      
    return graphNodeElement.merge(graphNodesData);
                        
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

  setLinkIndexAndNum(links) {
    var mLinkNum = [];
    for (var i = 0; i < links.length; i++) {
      if (
        i != 0 &&
        links[i].source == links[i - 1].source.switch_id &&
        links[i].target == links[i - 1].target.switch_id
      ) {
        links[i].linkindex = links[i - 1].linkindex + 1;
      } else {
        links[i].linkindex = 1;
      }
      // save the total number of links between two nodes
      if (
        mLinkNum[links[i].target.switch_id + "," + links[i].source.switch_id] !==
        undefined
      ) {
        mLinkNum[
          links[i].target.switch_id + "," + links[i].source.switch_id
        ] = links[i].linkindex;
      } else {
        mLinkNum[
          links[i].source.switch_id + "," + links[i].target.switch_id
        ] = links[i].linkindex;
      }
    }
    return mLinkNum;
  }

  tick(graphLink,graphNode,linksSourceArr,mLinkNum){
    var ref = this;
    var lookup = {};
    graphLink.attr("d", d => {
      var islCount = 0;
      var matchedIndex = 1;
      var key = d.source.switch_id + "_" + d.target.switch_id;
      var key1 =  d.target.switch_id + "_" + d.source.switch_id;
      var processKey = ( linksSourceArr && typeof linksSourceArr[key] !== "undefined") ? key:key1;
      if (
        linksSourceArr &&
        typeof linksSourceArr[processKey] !== "undefined"
      ) {
        islCount = linksSourceArr[processKey].length;
      }
      if (islCount > 1) {
        linksSourceArr[processKey].map(function(o, i) {
          if (ref.isObjEquivalent(o, d)) {
            matchedIndex = i + 1;
            return;
          }
        });
      }
    
      var x1 = d.source.x,
        y1 = d.source.y,
        x2 = d.target.x,
        y2 = d.target.y,
        dx = x2 - x1,
        dy = y2 - y1,
        dr = Math.sqrt(dx * dx + dy * dy),
        // Defaults for normal edge.
        drx = dr,
        dry = dr,
        xRotation = 0, // degrees
        largeArc = 0, // 1 or 0
        sweep = 1; // 1 or 0
      var lTotalLinkNum =
        mLinkNum[d.source.index + "," + d.target.index] ||
        mLinkNum[d.target.index + "," + d.source.index];

      if (lTotalLinkNum > 1) {
        dr = dr / (1 + (1 / lTotalLinkNum) * (d.linkindex - 1));
      }

      // generate svg path

      lookup[d.key] = d.flow_count;
      if (lookup[d.Key] == undefined) {
        if (islCount == 1) {
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
        } else {
          if (islCount % 2 != 0 && matchedIndex == 1) {
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
          } else if (matchedIndex % 2 == 0) { 
            return (
              "M" +
              d.source.x +
              "," +
              d.source.y +
              "A" +
              dr +
              "," +
              dr +
              " 0 0 1," +
              d.target.x +
              "," +
              d.target.y +
              "A" +
              dr +
              "," +
              dr +
              " 0 0 0," +
              d.source.x +
              "," +
              d.source.y
            );
          } else {  
            return (
              "M" +
              d.source.x +
              "," +
              d.source.y +
              "A" +
              dr +
              "," +
              dr +
              " 0 0 0," +
              d.target.x +
              "," +
              d.target.y +
              "A" +
              dr +
              "," +
              dr +
              " 0 0 1," +
              d.source.x +
              "," +
              d.source.y
            );
          }
        }
      } else {
        if (d.source_switch == d.target_switch) {
          // Self edge.
          if (x1 === x2 && y1 === y2) {
            // Fiddle with this angle to get loop oriented.
            xRotation = -45;

            // Needs to be 1.
            largeArc = 1;

            // Change sweep to change orientation of loop.
            //sweep = 0;

            // Make drx and dry different to get an ellipse
            // instead of a circle.
            drx = 50;
            dry = 20;

            // For whatever reason the arc collapses to a point if the beginning
            // and ending points of the arc are the same, so kludge it.
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
        } else {
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
        }
      }
       

          // return (
          //   "M" +
          //   d.source.x +
          //   "," +
          //   d.source.y +
          //   "L" +
          //   d.target.x +
          //   "," +
          //   d.target.y
          // );
         
    });
     graphNode.attr("transform", function(d) {
        if (d.x && d.y) {
          return "translate(" + d.x + "," + d.y + ")";
        }
      });
  }
}
