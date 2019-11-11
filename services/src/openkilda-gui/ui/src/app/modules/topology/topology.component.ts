import {
  Component,
  OnInit,
  ViewChild,
  AfterViewInit,
  HostListener,
  ElementRef,
  ViewContainerRef,
  Renderer2,
  OnDestroy
} from "@angular/core";
import { TopologyService } from "../../common/services/topology.service";
import { SwitchService } from "../../common/services/switch.service";
import { UserService } from "../../common/services/user.service";
import { ISL } from "../../common/enums/isl.enum";
import { CommonService } from "../../common/services/common.service";
import * as d3 from "d3";
import { NgbTypeahead } from "@ng-bootstrap/ng-bootstrap";
import { TopologyView } from "../../common/data-models/topology-view";
import { FlowsService } from "../../common/services/flows.service";
import { Observable } from "rxjs";
import { debounceTime, distinctUntilChanged, map } from "rxjs/operators";
import { environment } from "../../../environments/environment";
import { LoaderService } from "../../common/services/loader.service";
import { ToastrService } from "ngx-toastr";
import { Title } from '@angular/platform-browser';
import { scaleBand } from "d3";
declare var jQuery: any;

@Component({
  selector: "app-topology",
  templateUrl: "./topology.component.html",
  styleUrls: ["./topology.component.css"]
})
export class TopologyComponent implements OnInit, AfterViewInit, OnDestroy {
  @HostListener("blur", ["$event"])
  onBlur(event: Event) {
    event.stopImmediatePropagation();
  }

  nodes = [];
  links = [];
  flows = [];

  searchCase: boolean = false;


  searchView: boolean = false;
  searchModel: any = "";
  searchHidden = false;
  autoRefreshTimerInstance: any;

  width: number;
  height: number;
  graphShow=false;
  min_zoom = 0.15;
  scaleLimit = 0.05;
  max_zoom = 3;
  zoomLevel = 0.15;
  zoomStep = 0.15;
  translateX = 0;
  translateY = 0;

  linksSourceArr = [];
  new_nodes = false;
  optArray = [];
  size: any;
  forceSimulation: any;
  force: any;
  g: any;
  drag: any;
  svgElement: any;
  zoom: any;
  mLinkNum: any = {};
  linkedByIndex = {};
  isDragMove = true;
  flagHover = true;

  graphdata = { switch: [], isl: [], flow: [] };

  syncCoordinates = null;

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

  viewOptions: TopologyView;

  graphLink: any;
  graphCircle: any;
  graphText: any;
  graphNode: any;
  graphFlowCount: any;

  graphNodeGroup: any;
  graphLinkGroup: any;
  graphFlowGroup: any;

  constructor(
    private topologyService: TopologyService,
    private switchService: SwitchService,
    private userService: UserService,
    private commonService: CommonService,
    private flowService: FlowsService,
    private renderer: Renderer2,
    private appLoader: LoaderService,
    private toaster :ToastrService,
    private titleService: Title
  ) {}

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Topology');
    this.appLoader.show("Loading Topology");
    this.viewOptions = this.topologyService.getViewOptions();

    this.forceSimulation = this.initSimulation();
    let query = {_:new Date().getTime()};
    this.userService.getSettings(query).subscribe(
      coordinates => {
        this.topologyService.setCoordinates(coordinates);
        this.topologyService.setCoordinateChangeStatus('NO');
        this.loadSwitchList();
      },
      error => {
        this.topologyService.setCoordinates(null);
        this.topologyService.setCoordinateChangeStatus('NO');
        this.loadSwitchList();
      }
    );

