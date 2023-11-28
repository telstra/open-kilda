import {
  Component,
  OnInit,
  AfterViewInit,
  HostListener,
  Renderer2,
  OnDestroy
} from '@angular/core';
import { TopologyService } from '../../common/services/topology.service';
import { SwitchService } from '../../common/services/switch.service';
import { UserService } from '../../common/services/user.service';
import { ISL } from '../../common/enums/isl.enum';
import { CommonService } from '../../common/services/common.service';
import * as d3 from 'd3';
import { TopologyView } from '../../common/data-models/topology-view';
import { FlowsService } from '../../common/services/flows.service';
import { Observable } from 'rxjs';
import { debounceTime, distinctUntilChanged, map } from 'rxjs/operators';
import { environment } from '../../../environments/environment';
import { LoaderService } from '../../common/services/loader.service';
import { ToastrService } from 'ngx-toastr';
import { Title } from '@angular/platform-browser';
import { Router } from '@angular/router';
declare var jQuery: any;
import { MessageObj } from 'src/app/common/constants/constants';

@Component({
  selector: 'app-topology',
  templateUrl: './topology.component.html',
  styleUrls: ['./topology.component.css']
})
export class TopologyComponent implements OnInit, AfterViewInit, OnDestroy {

  constructor(
    private topologyService: TopologyService,
    private switchService: SwitchService,
    private userService: UserService,
    private commonService: CommonService,
    private flowService: FlowsService,
    private renderer: Renderer2,
    private router: Router,
    private appLoader: LoaderService,
    private toaster: ToastrService,
    private titleService: Title
  ) {
    if (!this.commonService.hasPermission('menu_topology')) {
       this.toaster.error(MessageObj.unauthorised);
       this.router.navigate(['/home']);
      }
  }

  nodes = [];
  links = [];
  flows = [];

  searchCase = false;


  searchView = false;
  searchModel: any = '';
  searchHidden = false;
  autoRefreshTimerInstance: any;
  showWorldMap = false;
  width: number;
  height: number;
  graphShow = false;
  loadWorldMap = false;
  min_zoom = 0.15;
  scaleLimit = 0.05;
  max_zoom = 3;
  zoomLevel = 0.15;
  zoomStep = 0.15;
  translateX = 0;
  translateY = 0;

  linksSourceArr = [];
  new_nodes = false;
  switches_location_changes = false;
  notification_arr = [];
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
  reloadMap = false;
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
  @HostListener('blur', ['$event'])
  onBlur(event: Event) {
    event.stopImmediatePropagation();
  }

