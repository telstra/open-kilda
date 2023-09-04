import { Component, OnInit, Input, OnDestroy } from '@angular/core';
import { FlowsService } from '../../../common/services/flows.service';
import { NgxSpinnerService } from 'ngx-spinner';
import { LoaderService } from '../../../common/services/loader.service';
import { Router } from '@angular/router';
import * as d3 from 'd3';
import { CommonService } from 'src/app/common/services/common.service';
import { FlowpathService } from 'src/app/common/services/flowpath.service';
import { MessageObj } from 'src/app/common/constants/constants';
@Component({
  selector: 'app-flow-path',
  templateUrl: './flow-path.component.html',
  styleUrls: ['./flow-path.component.css']
})
export class FlowPathComponent implements OnInit, OnDestroy {
  @Input() flowId;


  flowPathData: any;
  forwardPathGraph: Boolean = false;
  reversePathGraph: Boolean = false;
  reversePathLoader = false;
  loadreversePath = false;
  reverseGraphSvg = false;
  forwardGraphSvg = false;
  forwardDiverse: any = 0.5;
  reverseDiverse: any = 0.5;
  forwardPathLoader = false;
  forwardLoader = false;
  loadforward = false;
  loadforwardPath = false;
  showReversePath = true;
  showFlowsForward = false;
  showFlowsReverse = false;
  isDiverseForward = false;
  isDiverseReverse = false;
  flowPathFlagForward: any = [];
  flowPathFlagReverse: any = [];
  commonSwitchFlagReverse = false;
  commonSwitchFlagForward = false;
  forwardLabelText = 'FORWARD PATH';
  reverseLabelText = 'REVERSE PATH';
  diversePath = {};
  protectedPath = {};
  diverseGroup = [];
  colourCodes = [];
  pathFlows = [];
  protectedPathFlows = [];
  hasDiverseGroup = false;
  hasProtectedPath = false;
  forwardPathSwitches = [];
  reversePathSwitches = [];
  reversePathData = [];
  forwardPathData = [];
  showDiverseGroupReverse = false;
  showDiverseGroupForward = false;
  showProtectedGroupForward = false;
  showProtectedGroupReverse = false;
  diverseUniqueSwitches = [];
  diverseGroupCommonSwitch = [];
  toggleFilter = 'pathforward';
  toggleFilterReverse = 'pathreverse';
  showReverseProtectedPath = false;
  showForwardProtectedPath = false;

  pathData = [] ;


  constructor(
    private flowService: FlowsService,
    private loaderService: LoaderService,
    private commonService: CommonService,
    private flowpathService: FlowpathService
  ) {}

  ngOnInit() {
    if (this.flowId) {
      this.getFlowPath(this.flowId);

    } else {
      console.error('Flow Id required');
    }
  }


  getFlowPath(flowId) {
    const self = this;
    this.loaderService.show(MessageObj.loading_flow_path);
    this.flowService.getFlowPath(flowId).subscribe(
      data => {
        this.flowPathData = data;
        this.forwardPathData = data.flowpath_forward;
        this.reversePathData = data.flowpath_reverse;
        this.hasProtectedPath = this.flowPathData['protected_path'] &&  this.flowPathData['protected_path']['flowpath_forward'];
        this.loadDiverseGroup();
        this.loaderService.hide();
      },
      error => {
        console.log('error in data');
        this.loaderService.hide();
      }
    );
  }

  showProtectedPath(type, e) {
    this.forwardPathGraph = false;
    this.reversePathGraph = false;
    if (type == 'forward') {
      this.forwardLabelText = 'FORWARD PROTECTED PATH';
      this.showForwardProtectedPath = (this.showForwardProtectedPath) ? false : true;
    } else if (type == 'reverse') {
      this.showReverseProtectedPath = (this.showReverseProtectedPath) ? false : true;
      this.reverseLabelText = 'REVERSE PROTECTED PATH';
    }
  }

