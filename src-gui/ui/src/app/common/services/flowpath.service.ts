import { Injectable } from '@angular/core';
import { CommonService } from './common.service';
import * as d3 from 'd3';
import { environment } from 'src/environments/environment';
import { Subject } from 'rxjs';
import { Router } from '@angular/router';

@Injectable({
  providedIn: 'root'
})
export class FlowpathService {
  forwardPathLoaded = false;
  reversepathLoaded = false;
  forwardpathLoadedChange: Subject<boolean> = new Subject<boolean>();
  reversepathLoadedChange: Subject<boolean> = new Subject<boolean>();
  diverseGroupCommonSwitch: any = [];
  diverseGroupCommonSwitchReverse: any = [];
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
  isDragMoveForward: any = false;
  isDragMoveReverse: any = false;
  dragforward: any;
  dragreverse: any;
  graphLinkArr = {};
  graphPortArrSource = {};
  graphPortArrTarget = {};
  graphNodeArr = {};
  linksSource = {};
  graphportGroupTarget = {};
  graphportGroupSource = {};
  linkNum = {};
  svgElementArr = {};

  constructor(private commonService: CommonService,
    private router: Router
    ) { }


  setCommonSwitch (type, data) {
    if (type == 'forward') {
      this.diverseGroupCommonSwitch = data;
    } else {
      this.diverseGroupCommonSwitchReverse = data;
    }
  }
  getcommonSwitches(type) {
    if (type == 'forward') {
      return this.diverseGroupCommonSwitch;
    } else {
      return this.diverseGroupCommonSwitchReverse;
    }
  }
  loadIslDetail(src_switch, src_port, dst_switch, dst_port) {
    this.router.navigate(['/isl/switch/isl/' + src_switch + '/' + src_port + '/' + dst_switch + '/' + dst_port]);
   }


   loadSwitchDetail(switchId) {
       this.router.navigate(['/switches/details/' + switchId]);
   }
  forwardLoaderChange() {
      this.forwardPathLoaded = false;
      this.forwardpathLoadedChange.next(this.forwardPathLoaded);
  }
  reverseLoaderChange() {
    this.reversepathLoaded = false;
    this.reversepathLoadedChange.next(this.reversepathLoaded);
 }
  horizontallyBound = (parentDiv, childDiv) => {
    const parentRect: any = parentDiv.getBoundingClientRect();
    const childRect: any = childDiv.getBoundingClientRect();
    return (
      parentRect.left <= childRect.left && parentRect.right >= childRect.right
    );
  }

  dragStartForward = (event) => {
    const simulation = this.simulationArr['forwardDiverse'];
    if (!event.active) { simulation.alphaTarget(1).stop(); }
  }

  draggingForward = (event, d) => {
    this.isDragMoveForward = true;
    d.py += event.dy;
    d.x += event.dx;
    d.y += event.dy;
    this.tick( this.graphLinkArr['forwardDiverse'], this.graphNodeArr['forwardDiverse'],
        this.graphPortArrSource['forwardDiverse'], this.graphPortArrTarget['forwardDiverse'],
        this.linksSource['forwardDiverse'], this.linkNum['forwardDiverse']);
  }

  dragEndForward = (event, d) => {
    const simulation = this.simulationArr['forwardDiverse'];
    if (!event.active) { simulation.alphaTarget(0); }
    d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    this.tick( this.graphLinkArr['forwardDiverse'], this.graphNodeArr['forwardDiverse'], this.graphPortArrSource['forwardDiverse'],
        this.graphPortArrTarget['forwardDiverse'], this.linksSource['forwardDiverse'], this.linkNum['forwardDiverse']);
  }
  // for reverse
  dragStartReverse = (event, d) => {
    const simulation = this.simulationArr['reverseDiverse'];
    if (!event.active) { simulation.alphaTarget(1).stop(); }
    jQuery('#topology-hover-txt').hide();
    jQuery('#topology-click-txt').hide();
  }

