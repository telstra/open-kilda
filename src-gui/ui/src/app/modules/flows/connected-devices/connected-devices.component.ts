import { Component, OnInit, Renderer2,ViewChild, OnChanges, SimpleChanges, AfterViewInit, Input } from '@angular/core';
import { FlowsService } from 'src/app/common/services/flows.service';
import { LoaderService } from 'src/app/common/services/loader.service';
import * as d3 from "d3";
import { ISL } from "../../../common/enums/isl.enum";
import { environment } from "../../../../environments/environment";
import { AngularFontAwesomeComponent } from 'angular-font-awesome';

@Component({
  selector: 'app-connected-devices',
  templateUrl: './connected-devices.component.html',
  styleUrls: ['./connected-devices.component.css']
})
export class ConnectedDevicesComponent implements OnInit,OnChanges, AfterViewInit {
  @Input() flowId;
  @Input() connectedDevices;
  @Input() flowDetail;

  
  connectedDeviceData:any;
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
  devices:any;
  links:any;
  graphNode:any;
  graphNodeDevices:any;
  graphLink:any;
  graphNodeGroup:any;
  graphNodeDevicesGroup:any;
  graphLinkGroup:any;
  svgElement: any;
  forceSimulation:any;
  isDragMove:true;
  size:any;
  min_zoom = 0.15;
  max_zoom = 3;
  zoomLevel = 0.15;
  zoomStep = 0.15;
  property : any = 'graphicalviewer';
  graphViewer:boolean=true;
  tabularViewer:boolean=false;
  connectedDevicesTableData:any = {};
  constructor(
    private flowService: FlowsService,
    private loaderService: LoaderService,
  ) { }

  ngOnInit() {
    
    if (this.flowId) {
      if(this.connectedDevices){
        this.getConnectedDevices(this.flowId);        
      }
    } else {
      console.error("Flow Id required");
    }
  }

  toggleView(e){
      if(e.target.checked) {
         this.property = 'tabularViewer';
         this.graphViewer = false ;
      } else {
        this.property = 'graphViewer';   
        this.tabularViewer = false; 
        this.loadTable(this.connectedDeviceData);  
    }
      this[this.property] = true;
  }
  getConnectedDevices(flowId){
    this.flowService.getConnectedDevices(flowId).subscribe(
      data => {
        this.connectedDeviceData = data; 
        this.loadTable(data);
        this.initGraphSimulation(); 
      },
      error => {
        this.connectedDeviceData = null;
        this.loadTable([]);
        console.log('error in data');
      }
    );
  }