  plotForwardDiverse() {
    const self = this;
    const commonSwitches = [];
    const links = [];
    const diverseUniqueSwitches = [];
    const nodes = [];
    const diverseGroupCommonSwitch = [];
      Object.keys(this.diversePath).map(function(i, v) {
        let j = 0;
        for (const d of self.diversePath[i].forward_path) {
          const path_type = self.diversePath[i].path_type;
        if (j < self.diversePath[i].forward_path.length) {  j++;
            commonSwitches.push({switch_id: d.switch_id, switch_name: d.switch_name, flow: i});
            if (typeof(self.diversePath[i].forward_path[j]) != 'undefined') {
              links.push({link_type: path_type, flow: i, source_detail: {out_port: d.output_port, in_port: d.input_port, id: d.switch_id}, target_detail: {out_port: self.diversePath[i].forward_path[j].output_port, in_port: self.diversePath[i].forward_path[j].input_port, id: self.diversePath[i].forward_path[j].switch_id}, source: {switch_id: d.switch_id, switch_name: d.switch_name}, target: {switch_id: self.diversePath[i].forward_path[j].switch_id, switch_name: self.diversePath[i].forward_path[j].switch_name}, colourCode: self.colourCodes[v], type: 'isl'});
            }
          }
        }
      });
    // fetching unique switches in all diverse group
    if (commonSwitches && commonSwitches.length) {
        for (const switchid of commonSwitches) {
          if (!this.checkifSwitchExists(switchid, diverseUniqueSwitches)) {
            diverseUniqueSwitches.push(switchid);
          } else {
            if (!this.checkifSwitchExists(switchid, diverseGroupCommonSwitch)) {
              diverseGroupCommonSwitch.push(switchid);
            }
          }
        }
      }
      this.flowpathService.setCommonSwitch('forward', diverseGroupCommonSwitch);
      // creating nodes object array
     for (const d of diverseUniqueSwitches) {
         nodes.push(d);
     }
     const svgElement = d3.select('#svgForwardPath');
     const element = $('#forwardPathWrapper');
     const positions = [];
     this.forwardPathLoader = true;
      this.loadforwardPath = true;
     this.flowpathService.initSimulation(nodes, links, svgElement, 'forwardPathWrapper', 'forwardDiverse', positions, 'diversepath-hover-txt', 'forward_flow_value', 'reverse_flow_value');
     this.flowpathService.forwardpathLoadedChange.subscribe((value: any) => {
      this.forwardPathLoader = value;
      this.loadforwardPath = value;
    });
  }

  checkifSwitchExists(switchval, arr) {
    let flag = false;
    if (arr && arr.length) {
      for (const val of arr) {
        if (val.switch_id == switchval.switch_id) {
          flag = true;
          break;
        }
      }
    }
    return flag;
  }

