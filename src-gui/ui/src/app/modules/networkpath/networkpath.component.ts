import { Component, OnInit } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ToastrService } from 'ngx-toastr';
import { ISL } from '../../common/enums/isl.enum';
import { NgxSpinnerService } from 'ngx-spinner';
import { LoaderService } from '../../common/services/loader.service';
import { FormBuilder, FormGroup, FormControl, Validators } from '@angular/forms';
import { SwitchService } from 'src/app/common/services/switch.service';
import * as d3 from 'd3';
import { Router } from '@angular/router';
import { environment } from '../../../environments/environment';
import { CommonService } from 'src/app/common/services/common.service';
import { MessageObj } from 'src/app/common/constants/constants';
@Component({
  selector: 'app-networkpath',
  templateUrl: './networkpath.component.html',
  styleUrls: ['./networkpath.component.css']
})
export class NetworkpathComponent implements OnInit {
  networkpathForm: FormGroup;
  switchList: any = [];
  strategyList = [
      { strategy_id: 'COST', name: 'Cost'},
      { strategy_id: 'LATENCY', name: 'Latency'},
      { strategy_id: 'MAX_LATENCY', name: 'Max Latency(ms)'},
      { strategy_id: 'COST_AND_AVAILABLE_BANDWIDTH', name: 'Cost and Available Bandwidth'},
    ];
  sortFlag: any = { bandwidth: false, latency: false, nodes: false};
  activeRowIndex = null;
  activePathData = null;
  submitted = false;
  networkPaths: any = [];
  pathLoader = false;
  loadpathGraph = false;
  linksSourceArr = [];
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
  width: number;
  height: number;
  graphShow = false;
  min_zoom = 0.15;
  max_zoom = 3;
  loadZoomIcon = false;
  zoomLevel = 0.15;
  zoomStep = 0.15;
  translateX = 0;
  translateY = 0;
  nodes = [];
  links = [];
  ports = [];
  size: any;
  forceSimulation: any;
  force: any;
  g: any;
  drag: any;
  svgElement: any;
  zoom: any;
  mLinkNum: any = {};

  isDragMove = true;
  flagHover = true;

  graphLink: any;
  graphCircle: any;
  graphText: any;
  graphNode: any;
  graphFlowCount: any;

  graphNodeGroup: any;
  graphLinkGroup: any;
  graphPortSource: any;
  graphPortTarget: any;
  graphportGroupSource: any;
  graphportGroupTarget: any;