  draggingReverse = (event, d) => {
    this.isDragMoveReverse = true;
    d.py += event.dy;
    d.x += event.dx;
    d.y += event.dy;
    this.tick( this.graphLinkArr['reverseDiverse'], this.graphNodeArr['reverseDiverse'], this.graphPortArrSource['reverseDiverse'],
        this.graphPortArrTarget['reverseDiverse'], this.linksSource['reverseDiverse'], this.linkNum['reverseDiverse']);
  }

  dragEndReverse = (event, d) => {
    const simulation = this.simulationArr['reverseDiverse'];
    if (!event.active) { simulation.alphaTarget(0); }
    d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    this.tick( this.graphLinkArr['reverseDiverse'], this.graphNodeArr['reverseDiverse'], this.graphPortArrSource['reverseDiverse'],
        this.graphPortArrTarget['reverseDiverse'], this.linksSource['reverseDiverse'], this.linkNum['reverseDiverse']);
  }
  initSimulation(nodes, links, svgElement, graphWrapper, type, positions, hoverTextID, showValueID, hideValueID) {
    this.svgElementArr[type] = svgElement;
    const linksSourceArr = [];
    const self = this;
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

                const prcessKey = ( linksSourceArr && typeof linksSourceArr[key] !== 'undefined') ? key : key1;
                if (typeof linksSourceArr[prcessKey] !== 'undefined') {
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
      this.linksSource[type] = linksSourceArr;
      const processedlinks = this.processLinks(nodes, links);
      let  zoomLevel = 0.45;
      svgElement.html('');
      const width = $('#' + graphWrapper)[0].clientWidth || window.innerWidth;
      const height = svgElement.attr('height');
      svgElement.style('cursor', 'move');
      svgElement.attr('width', width);
      svgElement.attr('height', height);
      const g = svgElement.append('g');
      const graphLinkGroup = g.append('g').attr('id', `links`).attr('class', 'links');
      const graphNodeGroup = g.append('g').attr('class', 'nodes').attr('id', 'nodes');
      this.graphportGroupSource[type] = g.append('g').attr('id', `sourcePorts`).attr('class', 'sourcePorts');
      this.graphportGroupTarget[type] = g.append('g').attr('id', `targetPorts`).attr('class', 'targetPorts');
      const size = d3
      .scalePow()
      .exponent(1)
      .domain(d3.range(1));
      const zoom = this.zoomArr[type] =  d3
      .zoom()
      .scaleExtent([this.min_zoom, this.max_zoom])
      .extent([[0, 0], [width - 200, height - 50]])
      .on('zoom', (event, d) => {
        zoomLevel = Math.round(event.transform.k * 100) / 100;
        self.zoomLevelArr[type] = zoomLevel;
        g.attr(
          'transform',
          'translate(' +
            event.transform.x +
            ',' +
            event.transform.y +
            ') scale(' +
            event.transform.k +
            ')'
        );

      });
      const mLinkNum = this.linkNum[type] =  this.setLinkIndexAndNum(processedlinks);
      const forceSimulation  =  d3
      .forceSimulation()
      .velocityDecay(0.2)
      .force('collision', d3.forceCollide().radius(function(d) {
            return 20;
      }))
      .force('charge_force', d3.forceManyBody().strength(-1000))
      .force('xPos', d3.forceX(width / 2))
      .force('yPos', d3.forceY(height / 2));
      forceSimulation.nodes(nodes);
      forceSimulation.force('link', d3.forceLink().links(processedlinks).distance((d: any) => {
         let distance = 10;
        if (d.type == 'isl') {
          distance = 150;
        }
        return distance;
      }).strength(0.1));
      forceSimulation.stop();
      const graphNode = this.graphNodeArr[type] = this.insertNodes(graphNodeGroup, nodes, type, hoverTextID, showValueID, hideValueID);
      const graphLink = this.graphLinkArr[type] =  this.insertLinks(graphWrapper, graphLinkGroup, processedlinks, type, hoverTextID, showValueID, hideValueID);
       const graphPortSource = this.graphPortArrSource[type] =  this.insertSourcePorts(processedlinks, type, hoverTextID, showValueID, hideValueID);
       const graphPortTarget = this.graphPortArrTarget[type] =  this.insertTargetPorts(processedlinks, type, hoverTextID, showValueID, hideValueID);

      forceSimulation.on('tick', () => {
      this.tick(graphLink, graphNode, graphPortSource, graphPortTarget, linksSourceArr, mLinkNum);

      });
      if (type == 'forwardDiverse') {
        this.dragforward = d3
        .drag()
        .on('start', this.dragStartForward)
        .on('drag', this.draggingForward)
        .on('end', this.dragEndForward);
      } else if (type == 'reverseDiverse') {
        this.dragreverse = d3
        .drag()
        .on('start', this.dragStartReverse)
        .on('drag', this.draggingReverse)
        .on('end', this.dragEndReverse);
      }

      svgElement.call(zoom);
      svgElement.on('dblclick.zoom', null);
      forceSimulation.restart();
      forceSimulation.on('end', () => {
        this.zoomFit(g, svgElement, zoomLevel, zoom, nodes, type);
    });
    this.simulationArr[type] = forceSimulation;
  }

  zoomFit = (g, svgElement, zoomLevel, zoom, nodes, type) => {
    const bounds = g.node().getBBox();
    const parent = g.node().parentElement;
    const fullWidth =  parent.clientWidth || parent.parentNode.clientWidth || $(parent).width(),
      fullHeight =  parent.clientHeight || parent.parentNode.clientHeight || $(parent).height();
    const width = bounds.width,
      height = bounds.height;
    const midX = (bounds.x + width) / 2,
      midY = (bounds.y + height) / 2;
    if (width == 0 || height == 0) { return; }
    let scale: number;
    let translate: number[];
    if (nodes.length > 10) {
       scale = 0.50;
       translate = [(fullWidth / 2  - scale * midX) / scale, (fullHeight / 2  - scale * midY) / scale];
    } else {
       scale = (zoomLevel || 1.30) / Math.max(width / fullWidth, height / fullHeight);
       translate = [fullWidth / 2 - scale * midX, fullHeight / 2 - scale * midY];
    }

    const newtranformation = d3.zoomIdentity
      .scale(scale)
     .translate(translate[0], translate[1]);
      svgElement.transition().duration(300).call(zoom.transform, newtranformation);
      if (type == 'forwardDiverse') {
        this.forwardLoaderChange();
      } else {
        this.reverseLoaderChange();
      }
    }

  zoomFn(direction, svgElement, type) {

    const simulation = this.simulationArr[type];
    const zoom  = this.zoomArr[type];
    const zoomLevel = this.zoomLevelArr[type];
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


  insertLinks(graphWrapper, graphLinkGroup, links, type, hoverTextID, showValueID, hideValueID) {
    const ref = this;
    const graphLinksData = graphLinkGroup.selectAll('path.link').data(links);
     const graphNewLink = graphLinksData
      .enter()
      .append('path')
      .attr('class', function(d, index) {
        if (d.link_type == 'protected') {
          return 'link_' + type + ' link dashed_path_protected physical ' + type + '_link_' + d.flow;
        }
        return 'link_' + type + ' link physical ' + type + '_link_' + d.flow;
      })
      .attr('id', (d, index) => {
        return type + '_link' + index;
      }).attr('stroke-width', (d) => 4.5).attr('stroke', function(d, index) {
               return d.colourCode;
      }).attr('cursor', 'pointer')
      .on('mouseover', function(event, d) {
        const index = d.index;
        if (type == 'forwardDiverse' || type == 'reverseDiverse' ) {
          const element = document.getElementById(type + '_link' + index);
          let classes = element.getAttribute('class');
          classes = classes + ' overlay';
          element.setAttribute('class', classes);
           const rec: any = element.getBoundingClientRect();
           $('#' + hideValueID).css('display', 'none');
            $('#' + hoverTextID).css('display', 'block');
            $('#' + showValueID).html(d.flow);
            $('#' + showValueID).css('display', 'block');


             $(element).on('mousemove', function(e) {
              $('#' + hoverTextID).css('top', (e.pageY - 50) + 'px');
              $('#' + hoverTextID).css('left', (e.pageX - 60) + 'px');
              const bound = ref.horizontallyBound(
                document.getElementById(graphWrapper),
                document.getElementById(hoverTextID)
              );

              if (bound) {
                $('#' + hoverTextID).removeClass('left');
              } else {
                const left = e.pageX; // subtract width of tooltip box + circle radius
                $('#' + hoverTextID).css('left', left + 'px');
                $('#' + hoverTextID).addClass('left');
              }
            });
        }

      }).on('mouseout', function(event, d) {
        $('#' + type + '_link' + d.index).removeClass('overlay');
        $('#' + hoverTextID).css('display', 'none');
      }).on('click', function(event, d) {
        if (d.type == 'isl') {
          const src_switch = d.source_detail.id,
          src_port = d.source_detail.out_port,
          dst_switch = d.target_detail.id,
          dst_port = d.target_detail.in_port;
          ref.loadIslDetail(src_switch, src_port, dst_switch, dst_port);
        }

      });


     graphLinksData.exit().remove();
     return  graphNewLink.merge(graphLinksData);
  }

  insertTargetPorts(links, type, hoverTextID, showValueID, hideValueID) {
    const linkText = this.graphportGroupTarget[type].selectAll('circle').data(links);
    const linkCircleTextTarget = linkText
                        .enter()
                        .append('g')
                        .attr('class', 'text-circle targetEnd');
    linkText.exit().remove();
    linkCircleTextTarget.append('circle')
        .attr('class', function(d) {
          let classes = 'circle-text targetEnd ' + type + '_port_circle  ' + type + '_port_circle_' + d.flow;
          classes = classes + ' ' + d.source.switch_id + '_' + d.target.switch_id + '_' + d.flow;
          return classes;
        }).attr('id', function(d) {
          return 'textCircle_target_' + type + '_' + d.target.switch_id;
        })
        .attr('r', '8')
        .attr('stroke', '#00baff')
        .attr('stroke-width', '1px')
        .attr('fill', '#FFF').attr('style', 'cursor:pointer')
        .on('mouseover', function(event, d) {
          if (type == 'forwardDiverse' || type == 'reverseDiverse' ) {
            const element = document.getElementById('textCircle_target_' + type + '_' + d.target.switch_id);
             const rec: any = element.getBoundingClientRect();
             $('#' + hideValueID).css('display', 'none');
             $('#' + hoverTextID).css('display', 'block');
             $('#' + showValueID).html(d.target.switch_name || d.target.switch_id);
             $('#' + showValueID).css('display', 'block');
              const x =  document.getElementById('textCircle_target_' + type + '_' + d.target.switch_id).getBoundingClientRect().left - 70;
              const y =  document.getElementById('textCircle_target_' + type + '_' + d.target.switch_id).getBoundingClientRect().top - 50;
              $('#' + hoverTextID).css('top', (y) + 'px');
              $('#' + hoverTextID).css('left', (x) + 'px');

          }

        }).on('mouseout', function(event, d) {
          $('#' + hoverTextID).css('display', 'none');
        });

        linkCircleTextTarget.append('text')
        .attr('class', function(d) {
          let classes = 'zEnd ' + type + '_port_text ' + type + '_port_text_' + d.flow;
          classes = classes + ' ' + d.source.switch_id + '_' + d.target.switch_id + '_' + d.flow;
          return classes;
        }).attr('dx', function(d) {
          if (d.target_detail.in_port >= 10) {
            return '-6';
          }
          return '-3';
        })
        .attr('dy', function(d) {
          if (d.target_detail.in_port >= 10) {
            return '3';
          }
          return '5';
        })
        .attr('fill', '#000').text(function(d) {
          return d.target_detail.in_port;
        });


      return linkCircleTextTarget.merge(linkText);
  }

  insertSourcePorts(links, type, hoverTextID, showValueID, hideValueID) {
    const linkText = this.graphportGroupSource[type].selectAll('circle').data(links);
    const linkCircleTextSource = linkText
                        .enter()
                        .append('g')
                        .attr('class', 'text-circle sourceEnd');
    linkText.exit().remove();
    linkCircleTextSource.append('circle')
        .attr('class', function(d) {
          let classes = 'circle-text sourceEnd ' + type + '_port_circle  ' + type + '_port_circle_' + d.flow;
          classes = classes + ' ' + d.source.switch_id + '_' + d.target.switch_id + '_' + d.flow;
          return classes;
        }).attr('id', function(d) {
          return 'textCircle_source_' + type + '_' + d.source.switch_id;
        })
        .attr('r', '8')
        .attr('stroke', '#00baff')
        .attr('stroke-width', '1px')
        .attr('fill', '#FFF').attr('style', 'cursor:pointer')
        .on('mouseover', function(event, d) {
          if (type == 'forwardDiverse' || type == 'reverseDiverse' ) {
            const element = document.getElementById('textCircle_source_' + type + '_' + d.source.switch_id);
             const rec: any = element.getBoundingClientRect();
             $('#' + hideValueID).css('display', 'none');
              $('#' + hoverTextID).css('display', 'block');
              $('#' + showValueID).html(d.source.switch_name || d.source.switch_id);
              $('#' + showValueID).css('display', 'block');
              const x =  document.getElementById('textCircle_source_' + type + '_' + d.source.switch_id).getBoundingClientRect().left - 70;
              const y =  document.getElementById('textCircle_source_' + type + '_' + d.source.switch_id).getBoundingClientRect().top - 50;

              $('#' + hoverTextID).css('top', (y) + 'px');
              $('#' + hoverTextID).css('left', (x) + 'px');

          }

        }).on('mouseout', function(event, d) {
           $('#' + hoverTextID).css('display', 'none');
        });

        linkCircleTextSource.append('text')
        .attr('class', function(d) {
          let classes = 'aEnd ' + type + '_port_text ' + type + '_port_text_' + d.flow;
          classes = classes + ' ' + d.source.switch_id + '_' + d.target.switch_id + '_' + d.flow;
          return classes;
        }).attr('dx', function(d) {
          if (d.source_detail.out_port >= 10) {
            return '-6';
          }
          return '-3';
        })
        .attr('dy', function(d) {
          if (d.source_detail.out_port >= 10) {
            return '3';
          }
          return '5';
        })
        .attr('fill', '#000').text(function(d) {
          return d.source_detail.out_port;
        });


      return linkCircleTextSource.merge(linkText);
  }
  insertNodes(graphNodeGroup, nodes, type, hoverTextID, showValueID, hideValueID) {
    const ref = this;
    const graphNodesData = graphNodeGroup.selectAll('g.node').data(nodes, d => d.switch_id);
    let graphNodeElement: any;
    if (type == 'forwardDiverse') {
       graphNodeElement = graphNodesData.enter().append('g')
        .attr('class', 'node')
        .call(

            d3
            .drag()
            .on('start', this.dragStartForward)
            .on('drag', this.draggingForward)
            .on('end', this.dragEndForward)
        );
  } else if (type == 'reverseDiverse') {
     graphNodeElement = graphNodesData.enter().append('g')
      .attr('class', 'node')
      .call(
          d3
          .drag()
          .on('start', this.dragStartReverse)
          .on('drag', this.draggingReverse)
          .on('end', this.dragEndReverse)
    );
}

    graphNodesData.exit().remove();

     graphNodeElement.append('circle')
                        .attr('r', this.graphOptions.radius)
                        .attr('class', function(d, index) {
                          const switchcls = d.switch_id.split(':').join('_');
                          const classes = 'circle ' + type + '_circle blue ' + type + '_circle_' + d.flow + ' sw_' + switchcls;
                           return classes;
                        })
                      .attr('id', function(d, index) {
                          return type + '_circle_' + d.switch_id;
                      }).style('cursor', 'pointer')
                      .on('click', function(event, d) {
                          ref.loadSwitchDetail(d.switch_id);
                      });
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
                      }).attr('class', function(d) {
                        const switchcls = d.switch_id.split(':').join('_');
                        return type + '_switch_image ' + type + '_switch_image_' + d.flow + ' sw_img_' + switchcls;
                      })
                      .attr('height', 58)
                      .attr('width', 58)
                      .attr('id', function(d, index) {
                        return 'image_' + index;
                      })
                      .attr('cursor', 'pointer')
                      .on('mouseover', function(event, d) {
                        if (type == 'forwardDiverse' || type == 'reverseDiverse' ) {
                          const element = document.getElementById(type + '_circle_' + d.switch_id);
                           const rec: any = element.getBoundingClientRect();
                           $('#' + hideValueID).css('display', 'none');
                           $('#' + hoverTextID).css('display', 'block');
                           $('#' + showValueID).html(d.switch_name || d.switch_id);
                           $('#' + showValueID).css('display', 'block');
                            const x =  document.getElementById(type + '_circle_' + d.switch_id).getBoundingClientRect().left - 70;
                            const y =  document.getElementById(type + '_circle_' + d.switch_id).getBoundingClientRect().top - 50;
                            $('#' + hoverTextID).css('top', (y) + 'px');
                            $('#' + hoverTextID).css('left', (x) + 'px');

                        }

                      }).on('mouseout', function(event, d) {
                        $('#' + hoverTextID).css('display', 'none');
                      });

    return graphNodeElement.merge(graphNodesData);

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

  tick(graphLink, graphNode, graphPortSource, graphPortTarget, linksSourceArr, mLinkNum) {
    const ref = this;
      graphLink.attr('d', d => {
      let islCount = 0;
      let matchedIndex = 1;
      const key = d.source.switch_id + '_' + d.target.switch_id;
      const key1 =  d.target.switch_id + '_' + d.source.switch_id;
      const processKey = ( linksSourceArr && typeof linksSourceArr[key] !== 'undefined') ? key : key1;
      if (
        linksSourceArr &&
        typeof linksSourceArr[processKey] !== 'undefined'
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
      const processKeyValues = processKey.split('_');
      if (processKeyValues[0] == d.target.switch_id && processKeyValues[1] == d.source.switch_id) {
          matchedIndex = matchedIndex + 1;
      }
      let x1 = d.source.x,
        y1 = d.source.y,
        x2 = d.target.x,
        y2 = d.target.y,
        dx = x2 - x1,
        dy = y2 - y1,
        dr = Math.sqrt(dx * dx + dy * dy);
      const lTotalLinkNum =
        mLinkNum[d.source.index + ',' + d.target.index] ||
        mLinkNum[d.target.index + ',' + d.source.index];

      if (lTotalLinkNum > 1) {
        dr = dr / (1 + (1 / lTotalLinkNum) * (d.linkindex - 1));
      }
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

    });
    graphNode.attr('transform', function(d) {
      if (typeof(d.x) != 'undefined' && typeof(d.y) != 'undefined') {
          return 'translate(' + d.x + ',' + d.y + ')';
        }
      });

    graphPortSource.attr('transform', function(d) {
       let yvalue = (d.source.y + d.target.y) / 2;
       let xvalue = (d.source.x + d.target.x) / 2;
       const points = ref.getCornerPoint(d.source.x, d.source.y, d.target.x, d.target.y);
        if (typeof(points) != 'undefined' && points.length) {
           xvalue = points[0];
           yvalue = points[1];
        }
        const key = d.source.switch_id + '_' + d.target.switch_id;
        const key1 =  d.target.switch_id + '_' + d.source.switch_id;
        const processKey = ( linksSourceArr && typeof linksSourceArr[key] !== 'undefined') ? key : key1;
        const processKeyValues = processKey.split('_');
        const linkArr = linksSourceArr[processKey];
        if (processKeyValues[0] == d.target.switch_id && processKeyValues[1] == d.source.switch_id) {
          return 'translate(' + (xvalue + 10) + ',' + (yvalue - 10) + ')';
        }
       return 'translate(' + xvalue + ',' + yvalue + ')';

    });
    graphPortTarget.attr('transform', function(d) {
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
  getCornerPoint(x1, y1, x2, y2) {
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


}