  plotReverseDiverse() {
    const self = this;
    const commonSwitches = [];
    const links = [];
    const nodes = [];
    const diverseUniqueSwitchesReverse = [];
    const diverseGroupCommonSwitchReverse = [];
    Object.keys(this.diversePath).map(function(i, v) {
        let j = 0;
        for (const d of self.diversePath[i].reverse_path) {
          const path_type = self.diversePath[i].path_type;
        if (j < self.diversePath[i].reverse_path.length) {  j++;
           commonSwitches.push({switch_id: d.switch_id, switch_name: d.switch_name, flow: i});
           if (typeof(self.diversePath[i].reverse_path[j]) != 'undefined') {
              links.push({link_type: path_type, flow: i, source_detail: {out_port: d.output_port, in_port: d.input_port, id: d.switch_id}, target_detail: {out_port: self.diversePath[i].reverse_path[j].output_port, in_port: self.diversePath[i].reverse_path[j].input_port, id: self.diversePath[i].reverse_path[j].switch_id}, source: {switch_id: d.switch_id, switch_name: d.switch_name}, target: {switch_id: self.diversePath[i].reverse_path[j].switch_id, switch_name: self.diversePath[i].reverse_path[j].switch_name}, colourCode: self.colourCodes[v], type: 'isl'});
            }
          }
        }
      });
    // fetching unique switches in all diverse group
    if (commonSwitches && commonSwitches.length) {
        for (const switchid of commonSwitches) {
          if (!this.checkifSwitchExists(switchid, diverseUniqueSwitchesReverse)) {
            diverseUniqueSwitchesReverse.push(switchid);
          } else {
            if (!this.checkifSwitchExists(switchid, diverseGroupCommonSwitchReverse)) {
              diverseGroupCommonSwitchReverse.push(switchid);
            }

          }
        }
      }
      this.flowpathService.setCommonSwitch('reverse', diverseGroupCommonSwitchReverse);
      // creating nodes object array
     for (const d of diverseUniqueSwitchesReverse) {
       nodes.push(d);
     }
     const svgElement = d3.select('#svgReversePath');
     const element = $('#reversePathWrapper');
     this.reversePathLoader = true;
      this.loadreversePath = true;
       const positions = [];
       this.flowpathService.initSimulation(nodes, links, svgElement, 'reversePathWrapper', 'reverseDiverse', positions, 'diversepath-hover-txt', 'reverse_flow_value', 'forward_flow_value');
       this.flowpathService.reversepathLoadedChange.subscribe((value: any) => {
        this.reversePathLoader = value;
        this.loadreversePath = value;
      });
  }