  ngOnInit() {
    this.titleService.setTitle('OPEN KILDA - Topology');
    this.appLoader.show(MessageObj.loading_topology);
    this.viewOptions = this.topologyService.getViewOptions();

    this.forceSimulation = this.initSimulation();
    const query = {_: new Date().getTime()};
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

    this.topologyService.notifyObj.subscribe((data: any) => {
			if (data && typeof data.type != 'undefined') {
				let type = 'switch';
				if (data.type.includes('isl')) {
					type = 'isl';
				}
				switch (type) {
          case 'isl': const link  = data.newlink;
                      let index = null;
                      this.links.forEach((l, i) => {
                          if (l.source_switch == link.source_switch && l.src_port == link.src_port && l.target_switch == link.target_switch && l.dst_port == link.dst_port) {
                            index = i;
                          }
                      });
                      if (index != null) {
                        const element = document.getElementById('link' + index);
                        const oldstroke = element.getAttribute('stroke');
                        const oldClasses = element.getAttribute('class');
                        const classes = ' link physical notify_animate';
                        element.setAttribute('class', classes);
                        element.setAttribute('stroke', '#545A52');
                        setTimeout(() => {
                          element.setAttribute('stroke', oldstroke);
                          element.setAttribute('class', oldClasses);
                        }, 2000);

                      }
							 break;
          case 'switch':  const switchData = data.switch;
                          this.searchNode(switchData.name);
							 break;
				}
			}
		});

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

    this.svgElement = d3.select('svg');
    this.svgElement.style('cursor', 'move');
    this.svgElement.attr('width', this.width);
    this.svgElement.attr('height', this.height);

    this.g = this.svgElement.append('g');



    this.graphLinkGroup = this.g
      .append('g')
      .attr('id', `links`)
      .attr('class', 'links');

    this.graphNodeGroup = this.g
      .append('g')
      .attr('id', `nodes`)
      .attr('class', 'nodes');

    this.graphFlowGroup = this.g
      .append('g')
      .attr('id', `flowcounts`)
      .attr('class', 'flowcounts');

    this.zoom = d3
      .zoom()
      .scaleExtent([this.scaleLimit, this.max_zoom])
      .extent([[0, 0], [this.width, this.height]])
      .on('zoom', (event) => {
        // this.forceSimulation.stop();
        this.g.attr(
          'transform',
          'translate(' +
            event.transform.x +
            ',' +
            event.transform.y +
            ') scale(' +
            event.transform.k +
            ')'
        );
        this.zoomLevel = Math.round(event.transform.k * 100) / 100;
        this.translateX = event.transform.x;
        this.translateY = event.transform.y;
        this.isDragMove = true;
        $('#topology-hover-txt, #switch_hover').css('display', 'none');
        $('#topology-click-txt').css('display', 'none');

      });

    this.size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));

    const result = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
        return 20;
      }))
      .force('charge_force', d3.forceManyBody().strength(-1000))
      .force('xPos', d3.forceX(this.width / 2))
      .force('yPos', d3.forceY(this.height / 2));

    return result;
  }

  loadSwitchList = () => {
    this.switchService.getSwitchList().subscribe(switches => {
      this.graphdata.switch = switches || [];
      if (this.graphdata.switch.length > 0) {
        this.loadSwitchLinks();
      } else {
        this.toaster.info(MessageObj.no_switch_available, 'Information');
        this.appLoader.hide();
      }
    }, err => {
      this.appLoader.hide();
      this.toaster.info(MessageObj.no_switch_available, 'Information');
    });
  }

  loadSwitchLinks = () => {
    this.switchService.getSwitchLinks().subscribe(
      links => {
        try {
         if (links) {
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
  }

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
  }

  initGraph = () => {
    const ref = this;

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

    const linksArr = [];
    if (this.nodes.length < 50) {
      this.min_zoom = 0.5;
      this.zoom.scaleExtent([this.scaleLimit, this.max_zoom]);
    }
    if (this.links.length > 0) {
      try {
        const result = this.commonService.groupBy(this.links, function(item) {
          return [item.source_switch, item.target_switch];
        });
        for (let i = 0, len = result.length; i < len; i++) {
          const row = result[i];
          if (row.length >= 1) {
            for (let j = 0, len1 = row.length; j < len1; j++) {
              const key = row[j].source_switch + '_' + row[j].target_switch;
              const key1 = row[j].target_switch + '_' + row[j].source_switch;
              const prcessKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== 'undefined') ? key : key1;
              if (typeof this.linksSourceArr[prcessKey] !== 'undefined') {
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
    const nodelength = this.nodes.length;
    const linklength = this.links.length;
    for (let i = 0; i < nodelength; i++) {
      this.optArray.push((this.nodes[i].name));
      for (let j = 0; j < linklength; j++) {
        if (
          this.nodes[i].switch_id == this.links[j]['source_switch'] &&
          this.nodes[i].switch_id == this.links[j]['target_switch']
        ) {
          this.links[j].source = i;
          this.links[j].target = i;
        } else {
          const key = this.links[j]['source_switch'] + '_' + this.links[j]['target_switch'];
          const key1 = this.links[j]['target_switch'] + '_' + this.links[j]['source_switch'];
          const processKey = this.linksSourceArr && typeof this.linksSourceArr[key] != 'undefined' ? key : key1;
          const sourceObj = processKey.split('_')[0];
          const targetObj = processKey.split('_')[1];
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

    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force('link', d3.forceLink().links(this.links).distance((d: any) => {
     let distance = 150;
      try {
     if (!d.flow_count) {
       if (d.speed == '40000000') {
         distance = 100;
       } else {
         distance = 300;
       }
      }
      } catch (e) {}
      return distance;
    }).strength(0.1));
    this.forceSimulation.stop();
    this.forceSimulation.on('tick', () => {
      this.repositionNodes();
      this.tick();
     });
    this.drag = d3
      .drag()
      .on('start', this.dragStart)
      .on('drag', this.dragging)
      .on('end', this.dragEnd);
    this.insertLinks(this.links);
    this.insertNodes(this.nodes);
    this.insertCircles();
    this.svgElement.call(this.zoom);
    this.svgElement.on('dblclick.zoom', null);
    this.forceSimulation.restart();
    this.forceSimulation.on('end', () => {
      this.appLoader.hide();
      this.graphShow = true;
      this.onViewSettingUpdate(this.viewOptions, true);
      this.zoomFit();
      const positions = this.topologyService.getCoordinates();
      if (!positions) {
        this.zoomReset();
      }
     });
  }

  private insertNodes(nodes) {
    const ref = this;

    const graphNodesData = this.graphNodeGroup
      .selectAll('g.node')
      .data(nodes, d => d.switch_id);

    const graphNodeElement = graphNodesData
      .enter()
      .append('g')
      .attr('class', 'node')
      .on('dblclick', this.dblclick)
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
      .attr('r', this.graphOptions.radius)
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
      .style('font-size', this.graphOptions.nominal_text_size + 'px')
      .attr('class', 'switchname hide');
    if (this.graphOptions.text_center) {
      text
        .text(function(d) {
          return d.name;
        })
        .style('text-anchor', 'middle');
    } else {
      text
        .attr('dx', function(d) {
          return ref.size(d.size) || ref.graphOptions.nominal_base_node_size;
        })
        .text(function(d) {
          return d.name;
        });
    }

    const images = graphNodeElement
      .append('svg:image')
      .attr('xlink:href', function(d) {
        return environment.assetsPath + '/images/switch.png';
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
      .attr('cursor', 'pointer')
      .on('mouseover', function(event, d) {
        $('#isl_hover').css('display', 'none');

        const element = document.getElementById('circle_' + d.switch_id);

        let classes = 'circle blue hover';
        if (d.state && d.state.toLowerCase() == 'deactivated') {
          classes = 'circle red hover';
        }
        element.setAttribute('class', classes);
        const rec: any = element.getBoundingClientRect();
        $('#topology-hover-txt, #switch_hover').css('display', 'block');
        $('#topology-hover-txt').css('top', rec.y + 'px');
        $('#topology-hover-txt').css('left', (rec.x) + 'px');

        d3.select('.switchdetails_div_switch_name').html(
          '<span>' + d.name + '</span>'
        );
        d3.select('.switchdetails_div_controller').html(
          '<span>' + d.switch_id + '</span>'
        );
        d3.select('.switchdetails_div_state').html(
          '<span>' + d.state + '</span>'
        );
        d3.select('.switchdetails_div_address').html(
          '<span>' + d.address + '</span>'
        );
        d3.select('.switchdetails_div_name').html(
          '<span>' + d.switch_id + '</span>'
        );
        d3.select('.switchdetails_div_desc').html(
          '<span>' + d.description + '</span>'
        );
        const bound = ref.horizontallyBound(
          document.getElementById('switchesgraph'),
          document.getElementById('topology-hover-txt')
        );
        if (bound) {

          $('#topology-hover-txt').removeClass('left');
        } else {
          const left = rec.x - (300 + 100); // subtract width of tooltip box + circle radius
          $('#topology-hover-txt').css('left', left + 'px');
          $('#topology-hover-txt').addClass('left');
        }

      })
      .on('mouseout', function(event, d) {
        if (this.flagHover == false) {
          this.flagHover = true;
        } else {
          const element = document.getElementById('circle_' + d.switch_id);
          let classes = 'circle blue';
          if (d.state && d.state.toLowerCase() == 'deactivated') {
            classes = 'circle red';
          }
          element.setAttribute('class', classes);
        }


        if (!$('#topology-hover-txt').is(':hover')) {
          $('#topology-hover-txt, #switch_hover').css('display', 'none');
        }

      })
      .on('click', function(event, d) {
        $('#topology-hover-txt').css('display', 'none');

        const cName = document.getElementById('circle_' + d.switch_id).className;
        const circleClass = cName; // cName.baseVal;

        const element = document.getElementById('circle_' + d.switch_id);

        let classes = 'circle blue hover';
        if (d.state && d.state.toLowerCase() == 'deactivated') {
          classes = 'circle red hover';
        }
        element.setAttribute('class', classes);
        const rec: any = element.getBoundingClientRect();
        if (!ref.isDragMove) {
          $('#topology-click-txt, #switch_click').css('display', 'block');
          $('#topology-click-txt').css('top', rec.y + 'px');
          $('#topology-click-txt').css('left', rec.x + 'px');

          d3.select('.switchdetails_div_click_switch_name').html(
            '<span>' + d.name + '</span>'
          );
          d3.select('.switchdetails_div_click_controller').html(
            '<span>' + d.switch_id + '</span>'
          );
          d3.select('.switchdetails_div_click_state').html(
            '<span>' + d.state + '</span>'
          );
          d3.select('.switchdetails_div_click_address').html(
            '<span>' + d.address + '</span>'
          );
          d3.select('.switchdetails_div_click_name').html(
            '<span>' + d.switch_id + '</span>'
          );
          d3.select('.switchdetails_div_click_desc').html(
            '<span>' + d.description + '</span>'
          );
          const bound = ref.horizontallyBound(
            document.getElementById('switchesgraph'),
            document.getElementById('topology-click-txt')
          );
          if (bound) {
            $('#topology-click-txt').removeClass('left');
          } else {
            const left = rec.x - (300 + 80); // subtract width of tooltip box + circle radius
            $('#topology-click-txt').css('left', left + 'px');
            $('#topology-click-txt').addClass('left');
          }
           $('#topology-hover-txt').css('display', 'none');
        } else {
          ref.isDragMove = false;
        }
      });

    this.graphNode = graphNodeElement.merge(graphNodesData);
  }
  private insertLinks(links) {
    const ref = this;
    const graphLinksData = this.graphLinkGroup.selectAll('path.link').data(links);

    const graphNewLink = graphLinksData
      .enter()
      .append('path')
      .attr('class', function(d, index) {
        const availbandwidth = d.available_bandwidth;
        const max_bandwidth = d.max_bandwidth;
        const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty('flow_count')) {
          return 'link logical';
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == 'discovered') ||
            (d.state && d.state.toLowerCase() == 'failed')
          ) {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                return 'link physical  orange_percentage dashed_maintenance_path';
              }
              return 'link physical  dashed_maintenance_path';
            } else if (d.affected) {
              return 'link physical  dashed_path';
            } else {
              return 'link physical';
            }
          } else {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                return 'link physical dashed_maintenance_path orange_percentage';
              }
              return 'link physical  dashed_maintenance_path';
            } else if (d.affected) {
              return 'link physical dashed_path';
            } else {
              if (parseInt(percentage) < 50) {
                return 'link physical orange_percentage';
              }
              return 'link physical';
            }
          }
        }
      })
      .attr('id', (d, index) => {
        return 'link' + index;
      })
      .on('mouseover', function(event, d) {
        const index = d.index;
        $('#switch_hover').css('display', 'none');
        const element = $('#link' + index)[0];
        const availbandwidth = d.available_bandwidth;
        const max_bandwidth = d.max_bandwidth;

        const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty('flow_count')) {
          if (d.under_maintenance) {
            element.setAttribute('class', 'link logical overlay dashed_maintenance_path');

          } else if (d.affected) {
            element.setAttribute('class', 'link logical overlay dashed_path');
          } else  {
            element.setAttribute('class', 'link logical overlay');
          }
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == 'discovered') ||
            (d.state && d.state.toLowerCase() == 'failed')
          ) {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  'class',
                  'link physical dashed_maintenance_path orange_percentage pathoverlay'
                );
              } else {
                element.setAttribute(
                  'class',
                  'link physical dashed_maintenance_path pathoverlay'
                );
              }
            } else if (d.affected) {
              element.setAttribute(
                'class',
                'link physical dashed_path pathoverlay'
              );
            } else  {
              if (parseInt(percentage) < 50 && d.state.toLowerCase() != 'failed' && !d.unidirectional) {
                element.setAttribute(
                  'class',
                  'link physical orange_percentage overlay'
                );
              } else {
                element.setAttribute('class', 'link physical overlay');
              }
            }
          } else {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  'class',
                  'link physical overlay orange_percentage dashed_maintenance_path'
                );
              } else {
                element.setAttribute(
                  'class',
                  'link physical overlay dashed_maintenance_path'
                );
              }


            } else if (d.affected) {
              element.setAttribute(
                'class',
                'link physical overlay dashed_path'
              );
            } else  {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  'class',
                  'link physical orange_percentage overlay'
                );
              } else {
                element.setAttribute('class', 'link physical overlay');
              }
            }
          }
          $(element).on('mousemove', function(e) {
            $('#topology-hover-txt').css('top', (e.pageY - 30) + 'px');
            $('#topology-hover-txt').css('left', (e.pageX) + 'px');
            const bound = ref.horizontallyBound(
              document.getElementById('switchesgraph'),
              document.getElementById('topology-hover-txt')
            );

            if (bound) {
              $('#topology-hover-txt').removeClass('left');
            } else {
              const left = e.pageX - (300 + 100); // subtract width of tooltip box + circle radius
              $('#topology-hover-txt').css('left', left + 'px');
              $('#topology-hover-txt').addClass('left');
            }
          });

          const rec = element.getBoundingClientRect();
          $('#topology-hover-txt, #isl_hover').css('display', 'block');
          d3.select('.isldetails_div_source_port').html(
            '<span>' +
              (d.src_port == '' || d.src_port == undefined ? '-' : d.src_port) +
              '</span>'
          );
          d3.select('.isldetails_div_maintenance').html(
            '<span>' +
              (d.under_maintenance == '' || d.under_maintenance == undefined ? 'false' : d.under_maintenance) +
              '</span>'
          );

          d3.select('.isldetails_div_destination_port').html(
            '<span>' +
              (d.dst_port == '' || d.dst_port == undefined ? '-' : d.dst_port) +
              '</span>'
          );
          d3.select('.isldetails_div_source_switch').html(
            '<span>' +
              (d.source_switch_name == '' || d.source_switch_name == undefined
                ? '-'
                : d.source_switch_name) +
              '</span>'
          );
          d3.select('.isldetails_div_destination_switch').html(
            '<span>' +
              (d.target_switch_name == '' || d.target_switch_name == undefined
                ? '-'
                : d.target_switch_name) +
              '</span>'
          );
          d3.select('.isldetails_div_speed').html(
            '<span>' +
              (d.max_bandwidth == '' || d.max_bandwidth == undefined ? '-' : d.max_bandwidth / 1000) +
              ' Mbps</span>'
          );
          d3.select('.isldetails_div_state').html(
            '<span>' +
              (d.state == '' || d.state == undefined ? '-' : d.state) +
              '</span>'
          );
          d3.select('.isldetails_div_latency').html(
            '<span>' +
              (d.latency == '' || d.latency == undefined ? '-' : d.latency) +
              '</span>'
          );
          d3.select('.isldetails_div_bandwidth').html(
            '<span>' +
              (d.available_bandwidth == '' || d.available_bandwidth == undefined
                ? '-'
                : d.available_bandwidth / 1000) +
              ' Mbps (' +
              percentage +
              '%)</span>'
          );
          d3.select('.isldetails_div_unidirectional').html(
            '<span>' +
              (d.unidirectional == '' || d.unidirectional == undefined
                ? '-'
                : d.unidirectional) +
              '</span>'
          );
          d3.select('.isldetails_div_cost').html(
            '<span>' +
              (d.cost == '' || d.cost == undefined ? '-' : d.cost) +
              '</span>'
          );
        }
      })
      .on('mouseout', function(event, d) {
        $('#topology-hover-txt, #isl_hover').css('display', 'none');
        const element = $('#link' + d.index)[0];
        const availbandwidth = d.available_bandwidth;
        const max_bandwidth = d.max_bandwidth;
        const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty('flow_count')) {
          if (d.under_maintenance) {
            element.setAttribute('class', 'link logical dashed_maintenance_path');
          }  else if (d.affected) {
            element.setAttribute('class', 'link logical dashed_path');
          } else {
            element.setAttribute('class', 'link logical');
          }
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == 'discovered') ||
            (d.state && d.state.toLowerCase() == 'failed')
          ) {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                element.setAttribute('class', 'link physical  orange_percentage dashed_maintenance_path');
              } else {
                element.setAttribute('class', 'link physical  dashed_maintenance_path');
              }

            } else if (d.affected) {
              element.setAttribute('class', 'link physical  dashed_path');
            } else {
              element.setAttribute('class', 'link physical ');
            }
          } else {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {
                element.setAttribute('class', 'link physical orange_percentage dashed_maintenance_path');
              } else {
                element.setAttribute('class', 'link physical dashed_maintenance_path');
              }
            } else if (d.affected) {
              element.setAttribute('class', 'link physical dashed_path');
            } else {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  'class',
                  'link physical orange_percentage '
                );
              } else {
                element.setAttribute('class', 'link physical ');
              }
            }
          }
        }

        if (!$('#topology-hover-txt').is(':hover')) {
          $('#topology-hover-txt, #isl_hover').css('display', 'none');
        }
      })
      .on('click', function(event, d) {
        const element = $('#link' + d.index)[0];
        const availbandwidth = d.available_bandwidth;
        const max_bandwidth = d.max_bandwidth;
        const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty('flow_count')) {
          if (d.under_maintenance) {
            element.setAttribute('class', 'link logical overlay dashed_maintenance_path');
          } else if (d.affected) {
            element.setAttribute('class', 'link logical overlay dashed_path');
          } else {
            element.setAttribute('class', 'link logical overlay');
          }

          ref.showFlowDetails(d);
        } else {
          if (
            (d.unidirectional &&
              d.state &&
              d.state.toLowerCase() == 'discovered') ||
            (d.state && d.state.toLowerCase() == 'failed')
          ) {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {

              element.setAttribute('class', 'link physical pathoverlay orange_percentage dashed_maintenance_path');
              } else {

              element.setAttribute('class', 'link physical pathoverlay dashed_maintenance_path');
              }
            } else if (d.affected) {
              element.setAttribute(
                'class',
                'link physical pathoverlay dashed_path'
              );
            } else {
              element.setAttribute('class', 'link physical pathoverlay');
            }
          } else {
            if (d.under_maintenance) {
              if (parseInt(percentage) < 50) {

              element.setAttribute('class', 'link physical overlay orange_percentage dashed_maintenance_path');
              } else {

              element.setAttribute('class', 'link physical overlay dashed_maintenance_path');
              }
            } else if (d.affected) {
              element.setAttribute(
                'class',
                'link physical overlay dashed_path'
              );
            } else {
              if (parseInt(percentage) < 50) {
                element.setAttribute(
                  'class',
                  'link physical orange_percentage overlay'
                );
              } else {
                element.setAttribute('class', 'link physical overlay');
              }
            }
          }
          ref.showLinkDetails(d);
        }
      })
      .attr('stroke', function(d, index) {
        if (d.hasOwnProperty('flow_count')) {
          return ISL.FLOWCOUNT;
        } else {
         if (
            d.unidirectional &&
            d.state &&
            d.state.toLowerCase() == 'discovered'
          ) {
            return ISL.UNIDIR;
          } else if (d.state && d.state.toLowerCase() == 'discovered') {
            return ISL.DISCOVERED;
          } else if (d.state && d.state.toLowerCase() == 'moved') {
            return ISL.MOVED;
          }

          return ISL.FAILED;
        }
      });

    graphLinksData.exit().remove();
    this.graphLink = graphNewLink.merge(graphLinksData);
  }

  insertCircles() {
    const ref = this;
    const filteredLinks = [];
    this.links.map(function(l, i) {
      if (l && l.hasOwnProperty('flow_count')) {
        const obj = l;
        obj.index = i;
        filteredLinks.push(obj);
      }
    });

    const graphFlowCountData = this.graphFlowGroup
      .selectAll('g.flow-circle')
      .data(filteredLinks);

    const graphCountElement = graphFlowCountData
      .enter()
      .append('g')
      .attr('class', 'flow-circle');
    graphFlowCountData.exit().remove();

    graphCountElement
      .append('circle')
      .attr('dy', '.35em')
      .style('font-size', this.graphOptions.nominal_text_size + 'px')
      .attr('r', function(d, index) {
        let r: any;
        const element = $('#link' + d.index)[0];
        const f = d.flow_count;
        if (
          element.getAttribute('stroke') == '#228B22' ||
          element.getAttribute('stroke') == 'green'
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
      .on('mouseover', function(event, d) {
        const index = d.index;
        const element = $('#link' + index)[0];
        const availbandwidth = d.available_bandwidth;
        let classes = '';
        const max_bandwidth = d.max_bandwidth;
        const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
        if (d.hasOwnProperty('flow_count')) {
          classes = 'link logical overlay';
        } else {
          if (parseInt(percentage) < 50) {
            classes = 'link physical orange_percentage overlay';
          } else {
            classes = 'link physical overlay';
          }
        }
        element.setAttribute('class', classes);
      })
      .on('mouseout', function(event, d) {
        const element = $('#link' + d.index)[0];
        const availbandwidth = d.available_bandwidth;
        let classes = '';
        const max_bandwidth = d.max_bandwidth;
        const percentage = ref.commonService.getPercentage(
          availbandwidth,
          max_bandwidth
        );
        if (d.hasOwnProperty('flow_count')) {
          classes = 'link logical';
        } else {
          if (parseInt(percentage) < 50) {
            classes = 'link physical orange_percentage';
          } else {
            classes = 'link physical';
          }
        }
        element.setAttribute('class', classes);
      })
      .on('click', function(event, d) {
        ref.showFlowDetails(d);
      })
      .attr('class', 'linecircle')
      .attr('id', function(d, index) {
        const id = '_' + index;
        return id;
      })
      .attr('fill', function(d) {
        return '#d3d3d3';
      })
      .call(this.drag);

    graphCountElement
      .append('text')
      .attr('dx', function(d) {
        let r: any;
        const f = d.flow_count;
        if (f < 10) {
          r = -3;
        } else if (f >= 10 && f < 100) {
          r = -6;
        } else {
          r = -9;
        }
        return r;
      })
      .attr('dy', function(d) {
        return 5;
      })
      .attr('fill', function(d) {
        return 'black';
      })
      .text(function(d) {
        const value = d.flow_count;
        return value;
      });

    this.graphFlowCount = graphCountElement.merge(graphFlowCountData);
  }

  repositionNodes = () => {
    const positions = this.topologyService.getCoordinates();
    if (positions) {
      d3.selectAll('g.node').attr('transform', function(d: any) {
        try {
          d.x = positions[d.switch_id][0];
          d.y = positions[d.switch_id][1];
        } catch (e) {}
        if (d.x && d.y) { return 'translate(' + d.x + ',' + d.y + ')'; }
      });
    }
  }

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
    for (let i = 0; i < this.links.length; i++) {
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
        this.mLinkNum[this.links[i].target + ',' + this.links[i].source] !==
        undefined
      ) {
        this.mLinkNum[
          this.links[i].target + ',' + this.links[i].source
        ] = this.links[i].linkindex;
      } else {
        this.mLinkNum[
          this.links[i].source + ',' + this.links[i].target
        ] = this.links[i].linkindex;
      }
    }
  }

  private processNodesData(newNodes, removedNodes, response) {
    const self = this;
    this.nodes.forEach(function(d) {
      for (let i = 0, len = response.length; i < len; i++) {
        if (d.switch_id == response[i].switch_id) {
           // condition for world map refresh
           if ( d.state != response[i].state) {
              self.switches_location_changes = true;
              const notification_msg = {
                    'id': 'switch_' + d.switch_id,
                    'type': 'switch',
                    'switch': d,
                    'message': d.name + ' Switch state changed from ' + d.state + ' to ' + response[i].state
                  };
              self.notification_arr.push(notification_msg);
          }
          d.state = response[i].state;

          let classes = 'circle blue';
          if (d.state && d.state.toLowerCase() == 'deactivated') {
            classes = 'circle red';
          }
          const element = document.getElementById('circle_' + d.switch_id);
          if (element) {
            element.setAttribute('class', classes);
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
          let foundFlag = false;
          for (let i = 0; i < removedNodes.length; i++) {
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
    const ref = this;
    let classes = '';
    this.links.forEach(function(d, index) {
      for (let i = 0, len = response.length; i < len; i++) {
        if (
          d.source_switch == response[i].source_switch &&
          d.target_switch == response[i].target_switch &&
          d.src_port == response[i].src_port &&
          d.dst_port == response[i].dst_port
        ) {
           // condition for world map refresh
           if (d.available_bandwidth != response[i].available_bandwidth) {
            ref.switches_location_changes = true;
            const notification_msg = {
              'id': 'isl_bandwidth_' + response[i]['source_switch'] + '_' + response[i]['src_port'] + '_' + response[i]['target_switch'] + '_' + response[i]['dst_port'],
              'type': 'isl',
              'oldlink': d,
              'newlink': response[i],
              'message': ' ISL having src(' + d.source_switch_name + ') and target(' + d.target_switch_name + ') has change in its Available bandwidth from ' + d.available_bandwidth + ' to ' + response[i].available_bandwidth,
            };
            ref.notification_arr.push(notification_msg);
           }
            if (d.max_bandwidth != response[i].max_bandwidth) {
              ref.switches_location_changes = true;
              const notification_msg = {
                'id': 'isl_max_bandwidth_' + response[i]['source_switch'] + '_' + response[i]['src_port'] + '_' + response[i]['target_switch'] + '_' + response[i]['dst_port'],
                'type': 'isl',
                'oldlink': d,
                'newlink': response[i],
                'message': ' ISL having src(' + d.source_switch_name + ') and target(' + d.target_switch_name + ') has change in its max bandwidth from ' + d.max_bandwidth + ' to ' + response[i].max_bandwidth,
              };
              ref.notification_arr.push(notification_msg);
            }
            if (d.state != response[i].state) {
              ref.switches_location_changes = true;
              const notification_msg = {
                'id': 'isl_state_' + response[i]['source_switch'] + '_' + response[i]['src_port'] + '_' + response[i]['target_switch'] + '_' + response[i]['dst_port'],
                'type': 'isl',
                'oldlink': d,
                'newlink': response[i],
                'message': ' ISL having src(' + d.source_switch_name + ') and target(' + d.target_switch_name + ') state changed from ' + d.state + ' to ' + response[i].state,
              };
              ref.notification_arr.push(notification_msg);
            }
            if (d.under_maintenance != response[i].under_maintenance) {
              ref.switches_location_changes = true;
              const notification_msg = {
                'id': 'isl_maintenance_' + response[i]['source_switch'] + '_' + response[i]['src_port'] + '_' + response[i]['target_switch'] + '_' + response[i]['dst_port'],
                'type': 'isl',
                'oldlink': d,
                'newlink': response[i],
                'message': ' ISL having src(' + d.source_switch_name + ') and target(' + d.target_switch_name + ')maintenance flag changed from ' + d.under_maintenance + ' to ' + response[i].under_maintenance,
              };
              ref.notification_arr.push(notification_msg);
            }
            if (d.unidirectional  != response[i].unidirectional) {
              ref.switches_location_changes = true;
              const notification_msg = {
                'id': 'isl_unidirectional' + response[i]['source_switch'] + '_' + response[i]['src_port'] + '_' + response[i]['target_switch'] + '_' + response[i]['dst_port'],
                'type': 'isl',
                'oldlink': d,
                'newlink': response[i],
                'message': ' ISL having src(' + d.source_switch_name + ') and target(' + d.target_switch_name + ') Unidirectional state changed from ' + d.unidirectional + ' to ' + response[i].unidirectional
              };
              ref.notification_arr.push(notification_msg);
           }
          d.available_bandwidth = response[i].available_bandwidth;
          d.max_bandwidth = response[i].max_bandwidth;
          d.state = response[i].state;
          d.under_maintenance = response[i].under_maintenance;
          d.unidirectional = response[i].unidirectional;

          const availbandwidth = d.available_bandwidth;
          const max_bandwidth = d.max_bandwidth;
          const percentage = ref.commonService.getPercentage(availbandwidth, max_bandwidth);
          if (response[i].affected) {
            d['affected'] = response[i].affected;
          } else {
            d['affected'] = false;
          }

          if (
            d.unidirectional ||
            (d.state && d.state.toLowerCase() == 'failed')
          ) {
            if (d.under_maintenance) {
              classes = 'link physical down dashed_maintenance_path';
            } else if (d.affected) {
              classes = 'link physical down dashed_path';
            } else {
              classes = 'link physical down';
            }
          } else {
            if (d.under_maintenance) {
              classes = 'link physical dashed_maintenance_path';
              if (parseInt(percentage) < 50) {
                classes = 'link physical dashed_maintenance_path orange_percentage';
              }
            } else if (d.affected) {
              classes = 'link physical dashed_path';
            } else {
              if (parseInt(percentage) < 50) {
                classes = 'link physical orange_percentage';
              } else {
                classes = 'link physical ';
              }
            }
          }
          const element = document.getElementById('link' + index);

          let stroke = ISL.FAILED;

          if (
            d.unidirectional &&
            d.state &&
            d.state.toLowerCase() == 'discovered'
          ) {
            stroke = ISL.UNIDIR;
          } else if (d.state && d.state.toLowerCase() == 'discovered') {
            stroke = ISL.DISCOVERED;
          } else if (d.state && d.state.toLowerCase() == 'moved') {
            stroke = ISL.MOVED;
          }

          if (element) {
            element.setAttribute('class', classes);
            element.setAttribute('stroke', stroke);
          }

          break;
        }
      }
    });

    if (
      (newLinks && newLinks.length) ||
      (removedLinks && removedLinks.length) ||
      this.new_nodes || (this.switches_location_changes && this.showWorldMap)
    ) {
      this.new_nodes = false;
      if (this.showWorldMap) {
        this.reloadMap = false;
        this.graphdata = { switch: [], isl: [], flow: [] };
      }
      this.restartGraphWithNewIsl(newLinks, removedLinks);
    } else { // In case user is on topology view and data changes in autoreload
      if (ref.notification_arr && ref.notification_arr.length && this.switches_location_changes) {
        const oldNotifications = JSON.parse(localStorage.getItem('notification_data')) || [];
        if (oldNotifications && oldNotifications.length) {
          const notifyArr = oldNotifications.concat(ref.notification_arr);
          localStorage.setItem('notification_data', JSON.stringify(notifyArr));
          this.topologyService.displayNotifications(notifyArr);
        } else {
          localStorage.setItem('notification_data', JSON.stringify(ref.notification_arr));
          this.topologyService.displayNotifications(ref.notification_arr);
        }
      }

    }
  }

  getSwitchList() {
    this.switchService.getSwitchList().subscribe(
      response => {
        let switchArr: any = [];
        // new switch is added
        switchArr = this.getNewSwitch(this.nodes, response);
        const newNodes = switchArr['added'] || [];
        const removedNodes = switchArr['removed'] || [];
        this.switches_location_changes = switchArr['location_updated'];
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
        let linksArr: any = [];
        linksArr = this.getNewLinks(this.links, response);
        const newLinks = linksArr['added'] || [];
        const removedLinks = linksArr['removed'] || [];

        this.processLinksData(newLinks, removedLinks, response);
      },
      error => {}
    );
  }

  /** get removed and newly added switch list */
  getNewSwitch(nodes, response) {
    const ref = this;
    const nodesArr = { added: [], removed: [] , location_updated: false};
    for (let i = 0; i < response.length; i++) {
      let foundFlag = false;
      for (let j = 0; j < nodes.length; j++) {
        if (nodes[j].switch_id == response[i].switch_id) {
          foundFlag = true;
          if (nodes[j].location.latitude != response[i].location.latitude || nodes[j].location.longitude != response[i].location.longitude) {
            nodesArr['location_updated'] = true;
          }
        }
      }
      if (!foundFlag) {
        nodesArr['added'].push(response[i]);
        const notification_msg = {
          'id': 'switchadded_' + response[i].switch_id,
          'type': 'switchadded',
          'switch': response[i],
          'message': ' New switch(' + response[i].name + ') has been added'
        };
        ref.notification_arr.push(notification_msg);
      }
    }
    for (let i = 0; i < nodes.length; i++) {
      let foundFlag = false;
      for (let j = 0; j < response.length; j++) {
        if (response[j].switch_id == nodes[i].switch_id) {
          foundFlag = true;
        }
      }
      if (!foundFlag) {
        nodesArr['removed'].push(nodes[i]);
        const notification_msg = {
          'id': 'switchremoved_' + nodes[i].switch_id,
          'type': 'switchremoved',
          'switch': nodes[i],
          'message': ' Switch(' + nodes[i].name + ') has been removed'
        };
        ref.notification_arr.push(notification_msg);
      }
    }
    return nodesArr;
  }

  /** get removed and newly added switch links  */
  getNewLinks(links, response) {
    const linksArr = { added: [], removed: [] };
    for (let i = 0; i < response.length; i++) {
      let foundFlag = false;
      for (let j = 0; j < links.length; j++) {
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
        linksArr['added'].push(response[i]);
      }
    }
    // checking for removed links
    for (let i = 0; i < links.length; i++) {
      let foundFlag = false;
      for (let j = 0; j < response.length; j++) {
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
        linksArr['removed'].push(links[i]);
      }
    }
    return linksArr;
  }

  restartGraphWithNewIsl(newLinks, removedLinks) {
    this.optArray = [];
    const ref = this;
    try {
      const result = this.commonService.groupBy(newLinks, function(item) {
        return [item.source_switch, item.target_switch];
      });
      for (let i = 0, len = result.length; i < len; i++) {
        const row = result[i];

        if (row.length >= 1) {
          for (let j = 0, len1 = row.length; j < len1; j++) {
            const key = row[j].source_switch + '_' + row[j].target_switch;
             const key1 = row[j].target_switch + '_' + row[j].source_switch;
             const prcessKey = ( this.linksSourceArr && typeof this.linksSourceArr[key] !== 'undefined') ? key : key1;
             if (typeof this.linksSourceArr[prcessKey] !== 'undefined') {
               this.linksSourceArr[prcessKey].push(row[j]);
             } else {
              this.linksSourceArr[key] = [];
              this.linksSourceArr[key].push(row[j]);
            }
          }
        }
      }
    } catch (e) {}
    const nodelength = this.nodes.length;
    const linklength = newLinks.length;
    for (let i = 0; i < nodelength; i++) {
      this.optArray.push(this.nodes[i].name);
      for (let j = 0; j < linklength; j++) {
        const notification_msg = {
          'id': 'isladded_' + newLinks[j]['source_switch'] + '_' + newLinks[j]['src_port'] + '_' + newLinks[j]['target_switch'] + '_' + newLinks[j]['dst_port'],
          'type': 'isladded',
          'link': newLinks[j],
          'message': ' New ISL between src(' + newLinks[j]['source_switch_name'] + ') and target(' + newLinks[j]['target_switch_name'] + ') has been added'
        };
        ref.notification_arr.push(notification_msg);
        if (
          this.nodes[i].switch_id == newLinks[j]['source_switch'] &&
          this.nodes[i].switch_id == newLinks[j]['target_switch']
        ) {
          newLinks[j].source = i;
          newLinks[j].target = i;

        } else {
          if (this.nodes[i].switch_id == newLinks[j]['source_switch']) {
            newLinks[j].source = i;
          } else if (this.nodes[i].switch_id == newLinks[j]['target_switch']) {
            newLinks[j].target = i;
          }
        }
      }
    }
    this.links = this.links.concat(newLinks);
    // splice removed links
    if (removedLinks && removedLinks.length) {
      this.links = this.links.filter(function(d) {
        let foundFlag = false;
        for (let i = 0; i < removedLinks.length; i++) {
          const notification_msg = {
            'id': 'islremoved_' + removedLinks[i]['source_switch'] + '_' + removedLinks[i]['src_port'] + '_' + removedLinks[i]['target_switch'] + '_' + removedLinks[i]['dst_port'],
            'type': 'islremoved',
            'link': removedLinks[i],
            'message': ' ISL between src(' + removedLinks[i]['source_switch_name'] + ') and target(' + removedLinks[i]['target_switch_name'] + ') has been removed'
          };
          ref.notification_arr.push(notification_msg);
          if (
            d.source_switch == removedLinks[i].source_switch &&
            d.target_switch == removedLinks[i].target_switch &&
            d.src_port == removedLinks[i].src_port &&
            d.dst_port == removedLinks[i].dst_port
          ) {
            foundFlag = true;
            const key = d.source_switch + '_' + d.target_switch;
            try {
              ref.linksSourceArr[key].splice(0, 1);
            } catch (err) {

            }
            break;
          }
        }
        return !foundFlag;
      });
    }

    const oldNotifications = JSON.parse(localStorage.getItem('notification_data')) || [];
    if (oldNotifications && oldNotifications.length) {
      const notifyArr = oldNotifications.concat(ref.notification_arr);
      localStorage.setItem('notification_data', JSON.stringify(notifyArr));
      this.topologyService.displayNotifications(notifyArr);
    } else {
      localStorage.setItem('notification_data', JSON.stringify(ref.notification_arr));
      this.topologyService.displayNotifications(ref.notification_arr);
    }

    if (this.showWorldMap) {
      this.reloadMap = true;
      this.graphdata.isl = this.links;
      this.graphdata.switch = this.nodes;
    } else {
      this.appLoader.show(MessageObj.reloading_topology_with_new_data);
      this.graphShow = false;
      this.forceSimulation = d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('charge_force', d3.forceManyBody().strength(-1000))
      .force('xPos', d3.forceX(this.width / 2))
      .force('yPos', d3.forceY(this.height / 2));

      this.forceSimulation.nodes(this.nodes);
      this.forceSimulation.force('link', d3.forceLink().links(this.links).distance(200).strength(1));
      this.forceSimulation.stop();

      this.forceSimulation.on('tick', () => {
        this.repositionNodes();
        this.tick();
        this.zoomFit();
      });

      this.insertLinks(this.links);
      this.insertNodes(this.nodes);
      this.insertCircles();
      this.forceSimulation.restart();

      this.forceSimulation.on('end', () => {
        this.appLoader.hide();
        this.graphShow = true;
        this.onViewSettingUpdate(this.viewOptions, true);
        this.zoomFit();
      });
    }


  }

  restartAutoRefreshWithNewSettings(duration) {
    this.autoRefreshTimerInstance = setInterval(() => {
      this.notification_arr = [];
      this.switches_location_changes = false;
      if (this.viewOptions.FLOW_CHECKED) {
        this.getSwitchList();
      } else {
        this.getSwitchList();
        this.getSwitchLinks();
      }
    }, duration * 1000);
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
      const key = d.source_switch + '_' + d.target_switch;
      const key1 =  d.target_switch + '_' + d.source_switch;
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

    this.graphFlowCount.attr('transform', function(d, index) {
      const xvalue = (d.source.y + d.target.y) / 2;
      const yvalue = (d.source.x + d.target.x) / 2;
      if (d.source_switch == d.target_switch) {
        return 'translate(' + (yvalue + 70) + ',' + (xvalue - 70) + ')';
      } else {
        return 'translate(' + yvalue + ',' + xvalue + ')';
      }
    });
  }

  updateCoordinates = () => {
    const coordinates = {};
    this.nodes.forEach(function(d) {
      coordinates[d.switch_id] = [
        Math.round(d.x * 100) / 100,
        Math.round(d.y * 100) / 100
      ];
    });

    this.topologyService.setCoordinates(coordinates);
    this.syncUserCoordinatesChanges();
  }

  dragStart = (event, d) => {
    if (!event.active) { this.forceSimulation.alphaTarget(1).stop(); }
    jQuery('#topology-hover-txt').hide();
    jQuery('#topology-click-txt').hide();
  }

  dragging = (event, d) => {
    jQuery('#topology-hover-txt').hide();
    jQuery('#topology-click-txt').hide();
    this.isDragMove = true;
    d.py += event.dy;
    d.x += event.dx;
    d.y += event.dy;
    this.tick();
  }

  dragEnd = (event, d) => {
    if (!event.active) { this.forceSimulation.alphaTarget(0); }
    this.flagHover = false;
    d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    this.tick();
    this.updateCoordinates();
  }

  horizontallyBound = (parentDiv, childDiv) => {
    const parentRect: any = parentDiv.getBoundingClientRect();
    const childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  }

  showFlowDetails = d => {
    const url = 'flows?src=' + d.source_switch_name + '&dst=' + d.target_switch_name;
    window.location.href = url;
  }

  showSwitchDetails = d => {
    localStorage.setItem('switchDetailsJSON', JSON.stringify(d));
    window.location.href = 'switches/details/' + d.switch_id;
  }

  showLinkDetails = d => {
    localStorage.setItem('linkData', JSON.stringify(d));
    const url = 'isl/switch/isl/' + d.source_switch + '/' + d.src_port + '/' + d.target_switch + '/' + d.dst_port;
    window.location.href = url;
  }

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
  }

  randomPoint = (min, max) => {
    let num1 = Math.random() * (max - min) + min;
    const num = Math.floor(Math.random() * 99) + 1;
    return (num1 *= Math.floor(Math.random() * 2) == 1 ? 1 : -1);
  }

  zoomReset = () => {
    this.topologyService.setCoordinates(null);
    const coordinates = {};
    this.forceSimulation = d3
    .forceSimulation()
    .velocityDecay(0.2)
    .force('charge_force', d3.forceManyBody().strength(-1000))
    .force('xPos', d3.forceX(this.width / 2))
    .force('yPos', d3.forceY(this.height / 2));

    this.forceSimulation.nodes(this.nodes);
    this.forceSimulation.force('link', d3.forceLink().links(this.links).distance((d: any) => {
      let distance = 150;
       try {
      if (!d.flow_count) {
        if (d.speed == '40000000') {
          distance = 100;
        } else {
          distance = 300;
        }
       }
       } catch (e) {}
       return distance;
     }).strength(0.1));
    this.forceSimulation.stop();
    this.insertLinks(this.links);
    this.insertNodes(this.nodes);
    this.insertCircles();

    this.forceSimulation.restart();
    this.zoomFit();
    this.forceSimulation.on('tick', () => {
      this.tick();
      this.zoomFit();
      this.updateCoordinates();
      const positions = this.topologyService.getCoordinates();
      this.topologyService.setCoordinates(positions);
    });

    setTimeout(() => {
      this.forceSimulation.stop();
      this.zoomFit();
    }, 500);

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
      .scale(this.min_zoom)
     .translate(
      ((fullWidth / 2 - this.min_zoom * midX) / this.min_zoom) - bounds.x,
      ((fullHeight / 2 - this.min_zoom * midY) / this.min_zoom)
      );
      this.svgElement.transition().duration(300).call(this.zoom.transform, newtranformation);
    }

  }

  toggleSearch = () => {
    this.searchView = this.searchView ? false : true;
    this.searchModel = '';
    if (this.searchView) {
      const element = this.renderer.selectRootElement('#search-bar');
      setTimeout(() => element.focus(), 0);
      this.searchHidden = false;
    } else {
      this.searchHidden = true;
    }
  }

  searchNode = (selectedVal) => {
    this.searchView = false;
    selectedVal = $.trim(selectedVal);
    this.searchModel = '';
    if ($.inArray(selectedVal, this.optArray) > -1) {
      const node = this.svgElement.selectAll('.node');
      if (selectedVal == 'none') {
        // node.style("stroke", "#666").style("stroke-width", "1");
      } else {
        d3.selectAll('g.node').each(function(d: any) {
          const element = document.getElementById('circle_' + d.switch_id);
          let classes = 'circle blue';
          if (d.state && d.state.toLowerCase() == 'deactivated') {
            classes = 'circle red';
          }
          element.setAttribute('class', classes);
        });

        const unmatched = node.filter(function(d, i) {
          return d.name != selectedVal;
        });

        const matched = node.filter(function(d, i) {
          return d.name == selectedVal;
        });

        unmatched.style('opacity', '0');

        matched.filter(function(d, index) {
          const element = document.getElementById('circle_' + d.switch_id);
          let classes = 'circle blue hover';
          if (d.state && d.state.toLowerCase() == 'deactivated') {
            classes = 'circle red hover';
          }
          element.setAttribute('class', classes);
        });

        const link = this.svgElement.selectAll('.link');
        link.style('opacity', '0');

        const circle = this.svgElement.selectAll('.flow-circle');
        circle.style('opacity', '0');

        d3.selectAll('.node, .link, .flow-circle')
          .transition()
          .duration(5000)
          .style('opacity', 1);
      }
    }
  }

  onViewSettingUpdate = (
    setting: TopologyView,
    initialLoad: boolean = false
  ) => {
    if (setting.SWITCH_CHECKED) {
      $('.switchname').removeClass('hide');
    } else {
      $('.switchname').addClass('hide');
    }

    if (setting.ISL_CHECKED) {
      d3.selectAll('.physical')
        .transition()
        .duration(500)
        .style('visibility', 'visible');
    } else {
      d3.selectAll('.physical')
        .transition()
        .duration(500)
        .style('visibility', 'hidden');
    }

    if (setting.FLOW_CHECKED) {
      d3.selectAll('.logical,.flow-circle')
        .transition()
        .duration(500)
        .style('opacity', 1)
        .style('visibility', 'visible');
      if (!initialLoad && this.graphdata.flow.length == 0) {
        this.graphShow = true;
        window.location.reload();
      }
    } else {
      d3.selectAll('.logical,.flow-circle')
        .transition()
        .duration(500)
        .style('opacity', 0)
        .style('visibility', 'hidden');
    }

    if (setting.WORLDMAP) {
        this.showWorldMap = true;
        const isPopup = $('#worldmap').find('#popup');
        if (isPopup.length == 0) {
          this.addOverlayHtml();
        }
    } else {
      this.showWorldMap = false;
    }

    this.onAutoRefreshSettingUpdate(setting);
  }
  addOverlayHtml() {
    let html = '<div #popup id="popup" class="ol-popup"><a  title="Minimize" href="javascript:void(0)" style="display:none;" id="popup-minimize" class="ol-popup-minimize"><i class="fa fa-window-minimize" aria-hidden="true"></i></a><a  title="Maximize" href="javascript:void(0)" id="popup-maximize" class="ol-popup-maximize"><i class="fa fa-window-maximize" aria-hidden="true"></i></a><a  href="#" id="popup-closer" class="ol-popup-closer"><i class="fa fa-window-close" aria-hidden="true"></i></a>';
    html += '<div id="graph_loader" style="display:none;"><span style="padding:150px 180px;float:left;">Loading...</span></div> <div #content id="popup-content" ></div></div>';
    html += '<div #popinfoContainer id="popInfoContainer" class="ol-popup-info"><a  href="#" id="popInfocloser" class="ol-popup-closer"></a>';
    html += '<div #popInfocontent id="popInfocontent" ></div></div>';
    jQuery('#worldmap').append(html);

  }

  onAutoRefreshSettingUpdate = (setting: TopologyView) => {
    if (this.autoRefreshTimerInstance) {
      clearInterval(this.autoRefreshTimerInstance);
    }

    if (setting.REFRESH_CHECKED) {
      this.restartAutoRefreshWithNewSettings(setting.REFRESH_INTERVAL);
    }
  }

  saveCoordinates = () => {
    if (this.topologyService.isCoordinatesChanged()) {
        const coordinates = this.topologyService.getCoordinates();
        if (coordinates) {
          this.userService
            .saveSettings(coordinates)
            .subscribe(() => { this.topologyService.setCoordinateChangeStatus('NO'); }, error => {});
        }
    }

  }

  dblclick = (d, index) => {
    const element = document.getElementById('circle_' + d.switch_id);
    let classes = 'circle blue';
    if (d.state && d.state.toLowerCase() == 'deactivated') {
      classes = 'circle red';
    }
    element.setAttribute('class', classes);
    this.showSwitchDetails(d);
  }

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

    jQuery('#close_switch_detail').click(function() {
      jQuery('#topology-click-txt').css('display', 'none');
    });


  }

  syncUserCoordinatesChanges() {
    if (this.syncCoordinates) {
      clearTimeout(this.syncCoordinates);
    }
    this.syncCoordinates = setTimeout(() => {
      this.saveCoordinates();
    }, 1500);
  }

  ngOnDestroy() {
    if (this.autoRefreshTimerInstance) {
      clearInterval(this.autoRefreshTimerInstance);
    }

    if (this.syncCoordinates) {
      clearTimeout(this.syncCoordinates);
    }

  }

}
