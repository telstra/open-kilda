import { Injectable } from '@angular/core';

import { ISL } from "../enums/isl.enum";
import * as d3 from "d3";
import { environment } from "../../../environments/environment";
import { CommonService } from './common.service';
@Injectable({
  providedIn: 'root'
})
export class TopologyGraphService {

    size: any;
    simulation: any;
    force: any;
    g: any;
  	drag: any;
	  min_zoom = 0.25;
    scaleLimit = 0.05;
    linksSourceArr:any=[];
    max_zoom = 3;
    mLinkNum: any = {};
  	zoom:any;
	  svgElement: any;
	  graphlink:any;
  	node:any;
    graphNodes:any;
    graphLinkGroup:any;
    graphNodeGroup:any;
    graph_data:any;
    isDragMove = true;
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
  constructor(private commonService:CommonService) { 

  }

  loadworldMapGraph(data,svgElement,width,height,graph_loader){
    this.graph_data = data;
    this.svgElement = d3.select("#"+svgElement)
	.append("svg")
	.attr("width", width)
  .attr("height", height)
	this.g =this.svgElement.append("g");

	this.graphLinkGroup = this.g
	.append("g")
	.attr("id", `links`)
	.attr("class", "links");

  // Initialize the nodes
   this.graphNodeGroup = this.g.append("g")
   				.attr("id", `nodes`)
   				.attr("class", "nodes");
	this.zoom = d3
	.zoom()
	.scaleExtent([this.scaleLimit, this.max_zoom])
	.extent([[0, 0], [width, height]])
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
	});
	this.simulation = d3.forceSimulation()                 // Force algorithm is applied to data.nodes      
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
        return 20;
      }))
      .force("charge_force",d3.forceManyBody().strength(-1000))
      .force("xPos", d3.forceX(width /2))
      .force("yPos", d3.forceY(height / 2)) ; 
      if (this.graph_data.links.length > 0) {
        try {
          var result = this.commonService.groupBy(this.graph_data.links, function(item) {
            return [item.source_switch, item.target_switch];
          });
          for (var i = 0, len = result.length; i < len; i++) {
            var row = result[i];
            if (row.length >= 1) {
              for (var j = 0, len1 = row.length; j < len1; j++) {
                var key = row[j].source_switch + "_" + row[j].target_switch;
                var key1 = row[j].target_switch + "_" + row[j].source_switch;
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
      var nodelength = this.graph_data.nodes.length;
      var linklength = this.graph_data.links.length;
      for (var i = 0; i < nodelength; i++) {
        for (var j = 0; j < linklength; j++) {
          if (
            this.graph_data.nodes[i].switch_id == this.graph_data.links[j]["source_switch"] &&
            this.graph_data.nodes[i].switch_id == this.graph_data.links[j]["target_switch"]
          ) {
            this.graph_data.links[j].source = i;
            this.graph_data.links[j].target = i;
          } else {
            var key = this.graph_data.links[j]["source_switch"] +"_"+this.graph_data.links[j]["target_switch"]; 
            var key1 = this.graph_data.links[j]["target_switch"] +"_"+this.graph_data.links[j]["source_switch"]; 
            var processKey = this.linksSourceArr && typeof this.linksSourceArr[key] !='undefined' ? key: key1;
            var sourceObj = processKey.split("_")[0];
            var targetObj = processKey.split("_")[1];
            if (this.graph_data.nodes[i].switch_id == sourceObj) {
              this.graph_data.links[j].source = i;
            } else if (
              this.graph_data.nodes[i].switch_id == targetObj
            ) {
              this.graph_data.links[j].target = i;
            }
          }
        }
      }



    this.simulation.nodes(this.graph_data.nodes);
    this.simulation.force("link", d3.forceLink().links(this.graph_data.links).distance((d:any)=>{
      let distance = 150;
       try{
      if(!d.flow_count){
        if(d.speed == "40000000"){
          distance = 100;
        }else {
          distance = 300;
        }
       }
       }catch(e){}
       return distance; 
     }).strength(0.1));
	  this.simulation.stop();
	  this.simulation.on("tick", () => { 
		this.ticked();
    this.zoomFit();
    
	   }); 
	  this.drag = d3
      .drag()
      .on("start", this.dragStart)
      .on("drag", this.dragging)
      .on("end", this.dragEnd);
      this.size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));
	  this.insertNodes(this.graph_data.nodes,true);
	  this.insertLinks(this.graph_data.links,true);
	  this.svgElement.call(this.zoom);  
	  this.svgElement.on("dblclick.zoom", null);
	  this.simulation.restart();
	  this.simulation.on("end", ()=>{
		setTimeout(()=>{
			this.ticked();
			this.zoomFit();
			graph_loader.style.display="none";
		},1000);
	  });
  }

  zoomFit = () => {
    var bounds = this.g.node().getBBox();
    var parent = this.g.node().parentElement;
    var fullWidth = $(parent).width(),
    fullHeight = $(parent).height();
    var width = bounds.width,
      height = bounds.height;

    var midX = (bounds.x + width) / 2,
      midY = (bounds.y + height) / 2;
    if (width == 0 || height == 0) return;

    if(this.graph_data.nodes.length >=50){
      let newtranformation = d3.zoomIdentity
      .scale(this.scaleLimit)
     .translate(
      (fullWidth/2 - this.scaleLimit*midX)/this.scaleLimit,
      (fullHeight/2 - this.scaleLimit*midY)/this.scaleLimit
      ); 
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }else{
      let newtranformation = d3.zoomIdentity
      .scale(this.min_zoom)
     .translate(
      (fullWidth/2 - this.min_zoom*midX),
      (fullHeight/2 - this.min_zoom*midY)
      ); 
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }
    
  }
  horizontallyBound = (parentDiv, childDiv) => {
    let parentRect: any = parentDiv.getBoundingClientRect();
    let childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  };
  showSwitchDetails = d => {
    localStorage.setItem("switchDetailsJSON", JSON.stringify(d));
    var url = "switches/details/" + d.switch_id;
    var win = window.open(url,"_blank");
    win.focus();
  };
  dblclick = (d, index) => {
    var element = document.getElementById("circle_" + d.switch_id);
    var classes = "circle blue";
    if (d.state && d.state.toLowerCase() == "deactivated") {
      classes = "circle red";
    }
    element.setAttribute("class", classes);
    this.showSwitchDetails(d);
  };
  insertNodes(nodes,forMap){
    let ref = this;
    let graphNodesData = this.graphNodeGroup
      .selectAll("g.node")
      .data(nodes, d => d.switch_id);
  let graphNodeElement = graphNodesData
      .enter()
      .append("g")
      .attr("class", "node")
      .on("dblclick", this.dblclick)
      .call(
        d3
          .drag()
          .on("start", this.dragStart)
          .on("drag", this.dragging)
          .on("end", this.dragEnd)
    );

    graphNodesData.exit().remove();
    graphNodeElement
      .append("circle")
      .attr("r", this.graphOptions.radius)
      .attr("class", function(d, index) {
        var classes = "circle blue";
        if (d.state && d.state.toLowerCase() == "deactivated") {
          classes = "circle red";
        }
        return classes;
      })
      .attr("id", function(d, index) {
        return "circle_" + d.switch_id;
      })
      .style("cursor", "move");

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

    let images = graphNodeElement
      .append("svg:image")
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
      })
      .attr("cursor", "pointer");
  if(!forMap){
    graphNodeElement.on("mouseover", function(d, index) {
      $("#isl_hover").css("display", "none");
      var element = document.getElementById("circle_" + d.switch_id);

      var classes = "circle blue hover";
      if (d.state && d.state.toLowerCase() == "deactivated") {
        classes = "circle red hover";
      }
      element.setAttribute("class", classes);
      var rec: any = element.getBoundingClientRect();
      $("#topology-hover-txt, #switch_hover").css("display", "block");
      $("#topology-hover-txt").css("top", rec.y + "px");
      $("#topology-hover-txt").css("left", (rec.x) + "px");

      d3.select(".switchdetails_div_switch_name").html(
        "<span>" + d.name + "</span>"
      );
      d3.select(".switchdetails_div_controller").html(
        "<span>" + d.switch_id + "</span>"
      );
      d3.select(".switchdetails_div_state").html(
        "<span>" + d.state + "</span>"
      );
      d3.select(".switchdetails_div_address").html(
        "<span>" + d.address + "</span>"
      );
      d3.select(".switchdetails_div_name").html(
        "<span>" + d.switch_id + "</span>"
      );
      d3.select(".switchdetails_div_desc").html(
        "<span>" + d.description + "</span>"
      );
      var bound = ref.horizontallyBound(
        document.getElementById("switchesgraph"),
        document.getElementById("topology-hover-txt")
      );
      if (bound) {
        
        $("#topology-hover-txt").removeClass("left");
      } else {
        var left = rec.x - (300 + 100); // subtract width of tooltip box + circle radius
        $("#topology-hover-txt").css("left", left + "px");
        $("#topology-hover-txt").addClass("left");
      }
      
    })
    .on("mouseout", function(d, index) {
      if (this.flagHover == false) {
        this.flagHover = true;
      } else {
        var element = document.getElementById("circle_" + d.switch_id);
        var classes = "circle blue";
        if (d.state && d.state.toLowerCase() == "deactivated") {
          classes = "circle red";
        }
        element.setAttribute("class", classes);
      }
      

      if (!$("#topology-hover-txt").is(":hover")) {
        $("#topology-hover-txt, #switch_hover").css("display", "none");
      }

    })
    .on("click", function(d, index) {
      $("#topology-hover-txt").css("display", "none");

      var cName = document.getElementById("circle_" + d.switch_id).className;
      let circleClass = cName; //cName.baseVal;

      var element = document.getElementById("circle_" + d.switch_id);

      var classes = "circle blue hover";
      if (d.state && d.state.toLowerCase() == "deactivated") {
        classes = "circle red hover";
      }
      element.setAttribute("class", classes);
      var rec: any = element.getBoundingClientRect();
      if (!ref.isDragMove) {
        $("#topology-click-txt, #switch_click").css("display", "block");
        $("#topology-click-txt").css("top", rec.y + "px");
        $("#topology-click-txt").css("left", rec.x + "px");

        d3.select(".switchdetails_div_click_switch_name").html(
          "<span>" + d.name + "</span>"
        );
        d3.select(".switchdetails_div_click_controller").html(
          "<span>" + d.switch_id + "</span>"
        );
        d3.select(".switchdetails_div_click_state").html(
          "<span>" + d.state + "</span>"
        );
        d3.select(".switchdetails_div_click_address").html(
          "<span>" + d.address + "</span>"
        );
        d3.select(".switchdetails_div_click_name").html(
          "<span>" + d.switch_id + "</span>"
        );
        d3.select(".switchdetails_div_click_desc").html(
          "<span>" + d.description + "</span>"
        );
        var bound = ref.horizontallyBound(
          document.getElementById("switchesgraph"),
          document.getElementById("topology-click-txt")
        );
        if (bound) {
          $("#topology-click-txt").removeClass("left");
        } else {
          var left = rec.x - (300 + 80); // subtract width of tooltip box + circle radius
          $("#topology-click-txt").css("left", left + "px");
          $("#topology-click-txt").addClass("left");
        }
         $("#topology-hover-txt").css("display", "none");
      } else {
        ref.isDragMove = false;
      }
    });
  }
      

	this.graphNodes = graphNodeElement.merge(graphNodesData);
  }
  insertLinks(links,forMap){
   let ref = this;
    let graphLinksData = this.graphLinkGroup.selectAll("path.link").data(links);

    let graphNewLink = graphLinksData
      .enter()
      .append("path")
      .attr("class", function(d, index) {
        var availbandwidth = d.available_bandwidth;
        var max_bandwidth = d.max_bandwidth;
        var percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty("flow_count")) {
          return "link logical";
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == "discovered") ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if(d.under_maintenance){
              if(parseInt(percentage) < 50){
                return "link physical  orange_percentage dashed_maintenance_path";
              }
              return "link physical  dashed_maintenance_path";
            } else if (d.affected) {
              return "link physical  dashed_path";
            }else {
              return "link physical";
            }
          } else {
            if(d.under_maintenance){
              if (parseInt(percentage) < 50) {
                return "link physical dashed_maintenance_path orange_percentage";
              }
              return "link physical  dashed_maintenance_path";
            }else if (d.affected) {
              return "link physical dashed_path";
            }else {
              if (parseInt(percentage) < 50) {
                return "link physical orange_percentage";
              }
              return "link physical";
            }
          }
        }
      })
      .attr("id", (d, index) => {
        return "link" + index;
      }).on("click", function(d, index) {
        var element = $("#link" + index)[0];
        var availbandwidth = d.available_bandwidth;
        var max_bandwidth = d.max_bandwidth;
        var percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty("flow_count")) {
          if(d.under_maintenance){
            element.setAttribute("class", "link logical overlay dashed_maintenance_path");
          } else if (d.affected) {
            element.setAttribute("class", "link logical overlay dashed_path");
          }else {
            element.setAttribute("class", "link logical overlay");
          }

          ref.showFlowDetails(d);
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == "discovered") ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if(d.under_maintenance){
              if(parseInt(percentage) < 50){
                
              element.setAttribute("class", "link physical pathoverlay orange_percentage dashed_maintenance_path");
              }else{
                
              element.setAttribute("class", "link physical pathoverlay dashed_maintenance_path");
              }
            } else if (d.affected) {
              element.setAttribute(
                "class",
                "link physical pathoverlay dashed_path"
              );
            }else {
              element.setAttribute("class", "link physical pathoverlay");
            }
          } else {
            if(d.under_maintenance){
              if(parseInt(percentage) < 50){
                
              element.setAttribute("class", "link physical overlay orange_percentage dashed_maintenance_path");
              }else{
                
              element.setAttribute("class", "link physical overlay dashed_maintenance_path");
              }
            } else if (d.affected) {
              element.setAttribute(
                "class",
                "link physical overlay dashed_path"
              );
            }else {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  "class",
                  "link physical orange_percentage overlay"
                );
              } else {
                element.setAttribute("class", "link physical overlay");
              }
            }
          }
          ref.showLinkDetails(d);
        }
      })
      .attr("stroke", function(d, index) {
        if (d.hasOwnProperty("flow_count")) {
          return ISL.FLOWCOUNT;
        } else {
         if (
            d.unidirectional &&
            d.state &&
            d.state.toLowerCase() == "discovered"
          ) {
            return ISL.UNIDIR;
          } else if (d.state && d.state.toLowerCase() == "discovered") {
            return ISL.DISCOVERED;
          }else if (d.state && d.state.toLowerCase() == "moved") {
            return ISL.MOVED;
          }

          return ISL.FAILED;
        }
      });
      if(!forMap){
        graphLinksData.on("mouseover", function(d, index) {
          $("#switch_hover").css("display", "none");
          var element = $("#link" + index)[0];
          var availbandwidth = d.available_bandwidth;
          var max_bandwidth = d.max_bandwidth;
  
          var percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
          if (d.hasOwnProperty("flow_count")) {
            if(d.under_maintenance){
              element.setAttribute("class", "link logical overlay dashed_maintenance_path");
        
            } else if (d.affected) {
              element.setAttribute("class", "link logical overlay dashed_path");
            }else  {
              element.setAttribute("class", "link logical overlay");
            }
          } else {
            if (
              (d.unidirectional &&
                d.state &&
                d.state.toLowerCase() == "discovered") ||
              (d.state && d.state.toLowerCase() == "failed")
            ) {
              if(d.under_maintenance){
                if(parseInt(percentage) < 50){
                  element.setAttribute(
                    "class",
                    "link physical dashed_maintenance_path orange_percentage pathoverlay"
                  );
                }else{
                  element.setAttribute(
                    "class",
                    "link physical dashed_maintenance_path pathoverlay"
                  );
                }
              } else if (d.affected) {
                element.setAttribute(
                  "class",
                  "link physical dashed_path pathoverlay"
                );
              }else  {
                if (parseInt(percentage) < 50 && d.state.toLowerCase() != 'failed' && !d.unidirectional) {
                  element.setAttribute(
                    "class",
                    "link physical orange_percentage overlay"
                  );
                } else {
                  element.setAttribute("class", "link physical overlay");
                }
              }
            } else {
              if(d.under_maintenance){
                if(parseInt(percentage) < 50){
                  element.setAttribute(
                    "class",
                    "link physical overlay orange_percentage dashed_maintenance_path"
                  );
                }else{
                  element.setAttribute(
                    "class",
                    "link physical overlay dashed_maintenance_path"
                  );
                }
                
                
              } else if (d.affected) {
                element.setAttribute(
                  "class",
                  "link physical overlay dashed_path"
                );
              }else  {
                if (parseInt(percentage) < 50) {
                  element.setAttribute(
                    "class",
                    "link physical orange_percentage overlay"
                  );
                } else {
                  element.setAttribute("class", "link physical overlay");
                }
              }
            }
            $(element).on("mousemove", function(e) {
              $("#topology-hover-txt").css("top", (e.pageY-30) + "px");
              $("#topology-hover-txt").css("left", (e.pageX) + "px");
              var bound = ref.horizontallyBound(
                document.getElementById("switchesgraph"),
                document.getElementById("topology-hover-txt")
              );
  
              if (bound) {
                $("#topology-hover-txt").removeClass("left");
              } else {
                var left = e.pageX - (300 + 100); // subtract width of tooltip box + circle radius
                $("#topology-hover-txt").css("left", left + "px");
                $("#topology-hover-txt").addClass("left");
              }
            });
  
            var rec = element.getBoundingClientRect();
            $("#topology-hover-txt, #isl_hover").css("display", "block");
            d3.select(".isldetails_div_source_port").html(
              "<span>" +
                (d.src_port == "" || d.src_port == undefined ? "-" : d.src_port) +
                "</span>"
            );
            d3.select(".isldetails_div_maintenance").html(
              "<span>" +
                (d.under_maintenance == "" || d.under_maintenance == undefined ? "false" : d.under_maintenance) +
                "</span>"
            );
            
            d3.select(".isldetails_div_destination_port").html(
              "<span>" +
                (d.dst_port == "" || d.dst_port == undefined ? "-" : d.dst_port) +
                "</span>"
            );
            d3.select(".isldetails_div_source_switch").html(
              "<span>" +
                (d.source_switch_name == "" || d.source_switch_name == undefined
                  ? "-"
                  : d.source_switch_name) +
                "</span>"
            );
            d3.select(".isldetails_div_destination_switch").html(
              "<span>" +
                (d.target_switch_name == "" || d.target_switch_name == undefined
                  ? "-"
                  : d.target_switch_name) +
                "</span>"
            );
            d3.select(".isldetails_div_speed").html(
              "<span>" +
                (d.max_bandwidth == "" || d.max_bandwidth == undefined ? "-" : d.max_bandwidth / 1000) +
                " Mbps</span>"
            );
            d3.select(".isldetails_div_state").html(
              "<span>" +
                (d.state == "" || d.state == undefined ? "-" : d.state) +
                "</span>"
            );
            d3.select(".isldetails_div_latency").html(
              "<span>" +
                (d.latency == "" || d.latency == undefined ? "-" : d.latency) +
                "</span>"
            );
            d3.select(".isldetails_div_bandwidth").html(
              "<span>" +
                (d.available_bandwidth == "" || d.available_bandwidth == undefined
                  ? "-"
                  : d.available_bandwidth / 1000) +
                " Mbps (" +
                percentage +
                "%)</span>"
            );
            d3.select(".isldetails_div_unidirectional").html(
              "<span>" +
                (d.unidirectional == "" || d.unidirectional == undefined
                  ? "-"
                  : d.unidirectional) +
                "</span>"
            );
            d3.select(".isldetails_div_cost").html(
              "<span>" +
                (d.cost == "" || d.cost == undefined ? "-" : d.cost) +
                "</span>"
            );
          }
        })
        .on("mouseout", function(d, index) {
          $("#topology-hover-txt, #isl_hover").css("display", "none");
          var element = $("#link" + index)[0];
          var availbandwidth = d.available_bandwidth;
          var max_bandwidth = d.max_bandwidth;
          var percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
          if (d.hasOwnProperty("flow_count")) {
            if(d.under_maintenance){
              element.setAttribute("class", "link logical dashed_maintenance_path");
            }  else if (d.affected) {
              element.setAttribute("class", "link logical dashed_path");
            }else {
              element.setAttribute("class", "link logical");
            }
          } else {
            if (
              (d.unidirectional &&
                d.state &&
                d.state.toLowerCase() == "discovered") ||
              (d.state && d.state.toLowerCase() == "failed")
            ) {
              if(d.under_maintenance){
                if(parseInt(percentage) < 50){
                  element.setAttribute("class", "link physical  orange_percentage dashed_maintenance_path");
                }else{
                  element.setAttribute("class", "link physical  dashed_maintenance_path");
                }
                
              } else if (d.affected) {
                element.setAttribute("class", "link physical  dashed_path");
              }else {
                element.setAttribute("class", "link physical ");
              }
            } else {
              if(d.under_maintenance){
                if (parseInt(percentage) < 50) {
                  element.setAttribute("class", "link physical orange_percentage dashed_maintenance_path");
                }else{
                  element.setAttribute("class", "link physical dashed_maintenance_path");
                }
              } else if (d.affected) {
                element.setAttribute("class", "link physical dashed_path");
              }else {
                if (parseInt(percentage) < 50) {
                  element.setAttribute(
                    "class",
                    "link physical orange_percentage "
                  );
                } else {
                  element.setAttribute("class", "link physical ");
                }
              }
            }
          }
  
          if (!$("#topology-hover-txt").is(":hover")) {
            $("#topology-hover-txt, #isl_hover").css("display", "none");
          }
        });
      }
      
      

    graphLinksData.exit().remove();
    this.graphlink = graphNewLink.merge(graphLinksData);
  }
  showLinkDetails = d => {
    localStorage.setItem("linkData", JSON.stringify(d));
    let url = "isl/switch/isl/"+d.source_switch+"/"+d.src_port+"/"+d.target_switch+"/"+d.dst_port;
    var win = window.open(url,'_blank');
    win.focus();
  };
  showFlowDetails = d => {
    let url = "flows?src=" + d.source_switch_name + "&dst=" + d.target_switch_name;
    var win = window.open(url,'_blank');
    win.focus();
  };
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
  private sortLinks() {
    this.graph_data.links.sort(function(a, b) {
      if (a.source > b.source) {
        return 1;
      } else if (a.source < b.source) {
        return -1;
      } else {
        if (a.target > b.target) {
          return 1;
        }
        if (a.target < b.target) {
          return -1;
        } else {
          return 0;
        }
      }
    });
  }

  private setLinkIndexAndNum() {
    for (var i = 0; i < this.graph_data.links.length; i++) {
      if (
        i != 0 &&
        this.graph_data.links[i].source == this.graph_data.links[i - 1].source &&
        this.graph_data.links[i].target == this.graph_data.links[i - 1].target
      ) {
        this.graph_data.links[i].linkindex = this.graph_data.links[i - 1].linkindex + 1;
      } else {
        this.graph_data.links[i].linkindex = 1;
      }
      // save the total number of links between two nodes
      if (
        this.mLinkNum[this.graph_data.links[i].target + "," + this.graph_data.links[i].source] !==
        undefined
      ) {
        this.mLinkNum[
          this.graph_data.links[i].target + "," + this.graph_data.links[i].source
        ] = this.graph_data.links[i].linkindex;
      } else {
        this.mLinkNum[
          this.graph_data.links[i].source + "," + this.graph_data.links[i].target
        ] = this.graph_data.links[i].linkindex;
      }
    }
  }
  ticked() {
	  let ref = this;
    var lookup = {};
    this.graphlink.attr("d", d => {
      var islCount = 0;
      var matchedIndex = 1;
      var key = d.source_switch + "_" + d.target_switch;
      var key1 =  d.target_switch + "_" + d.source_switch;
      var processKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== "undefined") ? key:key1;
      if (
        this.linksSourceArr &&
        typeof this.linksSourceArr[processKey] !== "undefined"
      ) {
        islCount = this.linksSourceArr[processKey].length;
      }
      if (islCount > 1) {
        this.linksSourceArr[processKey].map(function(o, i) {
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
        this.mLinkNum[d.source.index + "," + d.target.index] ||
        this.mLinkNum[d.target.index + "," + d.source.index];

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
    });

    this.graphNodes.attr("transform", function(d) {
      if (d.x && d.y) {
        return "translate(" + d.x + "," + d.y + ")";
      }
    });
  }

  dragStart = () => {
    if (!d3.event.active) this.simulation.alphaTarget(1).stop();
  };

  dragging = (d: any, i) => {
    // this.isDragMove = true;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy;
    this.ticked();
  };

  dragEnd = (d: any, i) => {
    if (!d3.event.active) this.simulation.alphaTarget(0);
  };
}