    this.topologyService.setCoordinateChangeStatus('NO');
    this.topologyService.settingReceiver.subscribe(this.onViewSettingUpdate);
    this.topologyService.autoRefreshReceiver.subscribe(
      this.onAutoRefreshSettingUpdate
    );

    
  }

  search = (text$: Observable<string>) =>
    text$.pipe(
      debounceTime(200),
      distinctUntilChanged(),
      map(term => term.length < 1 ? []
        : this.optArray.filter(v => v.toLowerCase().indexOf(term.toLowerCase()) > -1).slice(0, 10))
  )

  initSimulation() {
    this.width = window.innerWidth;
    this.height = window.innerHeight;
   
    this.svgElement = d3.select("svg");
    this.svgElement.style('cursor','move');
    this.svgElement.attr("width",this.width);
    this.svgElement.attr("height",this.height);

    this.g = this.svgElement.append("g");
    
  

    this.graphLinkGroup = this.g
      .append("g")
      .attr("id", `links`)
      .attr("class", "links");

    this.graphNodeGroup = this.g
      .append("g")
      .attr("id", `nodes`)
      .attr("class", "nodes");

    this.graphFlowGroup = this.g
      .append("g")
      .attr("id", `flowcounts`)
      .attr("class", "flowcounts");

    this.zoom = d3
      .zoom()
      .scaleExtent([this.scaleLimit, this.max_zoom])
      .extent([[0, 0], [this.width, this.height]])
      .on("zoom", () => {
        // this.forceSimulation.stop();
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
        $("#topology-hover-txt, #switch_hover").css("display", "none");
        $("#topology-click-txt").css("display", "none");
        
      });

    this.size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));

    let result = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
        return 20;
      }))
      .force("charge_force",d3.forceManyBody().strength(-1000))
      .force("xPos", d3.forceX(this.width /2))
      .force("yPos", d3.forceY(this.height / 2));
     
    return result;
  }

  loadSwitchList = () => {
    this.switchService.getSwitchList().subscribe(switches => {
      this.graphdata.switch = switches || [];
      if(this.graphdata.switch.length > 0){
        this.loadSwitchLinks();
      }else{
        this.toaster.info("No Switch Available","Information");
        this.appLoader.hide();
      }
    },err=>{
      this.appLoader.hide();
      this.toaster.info("No Switch Available","Information");
    });
  };

  loadSwitchLinks = () => {
    this.switchService.getSwitchLinks().subscribe(
      links => {     
        try {    
         if(links){
          this.graphdata.isl = links || [];
          this.topologyService.setLinksData(links);
         }
        
          if (this.viewOptions.FLOW_CHECKED) {
            this.loadFlowCount();
          } else {
            this.initGraph();
          }
        } catch (err) {
          this.initGraph();
        }
      },
      error => {
        this.loadFlowCount();
      }
    );
  };

  loadFlowCount = () => {
    this.flowService.getFlowCount().subscribe(
      flow => {
        this.graphdata.flow = flow || [];
        this.initGraph();
      },
      error => {
        this.initGraph();
      }
    );
  };

  initGraph = () => {
    let ref = this;

    if (
      this.graphdata.switch.length == 0 &&
      this.graphdata.isl.length == 0 &&
      this.graphdata.flow.length == 0
    ) {
      this.appLoader.hide();
    }

    /*
		 * A force layout requires two data arrays. The first array, here named
		 * nodes, contains the object that are the focal point of the visualization.
		 * The second array, called links below, identifies all the links between
		 * the nodes.
		 */
    this.nodes = this.graphdata.switch;
    this.links = this.graphdata.isl;
    this.flows = this.graphdata.flow;
    
       
    this.linksSourceArr = [];

    var linksArr = [];
    if (this.nodes.length < 50) {
      this.min_zoom = 0.5;
      this.zoom.scaleExtent([this.scaleLimit, this.max_zoom]);
    }
    if (this.links.length > 0) {
      try {
        var result = this.commonService.groupBy(this.links, function(item) {
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
   
    if (this.flows.length > 0) {
      this.links = this.links.concat(this.flows);
    }
    this.optArray = this.optArray.sort();

    // calculating nodes
    var nodelength = this.nodes.length;
    var linklength = this.links.length;
    for (var i = 0; i < nodelength; i++) {
      this.optArray.push((this.nodes[i].name));
      for (var j = 0; j < linklength; j++) {
        if (
          this.nodes[i].switch_id == this.links[j]["source_switch"] &&
          this.nodes[i].switch_id == this.links[j]["target_switch"]
        ) {
          this.links[j].source = i;
          this.links[j].target = i;
        } else {
          var key = this.links[j]["source_switch"] +"_"+this.links[j]["target_switch"]; 
          var key1 = this.links[j]["target_switch"] +"_"+this.links[j]["source_switch"]; 
          var processKey = this.linksSourceArr && typeof this.linksSourceArr[key] !='undefined' ? key: key1;
          var sourceObj = processKey.split("_")[0];
          var targetObj = processKey.split("_")[1];
          if (this.nodes[i].switch_id == sourceObj) {
           this.links[j].source = i;
          } else if (
            this.nodes[i].switch_id == targetObj
          ) {
            this.links[j].target = i;
          }
        }
      }
    }
    
    this.sortLinks();
    this.setLinkIndexAndNum();

    // this.links.forEach(function(d, index, object) {
    //   if (
    //     typeof ref.nodes[d.source] === "undefined" ||
    //     typeof ref.nodes[d.target] === "undefined"
    //   ) {
    //     object.splice(index, 1);
    //   }
    //   ref.linkedByIndex[d.source + "," + d.target] = true;
    // });
   
    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force("link", d3.forceLink().links(this.links).distance((d:any)=>{
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
    this.forceSimulation.stop();
    this.forceSimulation.on("tick", () => {      
      this.repositionNodes();
      this.tick();
     });
    this.drag = d3
      .drag()
      .on("start", this.dragStart)
      .on("drag", this.dragging)
      .on("end", this.dragEnd);
    this.insertLinks(this.links);
    this.insertNodes(this.nodes);
    this.insertCircles();
    this.svgElement.call(this.zoom);    
    this.svgElement.on("dblclick.zoom", null);
    this.forceSimulation.restart();
    this.forceSimulation.on("end",()=>{
      this.appLoader.hide();  
      this.graphShow = true;
      this.onViewSettingUpdate(this.viewOptions, true);
      this.zoomFit();
      let positions = this.topologyService.getCoordinates();
      if(!positions){
        this.zoomReset();
      }
     })
   
  };

  private insertNodes(nodes) {
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
      .attr("class", "switchname hide");
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
          //return ref.graphOptions.nominal_base_node_size;
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
      .attr("cursor", "pointer")
      .on("mouseover", function(d, index) {
        $("#isl_hover").css("display", "none");

        /*var cName : any = document.getElementById("circle_" + d.switch_id).className;
          let circleClass = cName.baseVal;*/

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

    this.graphNode = graphNodeElement.merge(graphNodesData);
  }
  private insertLinks(links) {
    let ref = this;
    let graphLinksData = this.graphLinkGroup.selectAll("path.link").data(links);

    let graphNewLink = graphLinksData
      .enter()
      .append("path")
      .attr("class", function(d, index) {
        var availbandwidth = d.available_bandwidth;
        var speed = d.speed;
        var percentage = ref.commonService.getPercentage(availbandwidth, speed);
        if (d.hasOwnProperty("flow_count")) {
          return "link logical";
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == "discovered") ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if (d.affected) {
              return "link physical down dashed_path";
            } else {
              return "link physical down";
            }
          } else {
            if (d.affected) {
              return "link physical dashed_path";
            } else {
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
      })
      .on("mouseover", function(d, index) {
        $("#switch_hover").css("display", "none");
        var element = $("#link" + index)[0];
        var availbandwidth = d.available_bandwidth;
        var speed = d.speed;

        var percentage = ref.commonService.getPercentage(availbandwidth, speed);
        if (d.hasOwnProperty("flow_count")) {
          if (d.affected) {
            element.setAttribute("class", "link logical overlay dashed_path");
          } else {
            element.setAttribute("class", "link logical overlay");
          }
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == "discovered") ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if (d.affected) {
              element.setAttribute(
                "class",
                "link physical dashed_path pathoverlay"
              );
            } else {
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
            if (d.affected) {
              element.setAttribute(
                "class",
                "link physical overlay dashed_path"
              );
            } else {
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
              (d.speed == "" || d.speed == undefined ? "-" : d.speed / 1000) +
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
        var speed = d.speed;
        var percentage = ref.commonService.getPercentage(availbandwidth, speed);
        if (d.hasOwnProperty("flow_count")) {
          if (d.affected) {
            element.setAttribute("class", "link logical dashed_path");
          } else {
            element.setAttribute("class", "link logical");
          }
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == "discovered") ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if (d.affected) {
              element.setAttribute("class", "link physical down dashed_path");
            } else {
              element.setAttribute("class", "link physical down");
            }
          } else {
            if (d.affected) {
              element.setAttribute("class", "link physical dashed_path");
            } else {
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
      })
      .on("click", function(d, index) {
        var element = $("#link" + index)[0];
        var availbandwidth = d.available_bandwidth;
        var speed = d.speed;
        var percentage = ref.commonService.getPercentage(availbandwidth, speed);
        if (d.hasOwnProperty("flow_count")) {
          if (d.affected) {
            element.setAttribute("class", "link logical overlay dashed_path");
          } else {
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
            if (d.affected) {
              element.setAttribute(
                "class",
                "link physical pathoverlay dashed_path"
              );
            } else {
              element.setAttribute("class", "link physical pathoverlay");
            }
          } else {
            if (d.affected) {
              element.setAttribute(
                "class",
                "link physical overlay dashed_path"
              );
            } else {
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

    graphLinksData.exit().remove();
    this.graphLink = graphNewLink.merge(graphLinksData);
  }

  insertCircles() {
    let ref = this;
    var filteredLinks = [];
    this.links.map(function(l, i) {
      if (l && l.hasOwnProperty("flow_count")) {
        var obj = l;
        obj.index = i;
        filteredLinks.push(obj);
      }
    });

    let graphFlowCountData = this.graphFlowGroup
      .selectAll("g.flow-circle")
      .data(filteredLinks);

    let graphCountElement = graphFlowCountData
      .enter()
      .append("g")
      .attr("class", "flow-circle");
    graphFlowCountData.exit().remove();

    graphCountElement
      .append("circle")
      .attr("dy", ".35em")
      .style("font-size", this.graphOptions.nominal_text_size + "px")
      .attr("r", function(d, index) {
        let r: any;
        var element = $("#link" + d.index)[0];
        var f = d.flow_count;
        if (
          element.getAttribute("stroke") == "#228B22" ||
          element.getAttribute("stroke") == "green"
        ) {
          if (f < 10) {
            r = 10;
          } else if (f >= 10 && f < 100) {
            r = 12;
          } else {
            r = 16;
          }
          return r;
        }
      })
      .on("mouseover", function(d, index) {
        var element = $("#link" + index)[0];
        var availbandwidth = d.available_bandwidth;
        let classes = "";
        var speed = d.speed;
        var percentage = ref.commonService.getPercentage(availbandwidth, speed);
        if (d.hasOwnProperty("flow_count")) {
          classes = "link logical overlay";
        } else {
          if (parseInt(percentage) < 50) {
            classes = "link physical orange_percentage overlay";
          } else {
            classes = "link physical overlay";
          }
        }
        element.setAttribute("class", classes);
      })
      .on("mouseout", function(d, index) {
        var element = $("#link" + index)[0];
        var availbandwidth = d.available_bandwidth;
        let classes = "";
        var speed = d.speed;
        var percentage = ref.commonService.getPercentage(
          availbandwidth,
          speed
        );
        if (d.hasOwnProperty("flow_count")) {
          classes = "link logical";
        } else {
          if (parseInt(percentage) < 50) {
            classes = "link physical orange_percentage";
          } else {
            classes = "link physical";
          }
        }
        element.setAttribute("class", classes);
      })
      .on("click", function(d, index) {
        ref.showFlowDetails(d);
      })
      .attr("class", "linecircle")
      .attr("id", function(d, index) {
        var id = "_" + index;
        return id;
      })
      .attr("fill", function(d) {
        return "#d3d3d3";
      })
      .call(this.drag);

    graphCountElement
      .append("text")
      .attr("dx", function(d) {
        let r: any;
        var f = d.flow_count;
        if (f < 10) {
          r = -3;
        } else if (f >= 10 && f < 100) {
          r = -6;
        } else {
          r = -9;
        }
        return r;
      })
      .attr("dy", function(d) {
        return 5;
      })
      .attr("fill", function(d) {
        return "black";
      })
      .text(function(d) {
        var value = d.flow_count;
        return value;
      });

    this.graphFlowCount = graphCountElement.merge(graphFlowCountData);
  }

  repositionNodes = () => {
    let positions = this.topologyService.getCoordinates();
    if (positions) {
      d3.selectAll("g.node").attr("transform", function(d: any) {
        try {
          d.x = positions[d.switch_id][0];
          d.y = positions[d.switch_id][1];
        } catch (e) {}
        if (d.x && d.y) return "translate(" + d.x + "," + d.y + ")";
      });
    }
  };

  private sortLinks() {
    this.links.sort(function(a, b) {
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
    for (var i = 0; i < this.links.length; i++) {
      if (
        i != 0 &&
        this.links[i].source == this.links[i - 1].source &&
        this.links[i].target == this.links[i - 1].target
      ) {
        this.links[i].linkindex = this.links[i - 1].linkindex + 1;
      } else {
        this.links[i].linkindex = 1;
      }
      // save the total number of links between two nodes
      if (
        this.mLinkNum[this.links[i].target + "," + this.links[i].source] !==
        undefined
      ) {
        this.mLinkNum[
          this.links[i].target + "," + this.links[i].source
        ] = this.links[i].linkindex;
      } else {
        this.mLinkNum[
          this.links[i].source + "," + this.links[i].target
        ] = this.links[i].linkindex;
      }
    }
  }

  private processNodesData(newNodes, removedNodes, response) {
    this.nodes.forEach(function(d) {
      for (var i = 0, len = response.length; i < len; i++) {
        if (d.switch_id == response[i].switch_id) {
          d.state = response[i].state;
          var classes = "circle blue";
          if (d.state && d.state.toLowerCase() == "deactivated") {
            classes = "circle red";
          }
          var element = document.getElementById("circle_" + d.switch_id);
          if (element) {
            element.setAttribute("class", classes);
          }
          break;
        }
      }
    });

    if (
      (newNodes && newNodes.length) ||
      (removedNodes && removedNodes.length)
    ) {
      if (newNodes && newNodes.length) {
        this.nodes = this.nodes.concat(newNodes);
        this.new_nodes = true;
      }
      if (removedNodes && removedNodes.length) {
        this.new_nodes = true;
        this.nodes = this.nodes.filter(function(node) {
          var foundFlag = false;
          for (var i = 0; i < removedNodes.length; i++) {
            if (removedNodes[i].switch_id == node.switch_id) {
              foundFlag = true;
              break;
            }
          }
          return !foundFlag;
        });
      }
    } else {
      this.new_nodes = false;
    }
  }

  processLinksData(newLinks, removedLinks, response) {
    let ref = this;
    var classes = "";
    this.links.forEach(function(d, index) {
      for (var i = 0, len = response.length; i < len; i++) {
        if (
          d.source_switch == response[i].source_switch &&
          d.target_switch == response[i].target_switch &&
          d.src_port == response[i].src_port &&
          d.dst_port == response[i].dst_port
        ) {
          d.state = response[i].state;
          var availbandwidth = d.available_bandwidth;
          var speed = d.speed;
          //var percentage = 20; //common.getPercentage(availbandwidth,speed);
          var percentage = ref.commonService.getPercentage(availbandwidth,speed);
          if (response[i].affected) {
            d["affected"] = response[i].affected;
          } else {
            d["affected"] = false;
          }
          d.unidirectional = response[i].unidirectional;
          if (
            d.unidirectional ||
            (d.state && d.state.toLowerCase() == "failed")
          ) {
            if (d.affected) {
              classes = "link physical down dashed_path";
            } else {
              classes = "link physical down";
            }
          } else {
            if (d.affected) {
              classes = "link physical dashed_path";
            } else {
              if (parseInt(percentage) < 50) {
                classes = "link physical orange_percentage";
              } else {
                classes = "link physical ";
              }
            }
          }
          var element = document.getElementById("link" + index);

          var stroke = ISL.FAILED;

          if (
            d.unidirectional &&
            d.state &&
            d.state.toLowerCase() == "discovered"
          ) {
            stroke = ISL.UNIDIR;
          } else if (d.state && d.state.toLowerCase() == "discovered") {
            stroke = ISL.DISCOVERED;
          }

          if (element) {
            element.setAttribute("class", classes);
            element.setAttribute("stroke", stroke);
          }

          break;
        }
      }
    });

    if (
      (newLinks && newLinks.length) ||
      (removedLinks && removedLinks.length) ||
      this.new_nodes
    ) {
      this.new_nodes = false;
      this.restartGraphWithNewIsl(newLinks, removedLinks);
    }
  }

  getSwitchList() {
    this.switchService.getSwitchList().subscribe(
      response => {
        let switchArr: any = [];
        //if (this.nodes.length != response.length) {
        // new switch is added
        switchArr = this.getNewSwitch(this.nodes, response);
        //}
        var newNodes = switchArr["added"] || [];
        var removedNodes = switchArr["removed"] || [];
        this.processNodesData(newNodes, removedNodes, response);

      },
      error => {
        this.appLoader.hide();
      
      }
    );
  }

  getSwitchLinks() {
    this.switchService.getSwitchLinks().subscribe(
      response => {
        var linksArr: any = [];
        //if (this.links.length !== response.length) {
        linksArr = this.getNewLinks(this.links, response);
        //}
        var newLinks = linksArr["added"] || [];
        var removedLinks = linksArr["removed"] || [];

        this.processLinksData(newLinks, removedLinks, response);
      },
      error => {}
    );
  }

  /** get removed and newly added switch list */
  getNewSwitch(nodes, response) {
    var nodesArr = { added: [], removed: [] };
    for (var i = 0; i < response.length; i++) {
      var foundFlag = false;
      for (var j = 0; j < nodes.length; j++) {
        if (nodes[j].switch_id == response[i].switch_id) {
          foundFlag = true;
        }
      }
      if (!foundFlag) {
        nodesArr["added"].push(response[i]);
      }
    }
    for (var i = 0; i < nodes.length; i++) {
      var foundFlag = false;
      for (var j = 0; j < response.length; j++) {
        if (response[j].switch_id == nodes[i].switch_id) {
          foundFlag = true;
        }
      }
      if (!foundFlag) {
        nodesArr["removed"].push(nodes[i]);
      }
    }
    return nodesArr;
  }

  /** get removed and newly added switch links  */
  getNewLinks(links, response) {
    var linksArr = { added: [], removed: [] };
    for (var i = 0; i < response.length; i++) {
      var foundFlag = false;
      for (var j = 0; j < links.length; j++) {
        if (
          links[j].source_switch == response[i].source_switch &&
          links[j].target_switch == response[i].target_switch &&
          links[j].src_port == response[i].src_port &&
          links[j].dst_port == response[i].dst_port
        ) {
          foundFlag = true;
        }
      }
      if (!foundFlag) {
        linksArr["added"].push(response[i]);
      }
    }
    // checking for removed links
    for (var i = 0; i < links.length; i++) {
      var foundFlag = false;
      for (var j = 0; j < response.length; j++) {
        if (
          links[i].source_switch == response[j].source_switch &&
          links[i].target_switch == response[j].target_switch &&
          links[i].src_port == response[j].src_port &&
          links[i].dst_port == response[j].dst_port
        ) {
          foundFlag = true;
        }
      }

      if (!foundFlag) {
        linksArr["removed"].push(links[i]);
      }
    }
    return linksArr;
  }

  restartGraphWithNewIsl(newLinks, removedLinks) {
    this.optArray = [];
    let ref = this;
    try {
      var result = this.commonService.groupBy(newLinks, function(item) {
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
    var nodelength = this.nodes.length;
    var linklength = newLinks.length;
    for (var i = 0; i < nodelength; i++) {
      this.optArray.push(this.nodes[i].name);
      for (var j = 0; j < linklength; j++) {
        if (
          this.nodes[i].switch_id == newLinks[j]["source_switch"] &&
          this.nodes[i].switch_id == newLinks[j]["target_switch"]
        ) {
          newLinks[j].source = i;
          newLinks[j].target = i;
        } else {
          if (this.nodes[i].switch_id == newLinks[j]["source_switch"]) {
            newLinks[j].source = i;
          } else if (this.nodes[i].switch_id == newLinks[j]["target_switch"]) {
            newLinks[j].target = i;
          }
        }
      }
    }
    this.links = this.links.concat(newLinks);
    // splice removed links
    if (removedLinks && removedLinks.length) {
      this.links = this.links.filter(function(d) {
        var foundFlag = false;
        for (var i = 0; i < removedLinks.length; i++) {
          if (
            d.source_switch == removedLinks[i].source_switch &&
            d.target_switch == removedLinks[i].target_switch &&
            d.src_port == removedLinks[i].src_port &&
            d.dst_port == removedLinks[i].dst_port
          ) {
            foundFlag = true;
            var key = d.source_switch + "_" + d.target_switch;
            try{  
              ref.linksSourceArr[key].splice(0, 1);
            }catch(err){

            }
            break;
          }
        }
        return !foundFlag;
      });
    }
    this.appLoader.show("Re-loading topology with new switch or isl");
    this.graphShow = false;

    this.forceSimulation = d3
    .forceSimulation()
    .velocityDecay(0.2)
    .force("charge_force",d3.forceManyBody().strength(-1000))
    .force("xPos", d3.forceX(this.width /2))
    .force("yPos", d3.forceY(this.height / 2));
    
    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force("link", d3.forceLink().links(this.links).distance(200).strength(1));
    this.forceSimulation.stop();
   
    this.forceSimulation.on("tick",()=>{
      this.repositionNodes();
      this.tick();
      this.zoomFit();
    });

    this.insertLinks(this.links);
    this.insertNodes(this.nodes);
    this.insertCircles();  
    this.forceSimulation.restart(); 

    this.forceSimulation.on("end",()=>{
      this.appLoader.hide();  
      this.graphShow = true;
      this.onViewSettingUpdate(this.viewOptions, true);
      this.zoomFit();
    });

  }

  restartAutoRefreshWithNewSettings(duration) {
    this.autoRefreshTimerInstance = setInterval(() => {
      if(this.viewOptions.FLOW_CHECKED){
        this.getSwitchList();
      }else{
        this.getSwitchList();
        this.getSwitchLinks();
      }
    }, duration * 1000);
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

  tick = () => {
    let ref = this;
    var lookup = {};
    this.graphLink.attr("d", d => {
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

    this.graphNode.attr("transform", function(d) {
      if (d.x && d.y) {
        return "translate(" + d.x + "," + d.y + ")";
      }
    });

    this.graphFlowCount.attr("transform", function(d, index) {
      var xvalue = (d.source.y + d.target.y) / 2;
      var yvalue = (d.source.x + d.target.x) / 2;
      if (d.source_switch == d.target_switch) {
        return "translate(" + (yvalue + 70) + "," + (xvalue - 70) + ")";
      } else {
        return "translate(" + yvalue + "," + xvalue + ")";
      }
    });
  };

  updateCoordinates = () => {
    var coordinates = {};
    this.nodes.forEach(function(d) {
      coordinates[d.switch_id] = [
        Math.round(d.x * 100) / 100,
        Math.round(d.y * 100) / 100
      ];
    });

    this.topologyService.setCoordinates(coordinates);
    this.syncUserCoordinatesChanges();
  };

  dragStart = () => {
    if (!d3.event.active) this.forceSimulation.alphaTarget(1).stop();
    jQuery('#topology-hover-txt').hide();
    jQuery('#topology-click-txt').hide();
  };

  dragging = (d: any, i) => {
    jQuery('#topology-hover-txt').hide();
    jQuery('#topology-click-txt').hide();
    this.isDragMove = true;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy;
    this.tick();
  };

  dragEnd = (d: any, i) => {
    if (!d3.event.active) this.forceSimulation.alphaTarget(0);
    this.flagHover = false;
    d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    this.tick();
    this.updateCoordinates();
  };

  horizontallyBound = (parentDiv, childDiv) => {
    let parentRect: any = parentDiv.getBoundingClientRect();
    let childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  };

  showFlowDetails = d => {
    let url = "flows?src=" + d.source_switch_name + "&dst=" + d.target_switch_name;
    window.location.href = url;
  };

  showSwitchDetails = d => {
    localStorage.setItem("switchDetailsJSON", JSON.stringify(d));
    window.location.href = "switches/details/" + d.switch_id;
  };

  showLinkDetails = d => {
    localStorage.setItem("linkData", JSON.stringify(d));
    let url = "isl/switch/isl/"+d.source_switch+"/"+d.src_port+"/"+d.target_switch+"/"+d.dst_port;
    window.location.href = url;
  };

  zoomFn = direction => {
    if (direction == 1) {
      this.forceSimulation.stop();
      if (this.zoomLevel + this.zoomStep <= this.max_zoom) {
        this.svgElement
          .transition()
          .duration(350)
          .call(this.zoom.scaleTo, this.zoomLevel + this.zoomStep);
      }
    } else if (direction == -1) {
      this.forceSimulation.stop();
      if (this.zoomLevel - this.zoomStep >= this.scaleLimit) {
        this.svgElement
          .transition()
          .duration(350)
          .call(this.zoom.scaleTo, this.zoomLevel - this.zoomStep);
      }
    }
  };

  randomPoint = (min, max) => {
    let num1 = Math.random() * (max - min) + min;
    var num = Math.floor(Math.random() * 99) + 1;
    return (num1 *= Math.floor(Math.random() * 2) == 1 ? 1 : -1);
  };

  zoomReset = () =>{
    this.topologyService.setCoordinates(null);
    let coordinates = {};
    this.forceSimulation = d3
    .forceSimulation()
    .velocityDecay(0.2)
    .force("charge_force",d3.forceManyBody().strength(-1000))
    .force("xPos", d3.forceX(this.width /2))
    .force("yPos", d3.forceY(this.height / 2));
    
    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force("link", d3.forceLink().links(this.links).distance((d:any)=>{
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
    this.forceSimulation.stop();
    this.insertLinks(this.links);
    this.insertNodes(this.nodes);
    this.insertCircles();
    
    this.forceSimulation.restart();
    this.zoomFit();
    this.forceSimulation.on("tick",()=>{
      this.tick();
      this.zoomFit();
      this.updateCoordinates();
      let positions = this.topologyService.getCoordinates();
      this.topologyService.setCoordinates(positions);
    });

    

    setTimeout(()=>{
      this.forceSimulation.stop();
      this.zoomFit();
    },500);
    

  };
  
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

    if(this.nodes.length >=50){
      let newtranformation = d3.zoomIdentity
      .scale(this.min_zoom)
     .translate(
      (fullWidth/2 - this.min_zoom*midX)/this.min_zoom,
      (fullHeight/2 - this.min_zoom*midY)/this.min_zoom
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

  toggleSearch = () => {
    this.searchView = this.searchView ? false : true;
    this.searchModel ='';
    if (this.searchView) {
      const element = this.renderer.selectRootElement("#search-bar");
      setTimeout(() => element.focus(), 0);
      this.searchHidden = false;
    } else {
      this.searchHidden = true;
    }
  };

  searchNode = (selectedVal) => {
    this.searchView = false;
    selectedVal = $.trim(selectedVal);
    this.searchModel = '';
    if ($.inArray(selectedVal, this.optArray) > -1) {
      var node = this.svgElement.selectAll(".node");
      if (selectedVal == "none") {
        //node.style("stroke", "#666").style("stroke-width", "1");
      } else {
        d3.selectAll("g.node").each(function(d: any) {
          var element = document.getElementById("circle_" + d.switch_id);
          var classes = "circle blue";
          if (d.state && d.state.toLowerCase() == "deactivated") {
            classes = "circle red";
          }
          element.setAttribute("class", classes);
        });

        var unmatched = node.filter(function(d, i) {
          return d.name != selectedVal;
        });

        var matched = node.filter(function(d, i) {
          return d.name == selectedVal;
        });

        unmatched.style("opacity", "0");

        matched.filter(function(d, index) {
          var element = document.getElementById("circle_" + d.switch_id);
          var classes = "circle blue hover";
          if (d.state && d.state.toLowerCase() == "deactivated") {
            classes = "circle red hover";
          }
          element.setAttribute("class", classes);
        });

        var link = this.svgElement.selectAll(".link");
        link.style("opacity", "0");

        var circle = this.svgElement.selectAll(".flow-circle");
        circle.style("opacity", "0");

        d3.selectAll(".node, .link, .flow-circle")
          .transition()
          .duration(5000)
          .style("opacity", 1);
      }
    }
  };

  onViewSettingUpdate = (
    setting: TopologyView,
    initialLoad: boolean = false
  ) => {
    if (setting.SWITCH_CHECKED) {
      $(".switchname").removeClass("hide");
    } else {
      $(".switchname").addClass("hide");
    }

    if (setting.ISL_CHECKED) {
      d3.selectAll(".physical")
        .transition()
        .duration(500)
        .style("visibility", "visible");
    } else {
      d3.selectAll(".physical")
        .transition()
        .duration(500)
        .style("visibility", "hidden");
    }

    if (setting.FLOW_CHECKED) {
      d3.selectAll(".logical,.flow-circle")
        .transition()
        .duration(500)
        .style("opacity", 1)
        .style("visibility", "visible");
      if (!initialLoad && this.graphdata.flow.length == 0) {
        this.graphShow = true;
        window.location.reload();
      }
    } else {
      d3.selectAll(".logical,.flow-circle")
        .transition()
        .duration(500)
        .style("opacity", 0)
        .style("visibility", "hidden");
    }

    this.onAutoRefreshSettingUpdate(setting);
  };

  onAutoRefreshSettingUpdate = (setting: TopologyView) => {
    if (this.autoRefreshTimerInstance) {
      clearInterval(this.autoRefreshTimerInstance);
    }

    if (setting.REFRESH_CHECKED) {
      this.restartAutoRefreshWithNewSettings(setting.REFRESH_INTERVAL);
    }
  };

  saveCoordinates = () => {
    if(this.topologyService.isCoordinatesChanged()){
        let coordinates = this.topologyService.getCoordinates();
        if (coordinates) {
          this.userService
            .saveSettings(coordinates)
            .subscribe(() => { this.topologyService.setCoordinateChangeStatus('NO');}, error => {});
        }
    }
    
  };

  dblclick = (d, index) => {
    var element = document.getElementById("circle_" + d.switch_id);
    var classes = "circle blue";
    if (d.state && d.state.toLowerCase() == "deactivated") {
      classes = "circle red";
    }
    element.setAttribute("class", classes);
    //doubleClickTime = new Date();
    //d3.select(this).classed("fixed", d.fixed = false);
    this.showSwitchDetails(d);
    //force.resume();
  };

  ngAfterViewInit() {
    setInterval(() => {
      if (this.searchHidden) {
        this.searchView = false;
        this.searchHidden = false;
        this.searchModel = '';
      }
      
    }, 1000);

    this.syncCoordinates = setInterval(() => {
      this.saveCoordinates();
    }, 30000);

    jQuery("#close_switch_detail").click(function() {
      jQuery("#topology-click-txt").css("display", "none");
    });
    
   
  }

  syncUserCoordinatesChanges(){
    if(this.syncCoordinates){
      clearTimeout(this.syncCoordinates);
    }
    this.syncCoordinates = setTimeout(() => {
      this.saveCoordinates();
    }, 1500);
  }

  ngOnDestroy(){
    if(this.autoRefreshTimerInstance){
      clearInterval(this.autoRefreshTimerInstance);
    }

    if(this.syncCoordinates){
      clearTimeout(this.syncCoordinates);
    }


   
  }

}