  loadIslDetail(index, type) {
    if (type == 'forward') {
      const src_switch = this.forwardPathData[index].switch_id;
      const src_port = this.forwardPathData[index].output_port;
      const dst_switch = this.forwardPathData[index + 1].switch_id;
      const dst_port = this.forwardPathData[index + 1].input_port;
    } else {
      const src_switch = this.reversePathData[index].switch_id;
      const src_port = this.reversePathData[index].output_port;
      const dst_switch = this.reversePathData[index + 1].switch_id;
      const dst_port = this.reversePathData[index + 1].input_port;
    }
    this.flowpathService.loadIslDetail(src_switch, src_port, dst_switch, dst_port);
   }
  zoomFn(type, dir) {
     if (type == 'forwardDiverse') {
      const svgElement = d3.select('#svgForwardPath');
      this.showFlowsForward = false;
    } else if (type == 'reverseDiverse') {
      const svgElement = d3.select('#svgReversePath');
      this.showFlowsReverse = false;
    }

    const direction = (dir == 'in') ? 1 : -1;
    this.flowpathService.zoomFn(direction, svgElement, type);
  }
  fetchFlowCommonSwitch(flowid, type) {
    if (type == 'forward') {
      const pathData = this.diversePath[flowid].forward_path;
      const flowSwitches = [];
      for (const d in pathData) {
        flowSwitches.push(pathData[d].switch_id);
      }

    } else if ( type == 'reverse' ) {
      const pathData = this.diversePath[flowid].reverse_path;
      const flowSwitches = [];
      for (const d in pathData) {
        flowSwitches.push(pathData[d].switch_id);
      }
    }
    const commonSwitchinFlow = [];
      const dataCommon = this.flowpathService.getcommonSwitches(type);
      for (const d in dataCommon) {
        if (flowSwitches.indexOf(dataCommon[d].switch_id) != -1) {
          commonSwitchinFlow.push(dataCommon[d].switch_id);
        }
      }
    return commonSwitchinFlow;
  }
  showFlowPath(flowid, type) {
    if (type == 'forward') {
      const getCommonSwitchForFlow = this.fetchFlowCommonSwitch(flowid, type);
      const svgElement = d3.select('#svgForwardPath');
        const flows = Object.keys(this.flowPathFlagForward);
        if (typeof(this.flowPathFlagForward[flowid]) != 'undefined' && this.flowPathFlagForward[flowid]) {
          const allSwitches =  svgElement.selectAll('.forwardDiverse_circle');
          const allSwitchImages =  svgElement.selectAll('.forwardDiverse_switch_image');
          const allText =  svgElement.selectAll('text.forwardDiverse_text');
          const allPortCircles = svgElement.selectAll('.forwardDiverse_port_circle');
          const allPortText = svgElement.selectAll('.forwardDiverse_port_text');
          const allLinks = svgElement.selectAll('.link_forwardDiverse');
          d3.selectAll('.forwardDiverse_link_' + flowid)
          .transition()
          .style('stroke-width', '2.5');
          allLinks.style('opacity', '1');
          allSwitches.style('opacity', '1');
          allSwitchImages.style('opacity', '1');
          allText.style('opacity', '1');
          allPortCircles.style('opacity', '1');
          allPortText.style('opacity', '1');
          this.flowPathFlagForward[flowid] = false;
        } else {
          if (flows && flows.length) {
            flows.map((i, v) => {
              if (this.flowPathFlagForward[i]) {
                  d3.selectAll('.forwardDiverse_link_' + i)
                  .transition()
                  .style('stroke-width', '2.5');
                  this.flowPathFlagForward[i] = false;
              }
            });
          }
          const allSwitches =  svgElement.selectAll('.forwardDiverse_circle');
          const allSwitchImages =  svgElement.selectAll('.forwardDiverse_switch_image');
          const allText =  svgElement.selectAll('text.forwardDiverse_text');
          const allPortCircles = svgElement.selectAll('.forwardDiverse_port_circle');
          const allPortText = svgElement.selectAll('.forwardDiverse_port_text');
          const flowportText = svgElement.selectAll('.forwardDiverse_port_text_' + flowid);
          const flowportCircles =  svgElement.selectAll('.forwardDiverse_port_circle_' + flowid);
          const flowText =  svgElement.selectAll('text.forwardDiverse_textcircle_' + flowid);
          const flowSwitches = svgElement.selectAll('.forwardDiverse_circle_' + flowid);
          const flowSwitchImages = svgElement.selectAll('.forwardDiverse_switch_image_' + flowid);
          const allLinks = svgElement.selectAll('.link_forwardDiverse');
          const links = svgElement.selectAll('.forwardDiverse_link_' + flowid);
          allSwitches.style('opacity', 0);
          allText.style('opacity', '0');
          allSwitchImages.style('opacity', 0);
          allPortCircles.style('opacity', 0);
          allPortText.style('opacity', '0');
          allLinks.style('opacity', '0');
          flowportCircles.style('opacity', 1);
          flowportText.style('opacity', '1');
          flowText.style('opacity', 1);
          flowSwitches.style('opacity', 1);
          flowSwitchImages.style('opacity', 1);
          links.style('opacity', '1');
          links.style('stroke-width', '5');
          for (const d in getCommonSwitchForFlow) {
           const switchid = getCommonSwitchForFlow[d];
           const switchcls = switchid.split(':').join('_');
            const commonswitchinflow = svgElement.selectAll('.forwardDiverse_circle.sw_' + switchcls);
            const commonswitchinflowtxt = svgElement.selectAll('.forwardDIverse_text.swtxt_' + switchcls);
            const commonswitchimages = svgElement.selectAll('.forwardDiverse_switch_image.sw_img_' + switchcls);
            commonswitchinflow.style('opacity', '1');
            commonswitchinflowtxt.style('opacity', '1');
            commonswitchimages.style('opacity', '1');
          }
          this.flowPathFlagForward[flowid] = true;
        }
    } else {
      const getCommonSwitchForFlow = this.fetchFlowCommonSwitch(flowid, type);
      const flows = Object.keys(this.flowPathFlagReverse);
      const svgElement = d3.select('#svgReversePath');
      if (typeof(this.flowPathFlagReverse[flowid]) != 'undefined' && this.flowPathFlagReverse[flowid]) {
        const allSwitches =  svgElement.selectAll('.reverseDiverse_circle');
        const allSwitchImages =  svgElement.selectAll('.reverseDiverse_switch_image');
        const allText =  svgElement.selectAll('text.reverseDiverse_text');
        const allPortCircles = svgElement.selectAll('.reverseDiverse_port_circle');
        const allPortText = svgElement.selectAll('.reverseDiverse_port_text');
        const allLinks = svgElement.selectAll('.link_reverseDiverse');
        d3.selectAll('.reverseDiverse_link_' + flowid)
        .transition()
        .style('stroke-width', '2.5');
        allLinks.style('opacity', '1');
        allSwitches.style('opacity', '1');
        allSwitchImages.style('opacity', '1');
        allText.style('opacity', '1');
        allPortCircles.style('opacity', '1');
        allPortText.style('opacity', '1');
        this.flowPathFlagReverse[flowid] = false;
      } else {
        if (flows && flows.length) {
          flows.map((i, v) => {
            if (this.flowPathFlagReverse[i]) {
                d3.selectAll('.reverseDiverse_link_' + i)
                .transition()
                .style('stroke-width', '2.5');
                this.flowPathFlagReverse[i] = false;
            }

          });
        }

        const allSwitches =  svgElement.selectAll('.reverseDiverse_circle');
        const allSwitchImages =  svgElement.selectAll('.reverseDiverse_switch_image');
        const allText =  svgElement.selectAll('text.reverseDiverse_text');
        const allPortCircles = svgElement.selectAll('.reverseDiverse_port_circle');
        const allPortText = svgElement.selectAll('.reverseDiverse_port_text');
        const flowportText = svgElement.selectAll('.reverseDiverse_port_text_' + flowid);
        const flowportCircles =  svgElement.selectAll('.reverseDiverse_port_circle_' + flowid);
        const flowText =  svgElement.selectAll('text.reverseDiverse_textcircle_' + flowid);
        const flowSwitches = svgElement.selectAll('.reverseDiverse_circle_' + flowid);
        const flowSwitchImages = svgElement.selectAll('.reverseDiverse_switch_image_' + flowid);
        const allLinks = svgElement.selectAll('.link_reverseDiverse');
        const links = svgElement.selectAll('.reverseDiverse_link_' + flowid);
        allSwitches.style('opacity', 0);
        allText.style('opacity', '0');
        allSwitchImages.style('opacity', 0);
        allPortCircles.style('opacity', 0);
        allPortText.style('opacity', '0');
        allLinks.style('opacity', '0');
        flowportCircles.style('opacity', 1);
        flowportText.style('opacity', '1');
        flowText.style('opacity', 1);
        flowSwitches.style('opacity', 1);
        flowSwitchImages.style('opacity', 1);
        links.style('opacity', '1');
        links.style('stroke-width', '5');
        for (const d in getCommonSwitchForFlow) {
         const switchid = getCommonSwitchForFlow[d];
         const switchcls = switchid.split(':').join('_');
          const commonswitchinflow = svgElement.selectAll('.reverseDiverse_circle.sw_' + switchcls);
          const commonswitchinflowtxt = svgElement.selectAll('.reverseDiverse_text.swtxt_' + switchcls);
          const commonswitchimages = svgElement.selectAll('.reverseDiverse_switch_image.sw_img_' + switchcls);
          commonswitchinflow.style('opacity', '1');
          commonswitchinflowtxt.style('opacity', '1');
          commonswitchimages.style('opacity', '1');
        }
        this.flowPathFlagReverse[flowid] = true;
      }
    }
  }


