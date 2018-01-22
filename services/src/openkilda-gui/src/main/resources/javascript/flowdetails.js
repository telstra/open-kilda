/*<![CDATA[*/

$(document).ready(function() {

	var flowid = window.location.href.split("#")[1]
	var tmp_anchor = '<a href="flowdetails#' + flowid + '">' + flowid + '</a>';
	$("#flow-id-name").parent().append(flowid)
	var flowData = localStorage.getItem("flowDetails");
	var obj = JSON.parse(flowData)
	showFlowData(obj);
	getFlowMetric();
	$("#wait1").css("display", "none");
	$('body').css('pointer-events','all');
})

function getFlowMetric() {

	common.getData("/stats/metrics","GET").then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all');
		 showFlowMetrics(response);
	})
}

function showFlowData(obj) {

	$(".flow_div_flow_id").html(obj.flow_id);
	$(".flow_div_source_switch").html(obj.source_switch);
	$(".flow_div_source_port").html(obj.src_port);
	$(".flow_div_source_vlan").html(obj.src_vlan);
	$(".flow_div_destination_switch").html(obj.target_switch);
	$(".flow_div_destination_port").html(obj.dst_port);
	$(".flow_div_destination_vlan").html(obj.dst_vlan);
	$(".flow_div_maximum_bandwidth").html(obj.maximum_bandwidth);
	$(".flow_div_Status").html(obj.status);

	if (!obj.description == "") {
		$(".flow_div_desc").html(obj.description);
	} else {
		$(".flow_div_desc").html("-");
	}
	callFlowForwardPath(obj.flow_id)
	callFlowReversePath(obj.flow_id)
}

function callFlowForwardPath(flow_id) {
	
	common.getData("/flows/path/" + flow_id,"GET").then(function(response) {
		$("#wait1").css("display", "none");
		showForwardFlowPathData(response);
	})

}

function callFlowReversePath(flow_id) {
	
	common.getData("/flows/path/" + flow_id,"GET").then(function(response) {
		$("#wait1").css("display", "none");
		showReverseFlowPathData(response);
	})

}

function showForwardFlowPathData(response) {
	var tmp_length = 0;
	for(var t in response) {
	    ++tmp_length;
	}
	if(!tmp_length > 0){
		
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
		$('#ForwardRow').append(path_html);
		return false;
	}
	
	var check_flowpath_exists = 0;
	for(var t in response) {
	    if (Object.keys(response.flowpath).length > 0) {
	    	++check_flowpath_exists;
	    } else {
	    	var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
				$('#ForwardRow').append(path_html);
		    	return false;
    	}  
	}
			
	if(response.flowpath.forwardPath.length==0) {
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
			$('#ForwardRow').append(path_html);	
	} else {
		var obj = response.flowpath.forwardPath;	
		for (var i = 0; i < obj.length; i++) {
			
			if(obj[i].in_port_no==null) {
				obj[i].in_port_no="NA";
			}
			
			if(obj[i].out_port_no==null) {
				obj[i].out_port_no="NA";
			}
			
			
			if(obj.length <= 5) {
				var path_html = '<div class="path"><div class="number" id="port-number-a">'
					+ obj[i].in_port_no + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_id + '</div><div class="line"></div><div class="number" id="port-number-a">'
					+ obj[i].out_port_no + '</div><div class="line"></div></div>';

				$('#ForwardRow').append(path_html);		
			} else {
				var path_html = '<div class="path"><div class="number" id="port-number-a">'
					+ obj[i].in_port_no + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_id + '</div><div class="line"></div><div class="number" id="port-number-a">'
					+ obj[i].out_port_no + '</div><div class="line"></div><div class="vertical-line"></div><div class="horizontal-line"></div><div class="vertical-line-2"></div></div>';
				
				$('#ForwardRow').append(path_html);
			}
		}
	}
	
	$(".path:last-child .line:nth-child(6)").hide();
}



function showReverseFlowPathData(response) {

	var tmp_length = 0;
	for(var t in response) {
	    ++tmp_length;
	}
	if(!tmp_length > 0){
		
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
		$('#ReversePath').append(path_html);
		return false;
	}
	
	var check_flowpath_exists = 0;
	for(var t in response) {
	    if (Object.keys(response.flowpath).length > 0) {
	    	++check_flowpath_exists;
	    } else {
	    	var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
				$('#ReversePath').append(path_html);
		    	return false;
    	}  
	}
	
	if(response.flowpath.reversePath.length==0){
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
			$('#ReversePath').append(path_html);	
	}else{
		var obj = response.flowpath.reversePath;
		for (var i = 0; i < obj.length; i++) {

			if(obj[i].in_port_no==null) {
				obj[i].in_port_no="NA";
			}
			
			if(obj[i].out_port_no==null) {
				obj[i].out_port_no="NA";
			}
			
			
			if(obj.length <= 5) {
				var path_html = '<div class="path"><div class="number" id="port-number-a">'
					+ obj[i].in_port_no + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_id + '</div><div class="line"></div><div class="number" id="port-number-a">'
					+ obj[i].out_port_no + '</div><div class="line"></div></div>';

				$('#ReversePath').append(path_html);		
			} else {
				var path_html = '<div class="path"><div class="number" id="port-number-a">'
					+ obj[i].in_port_no + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_id + '</div><div class="line"></div><div class="number" id="port-number-a">'
					+ obj[i].out_port_no + '</div><div class="line"></div><div class="vertical-line"></div><div class="horizontal-line"></div><div class="vertical-line-2"></div></div>';
				
				$('#ReversePath').append(path_html);
			}	
		}
	}
	
	$(".path:last-child .line:nth-child(6)").hide();
}

function showFlowMetrics(response){
	var metricArray = [];			
	for (var i = 0; i < response.length; i++) {				
		
		if(response[i].includes("pen.flow")) {
			var value = response[i].split(".")[2]
			metricArray.push(value);
		}
	}		
	var metricList = metricArray;
	var optionHTML = "";
	for (var i = 0; i <= metricList.length - 1; i++) {
		optionHTML += "<option value=" + 'pen.flow.' + metricList[i] + ">"+ metricList[i] + "</option>";
	}
	$("select.selectbox_menulist").html("").html(optionHTML);
	$('#menulist').val('pen.flow.bytes');
}


/* ]]> */