  constructor(
    private titleService: Title,
    private toastr: ToastrService,
		private formBuilder: FormBuilder,
    private loaderService: LoaderService,
    private switchService: SwitchService,
    private commonService: CommonService,
    private router: Router
    ) {
      if (!this.commonService.hasPermission('menu_available_path')) {
        this.toastr.error(MessageObj.unauthorised);
         this.router.navigate(['/home']);
        }
    }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Network Path');
    this.loadSwitchList();
    this.networkpathForm = this.formBuilder.group({
      source_switch: ['', Validators.required],
      target_switch: ['', Validators.required],
      strategy: ['', Validators.required],
      max_latency: [0],
    });
  }

  loadSwitchList() {
    this.loaderService.show(MessageObj.loading_switches);
    this.switchService.getSwitchList().subscribe((data: any) => {

    this.switchList = data;
    this.loaderService.hide();
     }, error => {
       this.loaderService.hide();
       this.toastr.error(MessageObj.no_switch_data, 'Error');
     });
  }

  get f() {
    return this.networkpathForm.controls;
  }
  sortNetworkData(type) {
    if (this.sortFlag[type]) {
      this.sortFlag[type] = !this.sortFlag[type];
      this.networkPaths = this.networkPaths.sort(function(a, b) {
        if (type == 'nodes') {

          return a[type].length - b[type].length;
        }
        return a[type] - b[type];
      });
    } else {
      this.sortFlag[type] = !this.sortFlag[type];
      this.networkPaths = this.networkPaths.sort(function(a, b) {
        if (type == 'nodes') {
          return b[type].length - a[type].length;
        }
        return b[type] - a[type];
      });
    }
  }
  viewPath(i , data) {
    if (this.activeRowIndex == i) {
      this.activeRowIndex = null;
      this.activePathData = null;
    } else {
      this.activeRowIndex = i;
      this.activePathData = data;
      this.loadGraph(data);
    }

  }
  getNetworkPath() {
    this.submitted = true;
    const self = this;
    if (this.networkpathForm.invalid) {
      return;
    }
    this.loaderService.show(MessageObj.fetching_network_paths);
    self.networkPaths = [];
    this.switchService.getNetworkPath(this.networkpathForm.controls['source_switch'].value, this.networkpathForm.controls['target_switch'].value,
        this.networkpathForm.controls['strategy'].value, this.networkpathForm.controls['max_latency'].value).subscribe(function(paths) {
      self.submitted = false;
      self.networkPaths = paths.paths.filter(function(d) {
         return d.nodes.length;
       });
       if (self.networkPaths.length == 0) {
        self.toastr.error(MessageObj.no_data_found, 'Success');
       }
       self.loaderService.hide();
    }, error => {
      self.submitted = false;
      self.loaderService.hide();
      self.toastr.error('Error:' + error.error['error-auxiliary-message'], 'Error');
    });
  }

  loadGraph(data) {
    this.loadZoomIcon = false;
    const commonSwitches = [];
    const links = [];
    const nodes = [];
    const ports = [];
    let i = 0;
    for (const d of data) {
        commonSwitches.push({switch_id: d.switch_id, switch_name: d.switch_name, type: 'switchnode'});
        if (typeof(d.input_port) !== 'undefined') {
          ports.push({switch_id: d.switch_id + '_' + d.input_port, switch_name: d.input_port, type: 'portNode'});
        }
        if (typeof(d.output_port) !== 'undefined') {
          ports.push({switch_id: d.switch_id + '_' + d.output_port, switch_name: d.output_port, type: 'portNode'});
        }

        if (typeof(data[i + 1]) != 'undefined' && typeof(data[i + 1].switch_id) !== 'undefined') {
          links.push({
            source_detail: {output_port: d.output_port, id: d.switch_id},
            target_detail: {input_port: data[i + 1].input_port, id: data[i + 1].switch_id},
            source: {switch_id: d.switch_id, switch_name: d.switch_id},
            target: {switch_id: data[i + 1].switch_id, switch_name: data[i + 1].switch_id},
            type: 'isl'
          });
       }
        i++;
    }
   for (const d of commonSwitches) {
      nodes.push(d);
    }

  for (const d of ports) {
    this.ports.push(d);
  }
  this.svgElement = d3.select('#svgForwardPath');
  this.pathLoader = true;
  this.loadpathGraph = true;
  this.initSimulation(nodes, links, 'networkpathGraphWrapper');
  }

  /**** graph plot code ***/

  initSimulation(nodes, links, graphWrapper) {
    this.nodes = nodes;
    this.links = links;
    const self = this;
    self.linksSourceArr = [];
      if (links.length > 0) {
        try {
          const result = this.commonService.groupBy(links, function(item) {
            return [item.source, item.target];
          });
          for (let i = 0, len = result.length; i < len; i++) {
            const row = result[i];
            if (row.length >= 1) {
              for (let j = 0, len1 = row.length; j < len1; j++) {
                const key = row[j].source.switch_id + '_' + row[j].target.switch_id;
                const key1 = row[j].target.switch_id + '_' + row[j].source.switch_id;
                const prcessKey = ( self.linksSourceArr && typeof self.linksSourceArr[key] !== 'undefined') ? key : key1;
                if (typeof self.linksSourceArr[prcessKey] !== 'undefined') {
                  self.linksSourceArr[prcessKey].push(row[j]);
                } else {
                  self.linksSourceArr[key] = [];
                  self.linksSourceArr[key].push(row[j]);
                }
              }
            }
          }

        } catch (e) {}
      }
      const processedlinks = this.processLinks(nodes, links);
      self.zoomLevel = 0.45;
      self.svgElement.html('');
      const width = $('#' + graphWrapper)[0].clientWidth || window.innerWidth;
      const height = window.innerHeight - 100 || $('#' + graphWrapper)[0].clientHeight;
      self.svgElement.style('cursor', 'move');
      self.svgElement.attr('width', width);
      self.svgElement.attr('height', height);
      this.g = self.svgElement.append('g');
      this.graphLinkGroup = this.g.append('g').attr('id', `links`).attr('class', 'links');
      this.graphNodeGroup = this.g.append('g').attr('class', 'nodes').attr('id', 'nodes');
      this.graphportGroupSource = this.g.append('g').attr('id', `sourcePorts`).attr('class', 'sourcePorts');
      this.graphportGroupTarget = this.g.append('g').attr('id', `targetPorts`).attr('class', 'targetPorts');
      this.size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));
      this.zoom  =  d3
      .zoom()
      .scaleExtent([this.min_zoom, this.max_zoom])
      .extent([[0, 0], [width - 200, height - 50]])
      .on('zoom', () => {
        self.zoomLevel = Math.round(d3.event.transform.k * 100) / 100;
        self.g.attr(
          'transform',
          'translate(' +
            d3.event.transform.x +
            ',' +
            d3.event.transform.y +
            ') scale(' +
            d3.event.transform.k +
            ')'
        );

      });
      this.mLinkNum =  this.setLinkIndexAndNum(processedlinks);
      this.forceSimulation = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
            return 20;
      }))
      .force('charge_force', d3.forceManyBody().strength(-1000))
      .force('xPos', d3.forceX(width / 2))
      .force('yPos', d3.forceY(height / 2));
      this.forceSimulation.nodes(nodes);
      this.forceSimulation.force('link', d3.forceLink().links(processedlinks).distance((d: any) => {
         let distance = 10;
        if (d.type == 'isl') {
          distance = 150;
        }
        return distance;
      }).strength(0.1));
      this.forceSimulation.on('tick', () => {
        self.tick();

      });
      this.drag = d3
      .drag()
      .on('start', this.dragStart)
      .on('drag', this.dragging)
      .on('end', this.dragEnd);
      this.insertLinks(this.links);
      this.insertNodes(this.nodes);
      this.graphPortSource = this.insertSourcePorts(this.links);
      this.graphPortTarget =  this.insertTargetPorts(this.links);

    this.svgElement.call(this.zoom);
    this.svgElement.on('dblclick.zoom', null);
    this.forceSimulation.restart();
    this.forceSimulation.on('end', () => {
      this.zoomFit();
      this.pathLoader = false;
      this.loadpathGraph = false;
      this.loadZoomIcon = true;
     });
  }

  processLinks(nodes, links) {
    const nodelength = nodes.length;
    const linklength = links.length;
    for (let i = 0; i < nodelength; i++) {
     for (let j = 0; j < linklength; j++) {
       if (
         nodes[i].switch_id == links[j]['source']['switch_id'] &&
         nodes[i].switch_id == links[j]['target']['switch_id']
       ) {
          links[j].source = i;
          links[j].target = i;
       } else {
         if (nodes[i].switch_id == links[j]['source']['switch_id']) {
           links[j].source = i;
           } else if (
             nodes[i].switch_id == links[j]['target']['switch_id']
           ) {
             links[j].target = i;
           }
       }
     }
   }
   return links;
  }
  setLinkIndexAndNum(links) {
    const mLinkNum = [];
    for (let i = 0; i < links.length; i++) {
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
        mLinkNum[links[i].target.switch_id + ',' + links[i].source.switch_id] !==
        undefined
      ) {
        mLinkNum[
          links[i].target.switch_id + ',' + links[i].source.switch_id
        ] = links[i].linkindex;
      } else {
        mLinkNum[
          links[i].source.switch_id + ',' + links[i].target.switch_id
        ] = links[i].linkindex;
      }
    }
    return mLinkNum;
  }
  dragStart = () => {
    if (!d3.event.active) { this.forceSimulation.alphaTarget(1).stop(); }
  }

  dragging = (d: any, i) => {
    this.isDragMove = true;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy;
    this.tick();
  }

  dragEnd = (d: any, i) => {
    if (!d3.event.active) { this.forceSimulation.alphaTarget(0); }
    this.flagHover = false;
    d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    this.tick();
  }
  isObjEquivalent(a, b) {
    // Create arrays of property names
    const aProps = Object.getOwnPropertyNames(a);
    const bProps = Object.getOwnPropertyNames(b);
    if (aProps.length != bProps.length) {
      return false;
    }

    for (let i = 0; i < aProps.length; i++) {
      const propName = aProps[i];
      if (a[propName] !== b[propName]) {
        return false;
      }
    }

    return true;
  }
  tick = () => {
    const ref = this;
    const lookup = {};
    this.graphLink.attr('d', d => {
      let islCount = 0;
      let matchedIndex = 1;
      const key = d.source.switch_id + '_' + d.target.switch_id;
      const key1 =  d.target.switch_id + '_' + d.source.switch_id;
      const processKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== 'undefined') ? key : key1;
      if (
        this.linksSourceArr &&
        typeof this.linksSourceArr[processKey] !== 'undefined'
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

      let x1 = d.source.x,
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
      const lTotalLinkNum =
        this.mLinkNum[d.source.index + ',' + d.target.index] ||
        this.mLinkNum[d.target.index + ',' + d.source.index];

      if (lTotalLinkNum > 1) {
        dr = dr / (1 + (1 / lTotalLinkNum) * (d.linkindex - 1));
      }

      // generate svg path

      lookup[d.key] = d.flow_count;
      if (lookup[d.Key] == undefined) {
        if (islCount == 1) {
          return (
            'M' +
            d.source.x +
            ',' +
            d.source.y +
            'L' +
            d.target.x +
            ',' +
            d.target.y
          );
        } else {
          if (islCount % 2 != 0 && matchedIndex == 1) {
            return (
              'M' +
              d.source.x +
              ',' +
              d.source.y +
              'L' +
              d.target.x +
              ',' +
              d.target.y
            );
          } else if (matchedIndex % 2 == 0) {
            return (
              'M' +
              d.source.x +
              ',' +
              d.source.y +
              'A' +
              dr +
              ',' +
              dr +
              ' 0 0 1,' +
              d.target.x +
              ',' +
              d.target.y +
              'A' +
              dr +
              ',' +
              dr +
              ' 0 0 0,' +
              d.source.x +
              ',' +
              d.source.y
            );
          } else {
            return (
              'M' +
              d.source.x +
              ',' +
              d.source.y +
              'A' +
              dr +
              ',' +
              dr +
              ' 0 0 0,' +
              d.target.x +
              ',' +
              d.target.y +
              'A' +
              dr +
              ',' +
              dr +
              ' 0 0 1,' +
              d.source.x +
              ',' +
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
            // sweep = 0;

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
            'M' +
            x1 +
            ',' +
            y1 +
            'A' +
            drx +
            ',' +
            dry +
            ' ' +
            xRotation +
            ',' +
            largeArc +
            ',' +
            sweep +
            ' ' +
            x2 +
            ',' +
            y2
          );
        } else {
          return (
            'M' +
            d.source.x +
            ',' +
            d.source.y +
            'L' +
            d.target.x +
            ',' +
            d.target.y
          );
        }
      }
    });

    this.graphNode.attr('transform', function(d) {
      if (d.x && d.y) {
        return 'translate(' + d.x + ',' + d.y + ')';
      }
    });

    this.graphPortSource.attr('transform', function(d) {
      let yvalue = (d.source.y + d.target.y) / 2;
      let xvalue = (d.source.x + d.target.x) / 2;
     const points = ref.getCornerPoint(d.source.x, d.source.y, d.target.x, d.target.y);
       if (typeof(points) != 'undefined' && points.length) {
          xvalue = points[0];
          yvalue = points[1];
       }
      return 'translate(' + xvalue + ',' + yvalue + ')';

   });
   this.graphPortTarget.attr('transform', function(d) {
     let yvalue = (d.source.y + d.target.y) / 2;
     let xvalue = (d.source.x + d.target.x) / 2;
    const points = ref.getCornerPoint(d.target.x, d.target.y, d.source.x, d.source.y);
      if (typeof(points) != 'undefined' && points.length) {
         xvalue = points[0];
         yvalue = points[1];
      }
     return 'translate(' + xvalue + ',' + yvalue + ')';

  });

  }

  private getCornerPoint(x1, y1, x2, y2) {
    const y = (y1 + y2) / 2;
    const x = (x1 + x2) / 2;
    if (y1 < y2 && x1 > x2) {
      if (y - y1 < this.graphOptions.radius + 5 && x1 - x < this.graphOptions.radius + 5 ) {
        return [x, y];
      } else {
        return this.getCornerPoint(x1, y1, x, y);
      }
    } else if (x1 < x2 && y1 < y2) {
      if (x - x1 < this.graphOptions.radius + 5 &&  y - y1 < this.graphOptions.radius + 5) {
        return [x, y];
      } else {
        return this.getCornerPoint(x1, y1, x, y);
      }
    } else if (x1 > x2) {
      if (x1 - x < this.graphOptions.radius + 5 &&  y1 - y < this.graphOptions.radius + 5) {
        return [x, y];
      } else {
        return this.getCornerPoint(x1, y1, x, y);
      }
   } else if (y1 > y2) {
      if (y1 - y < this.graphOptions.radius + 5 && x - x1 < this.graphOptions.radius + 5 ) {
        return [x, y];
      } else {
        return this.getCornerPoint(x1, y1, x, y);
      }

    } else {
      if (x - x1 < this.graphOptions.radius + 5) {
        return [x, y];
      } else {
        return this.getCornerPoint(x1, y1, x, y);
      }
    }

  }

  private insertSourcePorts(links) {
    const ref = this;
    const linkText = this.graphportGroupSource.selectAll('circle').data(links);
    const linkCircleTextSource = linkText
                        .enter()
                        .append('g')
                        .attr('class', 'text-circle sourceEnd');
    linkText.exit().remove();
    linkCircleTextSource.append('circle')
        .attr('class', function(d) {
          const classes = 'circle-text sourceEnd port_circle';
          return classes;
        }).attr('id', function(d) {
          return 'textCircle_' + d.source.switch_id;
        })
        .attr('r', '8')
        .attr('stroke', '#00baff')
        .attr('stroke-width', '1px')
        .attr('fill', '#FFF').attr('style', 'cursor:pointer');

        linkCircleTextSource.append('text')
        .attr('class', function(d) {
          const classes = 'aEnd port_text';
          return classes;
        }).attr('dx', function(d) {
          if (d.source_detail.output_port >= 10) {
            return '-6';
          }
          return '-3';
        })
        .attr('dy', function(d) {
          if (d.source_detail.output_port >= 10) {
            return '3';
          }
          return '5';
        })
        .attr('fill', '#000').text(function(d) {
          return d.source_detail.output_port;
        });


      return linkCircleTextSource.merge(linkText);
  }

  private insertTargetPorts(links) {
    const ref = this;
    const linkText = this.graphportGroupTarget.selectAll('circle').data(links);
    const linkCircleTextTarget = linkText
                        .enter()
                        .append('g')
                        .attr('class', 'text-circle targetEnd');
    linkText.exit().remove();
    linkCircleTextTarget.append('circle')
        .attr('class', function(d) {
          const classes = 'circle-text targetEnd port_circle';
          return classes;
        }).attr('id', function(d) {
          return 'textCircle_' + d.target.switch_id;
        })
        .attr('r', '8')
        .attr('stroke', '#00baff')
        .attr('stroke-width', '1px')
        .attr('fill', '#FFF').attr('style', 'cursor:pointer');

        linkCircleTextTarget.append('text')
        .attr('class', function(d) {
          const classes = 'zEnd port_text';
          return classes;
        }).attr('dx', function(d) {
          if (d.target_detail.input_port >= 10) {
            return '-6';
          }
          return '-3';
        })
        .attr('dy', function(d) {
          if (d.target_detail.input_port >= 10) {
            return '3';
          }
          return '5';
        })
        .attr('fill', '#000').text(function(d) {
          return d.target_detail.input_port;
        });


      return linkCircleTextTarget.merge(linkText);
  }
  zoomFn(direction) {
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
      if (this.zoomLevel - this.zoomStep >= this.min_zoom) {
        this.svgElement
          .transition()
          .duration(350)
          .call(this.zoom.scaleTo, this.zoomLevel - this.zoomStep);
      }
    }
  }
  nodeclick = (d, index) => {
    this.showSwitchDetails(d);
  }
  showSwitchDetails = d => {
    localStorage.setItem('switchDetailsJSON', JSON.stringify(d));
    window.location.href = 'switches/details/' + d.switch_id;
  }
  private insertNodes(nodes) {
    const ref = this;

    const graphNodesData = this.graphNodeGroup
      .selectAll('g.node')
      .data(nodes, d => d.switch_id);

    const graphNodeElement = graphNodesData
      .enter()
      .append('g')
      .attr('class', function(d) {
        return 'node ' + d.switch_id.replace(/:+/g, '_');
      })
      .on('dblclick', null)
      .on('click', this.nodeclick)
      .call(
        d3
          .drag()
          .on('start', this.dragStart)
          .on('drag', this.dragging)
          .on('end', this.dragEnd)
    );

    graphNodesData.exit().remove();

    graphNodeElement
      .append('circle')
      .attr('r', function(d) {
        if (d.type == 'switchnode') {
            return ref.graphOptions.radius;
        } else {
          return '10px';
        }
      })
      .attr('class', function(d, index) {
        let classes = 'circle blue';
        if (d.state && d.state.toLowerCase() == 'deactivated') {
          classes = 'circle red';
        }
        return classes;
      })
      .attr('id', function(d, index) {
        return 'circle_' + d.switch_id;
      })
      .style('cursor', 'move');

    const text = graphNodeElement
      .append('text')
      .attr('dy', '.35em')
      .style('font-size', function(d) {
          return ref.graphOptions.nominal_text_size + 'px';
      })
      .attr('class', 'switchname');
    if (ref.graphOptions.text_center) {
      text
        .text(function(d) {
          return d.switch_name;
        })
        .style('text-anchor', 'middle');
    } else {
      text
        .attr('dx', function(d) {
          return ref.size(d.size) || ref.graphOptions.nominal_base_node_size;
        })
        .text(function(d) {
               return d.switch_name;
        });
    }

    const images = graphNodeElement
      .append('svg:image')
      .attr('xlink:href', function(d) {
        if (d.type == 'switchnode') {
          return environment.assetsPath + '/images/switch.png';
        }
        return '';
      })
      .attr('x', function(d) {
        return -29;
      })
      .attr('y', function(d) {
        return -29;
      })
      .attr('height', 58)
      .attr('width', 58)
      .attr('id', function(d, index) {
        return 'image_' + index;
      })
      .attr('cursor', 'pointer');




    this.graphNode = graphNodeElement.merge(graphNodesData);
  }
  insertPort(port) {
    const ref = this;
    const switch_id = port.switch_id.split('_')[0].replace(/:+/g, '_');
    const graphportNodeData = this.svgElement.selectAll('g.node').data(port, d => d.switch_id);

    const portNode = graphportNodeData.append('circle')
                .attr('r', function(d) {
                  return '10px';
                })
                .attr('class', function(d, index) {
                  const classes = 'port_circle';
                  return classes;
                })
                .attr('id', function(d, index) {
                  return 'port_circle_' + port.switch_id;
                })
                .style('cursor', 'move');


  }

  insertLinks(links) {
    const ref = this;
    const graphLinksData = this.graphLinkGroup.selectAll('path.link').data(links);

    const graphNewLink = graphLinksData
      .enter()
      .append('path')
      .attr('class', function(d, index) {
        return 'link physical';
      })
      .attr('id', (d, index) => {
        return 'link' + index;
      })
      .attr('stroke', function(d, index) {
        return ISL.DISCOVERED;
      });

    graphLinksData.exit().remove();
    this.graphLink = graphNewLink.merge(graphLinksData);

  }
  zoomFit = () => {
    const bounds = this.g.node().getBBox();
    const parent = this.g.node().parentElement;
    const fullWidth = $(parent).width(),
      fullHeight = $(parent).height();
    const width = bounds.width,
      height = bounds.height;
    const midX = (bounds.x + width) / 2,
      midY = (bounds.y + height) / 2;
    if (width == 0 || height == 0) { return; }

    if (this.nodes.length >= 50) {
      const newtranformation = d3.zoomIdentity
      .scale(this.min_zoom)
     .translate(
      (fullWidth / 2 - this.min_zoom * midX) / this.min_zoom,
      (fullHeight / 2 - this.min_zoom * midY) / this.min_zoom
      );
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    } else {
      const newtranformation = d3.zoomIdentity
      .scale(this.zoomLevel)
     .translate(
      (fullWidth / 2 - this.min_zoom * midX),
      (fullHeight / 2 - this.min_zoom * midY) - 200
      );
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }

  }
}