  showCommonSwitch(type) {
    if (type == 'forward') {
      const commmonSwitch = this.flowpathService.getcommonSwitches('forward');
      if (this.commonSwitchFlagForward) {
        this.commonSwitchFlagForward = false;
      } else {
        this.commonSwitchFlagForward = true;
      }
      for (let i = 0; i < commmonSwitch.length; i++) {
        const switch_id = commmonSwitch[i].switch_id;
         const element = document.getElementById('forwardDiverse_circle_' + switch_id);
         const switchcls = switch_id.split(':').join('_');
         let classes = 'circle forwardDiverse_circle blue forwardDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
        if (!this.commonSwitchFlagForward) {
          classes = 'circle forwardDiverse_circle blue forwardDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
        } else {
          classes = 'circle forwardDiverse_circle common_switch blue hover forwardDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
        }
        element.setAttribute('class', classes);
      }

    } else {
      const commmonSwitch = this.flowpathService.getcommonSwitches('reverse');
      if (this.commonSwitchFlagReverse) {
        this.commonSwitchFlagReverse = false;
      } else {
        this.commonSwitchFlagReverse = true;
      }
       for (let i = 0; i < commmonSwitch.length; i++) {
        const switch_id = commmonSwitch[i].switch_id;
        const element = document.getElementById('reverseDiverse_circle_' + switch_id);
        const switchcls = switch_id.split(':').join('_');
        let classes = 'circle reverseDiverse_circle blue reverseDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
        if (!this.commonSwitchFlagReverse) {
          classes = 'circle reverseDiverse_circle blue reverseDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
         } else {
          classes = 'circle reverseDiverse_circle common_switch blue hover reverseDiverse_circle_' + commmonSwitch[i].flow + ' sw_' + switchcls;
        }
        element.setAttribute('class', classes);
      }
    }
  }