  loadTable(data){
    var ref = this;
    this.connectedDevicesTableData = {};
    if(this.connectedDeviceData){
      this.connectedDevicesTableData['source'] = [];
      this.connectedDevicesTableData['dst'] = [];
      if(this.connectedDeviceData.source && this.connectedDeviceData.source.lldp && this.connectedDeviceData.source.lldp.length){
        for(let i = 0; i < this.connectedDeviceData.source.lldp.length; i++){
          let data = this.connectedDeviceData.source.lldp[i];
          this.connectedDevicesTableData['source'].push(data);          
        }
      }
      if(this.connectedDeviceData.source && this.connectedDeviceData.source.arp && this.connectedDeviceData.source.arp.length){
        for(let i = 0; i < this.connectedDeviceData.source.arp.length; i++){
          let data = this.connectedDeviceData.source.arp[i];
          this.connectedDevicesTableData['source'].push(data);          
        }
      }

      if(this.connectedDeviceData.destination && this.connectedDeviceData.destination.lldp && this.connectedDeviceData.destination.lldp.length){
        for(let i = 0; i < this.connectedDeviceData.destination.lldp.length; i++){
          let data = this.connectedDeviceData.destination.lldp[i];
          this.connectedDevicesTableData['dst'].push(data);
        }
      }
      if(this.connectedDeviceData.destination && this.connectedDeviceData.destination.arp && this.connectedDeviceData.destination.arp.length){
        for(let i = 0; i < this.connectedDeviceData.destination.arp.length; i++){
          let data = this.connectedDeviceData.destination.arp[i];
          this.connectedDevicesTableData['dst'].push(data);
        }
      }
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
    if(!d.connected){
        d.fixed = true;
    }
  };

  horizontallyBound = (parentDiv, childDiv) => {
    let parentRect: any = parentDiv.getBoundingClientRect();
    let childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  };
  initGraphSimulation(){
    this.nodes = [{ "x": -208.992345, "y": -6556.9998 ,connected:false,switch_id_value:this.flowDetail.source_switch,switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name,port:this.flowDetail.src_port,vlan:this.flowDetail.src_vlan},
                  { "x": 595.98896,  "y":  -6556.9998,connected:false,switch_id_value:this.flowDetail.target_switch,switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name,port:this.flowDetail.dst_port,vlan:this.flowDetail.dst_vlan }
                ];
    this.links = [{
                 connected:false,
                source:{switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name},
                target:{switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name}
               }];
  this.devices = [];
    if(this.connectedDeviceData){
        if(this.connectedDeviceData.source && this.connectedDeviceData.source.lldp && this.connectedDeviceData.source.lldp.length){
          for(let i = 0; i < this.connectedDeviceData.source.lldp.length; i++){
            let data = this.connectedDeviceData.source.lldp[i];
            let node = data;
            node['switch_id'] = data.mac_address;
            node['connected'] = true;
            this.nodes.push(node);
            var link = {
              connected:true,
              source:{switch_id:data.mac_address,mac_address:data.mac_address},
              target:{ switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name }
            }
            this.links.push(link);
          }
        }

        if(this.connectedDeviceData.source && this.connectedDeviceData.source.arp && this.connectedDeviceData.source.arp.length){
          for(let i = 0; i < this.connectedDeviceData.source.arp.length; i++){
            let data = this.connectedDeviceData.source.arp[i];
            let node = data;
            node['switch_id'] = data.mac_address;
            node['connected'] = true;
            this.nodes.push(node);
            var link = {
              connected:true,
              source:{switch_id:data.mac_address,mac_address:data.mac_address},
              target:{ switch_id:this.flowDetail.source_switch+"_"+this.flowDetail.src_port,name:this.flowDetail.source_switch_name }
            }
            this.links.push(link);
          }
        }

        if(this.connectedDeviceData.destination && this.connectedDeviceData.destination.lldp && this.connectedDeviceData.destination.lldp.length){
          for(let i = 0; i < this.connectedDeviceData.destination.lldp.length; i++){
            let data = this.connectedDeviceData.destination.lldp[i];
            let node = data;
            node['switch_id'] = data.mac_address;
            node['connected'] = true;
            this.nodes.push(node);
            var link = {
              connected:true,
              source:{ switch_id:data.mac_address,mac_address:data.mac_address},
              target:{ switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name }
            }
            this.links.push(link);
          }
        }

        if(this.connectedDeviceData.destination && this.connectedDeviceData.destination.arp && this.connectedDeviceData.destination.arp.length){
          for(let i = 0; i < this.connectedDeviceData.destination.arp.length; i++){
            let data = this.connectedDeviceData.destination.arp[i];
            let node = data;
            node['switch_id'] = data.mac_address;
            node['connected'] = true;
            this.nodes.push(node);
            var link = {
              connected:true,
              source:{ switch_id:data.mac_address,mac_address:data.mac_address},
              target:{ switch_id:this.flowDetail.target_switch+"_"+this.flowDetail.dst_port,name:this.flowDetail.target_switch_name }
            }
            this.links.push(link);
          }
        }
    }

    this.processLinks();
    this.svgElement = d3.select("svg#connectedGraph");
    this.width = $("#connectedGraphwrapper")[0].clientWidth || window.innerWidth;;
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
      });
    this.forceSimulation = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
        return 20;
      }))
      .force("charge_force",d3.forceManyBody().strength(-500))
      .force("xPos", d3.forceX(this.width /2))
      .force("yPos", d3.forceY(this.height / 2));
    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force("link", d3.forceLink().links(this.links).distance((d:any)=>{
      let distance = 150;
       if(!d.connected){
         distance = 250;
       }
        return distance; 
     }).strength(0.1));
     this.forceSimulation.on("tick", () => {  
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
      
  }
  processLinks(){
    var nodelength = this.nodes.length;
    var nodedevicelength = this.devices.length;
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
        if(d.connected){
          return ISL.MOVED;
        }
        return ISL.DISCOVERED;
      }).attr("cursor","pointer");
      graphLinksData.exit().remove();
      this.graphLink = graphNewLink.merge(graphLinksData);
  }
 
  insertNodes(){
    let ref = this;
    let graphNodesData = this.graphNodeGroup.selectAll("g.node").data(this.nodes);
    let graphNodeElement = graphNodesData.enter().append("g").attr("class", "node").call(
      d3
        .drag()
        .on("start", this.dragStart)
        .on("drag", this.dragging)
        .on("end", this.dragEnd)
  );
    
    graphNodesData.exit().remove();
                graphNodeElement.append("circle").
                      attr("r",  this.graphOptions.radius)
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
                          if(d.connected){
                            return environment.assetsPath + "/images/server-regular.svg";
                          }
                          return environment.assetsPath + "/images/switch.png";
                      })
                      .attr("x", function(d) {
                        if(d.connected){
                          return -20;
                        }
                        return -29;
                      })
                      .attr("y", function(d) {
                        if(d.connected){
                          return -20;
                        }
                        return -29;
                      })
                      .attr("height",function(d){
                        if(d.connected){
                          return 38;
                        }
                        return 58;
                      })
                      .attr("width", function(d){
                        if(d.connected){
                          return 38;
                        }
                        return 58;
                      })
                      .attr("id", function(d, index) {
                        return "image_" + index;
                      }).attr("cursor","pointer").on('mouseover',function(d,index){
                        var element = document.getElementById("circle_" + index);
                          var rec: any = element.getBoundingClientRect();
                          if(d.connected){
                            $("#tooltip_connected_device").css("display", "block");
                            $("#tooltip_connected_device").css("top", rec.y + "px");
                            $("#tooltip_connected_device").css("left", (rec.x) + "px");
                            d3.select("#mac_address").html(d.mac_address);
                            d3.select("#chassis_id").html(d.chassis_id);
                            d3.select("#port_id").html(d.port_id);
                            d3.select("#port_description").html(d.port_description);
                            d3.select("#ttl").html(d.ttl);
                            d3.select("#system_name").html(d.system_name);
                            d3.select("#system_description").html(d.system_description);
                            d3.select("#system_capabilities").html(d.system_capabilities);
                            d3.select("#management_address").html(d.management_address);
                           
                            var bound = ref.horizontallyBound(
                              document.getElementById("connectedGraphwrapper"),
                              document.getElementById("tooltip_connected_device")
                            );
                            if (bound) {
                              $("#tooltip_connected_device").removeClass("left");
                            } else {
                              var left = rec.x - (300 + 100); 
                              $("#tooltip_connected_device").css("left", left + "px");
                              $("#tooltip_connected_device").addClass("left");
                            }
                          }
                          
                      }).on('mouseout',function(d,index){
                         $("#tooltip_connected_device").css("display", "none");
                      });;
      
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

  ngOnChanges(change: SimpleChanges){
   
  }
  ngAfterViewInit(){}

}