  showFlowList(type) {
    if (type == 'forward') {
        this.showFlowsForward = this.showFlowsForward ? false : true;
    } else {
        this.showFlowsReverse = this.showFlowsReverse ? false : true;
    }
  }

 loadDiverseGroup() {
    const self = this;
    let protectedPath = null;
    const currentFlow = {type: 'current_Flow', flowid: this.flowPathData.flowid, flowpath_forward: this.flowPathData['flowpath_forward'], flowpath_reverse: this.flowPathData['flowpath_reverse']};
    if (this.flowPathData && this.flowPathData['protected_path'] && this.flowPathData['protected_path']['flowpath_forward']) {
      protectedPath = {type: 'protected', flowid: 'protected_path_' + this.flowPathData.flowid, flowpath_forward: this.flowPathData['protected_path']['flowpath_forward'], flowpath_reverse: this.flowPathData['protected_path']['flowpath_reverse']};
    }

     const otherFLows = this.flowPathData && this.flowPathData['diverse_group'] && this.flowPathData['diverse_group']['other_flows'] ? this.flowPathData['diverse_group']['other_flows'] :  null;
     this.hasDiverseGroup = this.flowPathData && this.flowPathData['diverse_group'] && this.flowPathData['diverse_group']['other_flows'];
    if (otherFLows && otherFLows.length) {

      if (currentFlow) {
        otherFLows.push(currentFlow);
      }
      if (protectedPath) {
        otherFLows.push(protectedPath);
      }

     for (const flow in otherFLows) {
         const coloCode = this.commonService.getCommonColorCode(flow, self.colourCodes);
          this.colourCodes.push(coloCode);
          if (otherFLows[flow] && otherFLows[flow]['flowpath_forward']) {
            const flowid = otherFLows[flow]['flowid'];
            const path_type = typeof(otherFLows[flow]['type']) != 'undefined' ? otherFLows[flow]['type'] : 'diverse';
            if (this.diversePath && this.diversePath[flowid]) {
              this.diversePath[flowid]['forward_path'] = otherFLows[flow]['flowpath_forward'];
              this.diversePath[flowid]['path_type'] = path_type;
            } else {
              this.diversePath[flowid] = {};
              this.diversePath[flowid]['path_type'] = path_type;
              this.diversePath[flowid]['forward_path'] = otherFLows[flow]['flowpath_forward'];
            }
          }
          if (otherFLows[flow] && otherFLows[flow]['flowpath_reverse']) {
            const flowid = otherFLows[flow]['flowid'];
            const path_type = typeof(otherFLows[flow]['type']) != 'undefined' ? otherFLows[flow]['type'] : 'diverse';
            if (this.diversePath && this.diversePath[flowid]) {
              this.diversePath[flowid]['reverse_path'] = otherFLows[flow]['flowpath_reverse'];
              this.diversePath[flowid]['path_type'] = path_type;
            } else {
              this.diversePath[flowid] = {};
              this.diversePath[flowid]['path_type'] = path_type;
              this.diversePath[flowid]['reverse_path'] = otherFLows[flow]['flowpath_reverse'];
            }

          }
        }
        // add flows to diverse group
        Object.keys(this.diversePath).map(function(i, v) {
          self.diverseGroup.push(i);
        });

      }

      this.pathFlows = Object.keys(this.diversePath);

      this.pathFlows = Object.keys(this.diversePath).filter(function(f, k) {
          return f != 'protected_path_' + self.flowId;
      });
  }

  viewDiverseGroup(type) {
   if (type == 'forward') {
      this.forwardLabelText = 'FORWARD DIVERSITY';
      this.showDiverseGroupForward = this.showDiverseGroupForward ? false : true;
      this.forwardDiverse = (this.showDiverseGroupForward) ? 1 : 0.5;
      this.forwardPathGraph = false;
      setTimeout(() => {
        this.plotForwardDiverse();
      });

    } else {
      this.reverseLabelText = 'REVERSE DIVERSITY';
      this.showDiverseGroupReverse = this.showDiverseGroupReverse ? false : true;
      this.reverseDiverse = (this.showDiverseGroupReverse) ? 1 : 0.5;
      this.reversePathGraph = false;
      setTimeout(() => {
        this.plotReverseDiverse();
      });

    }
  }

  toggleDiversePath(type) {
    this.showForwardProtectedPath = false;
    this.showReverseProtectedPath = false;
    switch (type) {
      case 'forward':
          this.showFlowsForward = false;
          this.forwardGraphSvg = false;
          this.isDiverseForward = true;
          this.viewDiverseGroup(type);
      break;
      case 'reverse':
          this.toggleFilterReverse = 'reverseDiverse';
          this.showFlowsReverse = false;
          this.reverseGraphSvg =  false;
          this.isDiverseReverse = true;
          this.viewDiverseGroup(type);
        break;
    }
  }

  viewPathGraph(type) {

    if (type == 'forward') {
      this.showForwardProtectedPath = false;
      this.isDiverseForward = false;
      this.forwardDiverse = 0.5;
      this.forwardLabelText = 'FORWARD PATH GRAPH';
      this.forwardPathGraph = this.forwardPathGraph ? false : true;
      this.showDiverseGroupForward = this.isDiverseForward;
      this.forwardGraphSvg = this.forwardGraphSvg ? false : true;
      if (!this.forwardPathGraph && this.isDiverseForward) {
        this.forwardLabelText = 'FORWARD DIVERSITY';
      }

    } else {
      this.isDiverseReverse = false;
      this.showReverseProtectedPath = false;
      this.reverseDiverse = 0.5;
      this.reverseLabelText = 'REVERSE PATH GRAPH';
      this.reversePathGraph = this.reversePathGraph ? false : true;
      this.showReversePath =  this.reversePathGraph ? false : true;
      this.showDiverseGroupReverse = this.isDiverseReverse;
      this.reverseGraphSvg = this.reverseGraphSvg ? false : true;
      if (!this.reversePathGraph && this.isDiverseReverse) {
        this.reverseLabelText = 'REVERSE DIVERSITY';
      }
    }
  }

  ngOnDestroy() {
    this.reversePathGraph = false;
    this.forwardPathGraph = false;
    this.showDiverseGroupForward = false;
    this.showDiverseGroupReverse = false;
  }
}
