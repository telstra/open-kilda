/*<![CDATA[*/

$(document).ready(function() {
	var flowid = window.location.href.split("#")[1]
	var tmp_anchor = '<a href="flowdetails#' + flowid + '">' + flowid + '</a>';
	$("#flow-id-name").parent().append(flowid);
	$("#loading").css("display", "block");	
	common.getData("/flows/"+flowid,"GET").then(function(response) {
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all'); 
		flowObj.setFlow(response);
		var isEdit = localStorage.getItem("flowEdit_"+flowid);
		if(isEdit){
			$('#flow_detail_div').hide();
			localStorage.removeItem("flowEdit_"+flowid);
			flowObj.editFlow();
		}else{
			$('#flow_detail_div').show();
			showFlowData(response);
		}
		
	},function(error){
		common.infoMessage('Flow does not exists, please try with new flow id','info');
		setTimeout(function(){ 
			window.location = APP_CONTEXT+ "/flows"; 
		}, 6000);
		response=[];
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all');
	});
	
	getMetricDetails.getFlowMetricData();
	$('body').css('pointer-events','all');
	
})

function callValidateFlow(flow_id){
	$('#validate_json_loader').show();
	$('#validate_json').html("");
	$('#validate_flow_response').hide();
	$('#resync_flow_response').hide();
		common.getData("/flows/" + flow_id+"/validate","GET").then(function(response) { // calling re-route api
				var responseData = JSON.stringify(response,null,2);
				$('#validate_json').html(responseData)
				$('#validate_flow_response').show();
				$('#validate_json_loader').hide();
		},function(error){
			$('#validate_json').html([])
			$('#validate_flow_response').show();
			$('#validate_json_loader').hide();
		})
}

function callResyncFlow(flow_id){
	$('#resync_json_loader').show();
	$('#resync_json').html("");
	$('#resync_flow_response').hide();
	$('#validate_flow_response').hide();
		common.getData("/flows/" + flow_id+"/sync","PATCH").then(function(response) { // calling re-route api
				var responseData = JSON.stringify(response,null,2);
				$('#resync_json').html(responseData);
				$('#resync_flow_response').show();
				$('#resync_json_loader').hide();
		},function(error){
			$('#validate_json').html([])
			$('#validate_flow_response').show();
			$('#validate_json_loader').hide();
		})
}
function showFlowData(obj) {
	$(".flow_div_flow_id").html(obj.flowid);
	$(".flow_div_source_switch").html(obj.source["switch-id"]);
	$(".flow_div_source_port").html(obj.source["port-id"]);
	$(".flow_div_source_switch_name").html(obj.source["switch-name"]);
	$(".flow_div_source_vlan").html(obj.source["vlan-id"]);	
	$(".flow_div_destination_switch").html(obj.destination["switch-id"]);
	$(".flow_div_destination_port").html(obj.destination["port-id"]);
	$(".flow_div_destination_switch_name").html(obj.destination["switch-name"]);
	$(".flow_div_destination_vlan").html(obj.destination["vlan-id"]);
	$(".flow_div_maximum_bandwidth").html(obj["maximum-bandwidth"]);
	$(".flow_div_Status").html(obj.status);
	if (!obj.description == "") {
		$(".flow_div_desc").html(obj.description);
	} else {
		$(".flow_div_desc").html("-");
	}
	callFlowPath(obj.flowid);
	$('#reroute_flow').click(function(e){
		e.preventDefault();
		callReRoute(obj.flowid);
		
	})
	$('#validate_flow_btn').click(function(e){
		e.preventDefault();
		callValidateFlow(obj.flowid);
	});
	$('#resync_flow_btn').click(function(e){
		e.preventDefault();
		callResyncFlow(obj.flowid);
	})
	$('#clear_validate').click(function(e){
		$("#validate_json").html("");
		$("#validate_flow_response").hide();
	})
	$('#clear_resync').click(function(e){
		$("#resync_json").html("");
		$("#resync_flow_response").hide();
	})

}




function callReRoute(flow_id){
	$("#path_reroute_loader").show();
	$('#ForwardRow').find('div').remove();
	$('#ReversePath').find('div').remove();
	common.getData("/flows/" + flow_id+"/reroute","GET").then(function(res) { // calling re-route api
		if(res && typeof(res.rerouted)!=='undefined' && res.rerouted){
			common.infoMessage('Flow : '+flow_id+" successfully re-routed!","success");
		} else {
			common.infoMessage('Flow : '+flow_id+" already on best route!","info");
		}
		// adding wait time of 10 sec to call path and status because re-route take some time to update in db
		setTimeout(function(){ 
			common.getData("/flows/"+flow_id+"/status","GET").then(function(response){
				if(response && typeof(response.status)!=='undefined'){
					$(".flow_div_Status").html(response.status);
				}
			})
			common.getData("/flows/path/" + flow_id,"GET").then(function(response) { //calling flow path api again
				showFlowPathData(response,true);
			})
		}, 10000);
		
	})
	
}
function callFlowPath(flow_id) {
	
	common.getData("/flows/path/" + flow_id,"GET").then(function(response) {
		showFlowPathData(response);
	})
}
function openswitchDetail(switch_id){
	common.getData("/switch/list","GET").then(function(response){
		var switchData= response.filter(function(f){
				return f.switch_id == switch_id;
		})
	})	
}
function showFlowPathData(response ,isloader) {
	flowObj.setFlowPathObj(response);
	var tmp_length = 0;
	for(var t in response) {
	    ++tmp_length;
	}
	if(!tmp_length > 0){
		
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
		$('#ForwardRow').append(path_html);
		$('#ReversePath').append(path_html);
		return false;
	}
	
	var check_flowpath_exists = 0;
	for(var t in response) {
	    if (Object.keys(response.flowpath_forward).length > 0 || Object.keys(response.flowpath_reverse).length > 0) {
	    	++check_flowpath_exists;
	    } else {
	    	var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
				$('#ForwardRow').append(path_html);
     			$('#ReversePath').append(path_html);
		    	return false;
    	}  
	}
			
	if(response.flowpath_forward.length==0) {
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
			$('#ForwardRow').append(path_html);	
	} else {
		var obj = response.flowpath_forward;	
		for (var i = 0; i < obj.length; i++) {
			
			if(obj[i].input_port==null) {
				obj[i].input_port="NA";
			}
			
			if(obj[i].output_port==null) {
				obj[i].output_port="NA";
			}
			
			
			if(obj.length <= 5) {
				var switchId = "'"+obj[i].switch_id+"'";
				var path_html = '<div class="path"><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].input_port + '</div><div class="line"></div><div class="text cursor-pointer" id ="switch-name" onclick="openswitchDetail('+switchId+')">' 
					+ obj[i].switch_name + '</div><div class="line"></div><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].output_port + '</div><div class="line"></div></div>';

				$('#ForwardRow').append(path_html);		
			} else {
				var switchId = "'"+obj[i].switch_id+"'";
				var path_html = '';
				path_html = '<div class="path"><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].input_port + '</div><div class="line"></div><div class="text cursor-pointer" id ="switch-name" onclick="openswitchDetail('+switchId+')">' 
					+ obj[i].switch_name + '</div><div class="line"></div><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].output_port + '</div><div class="line"></div><div class="vertical-line"></div><div class="horizontal-line"></div><div class="vertical-line-2"></div></div>';
				
				$('#ForwardRow').append(path_html);
			}
		}
	}	
	$(".path:last-child .line:nth-child(6)").hide();
	
		
	if(response.flowpath_reverse.length==0) {
		var path_html = '<div class="alert alert-danger text-center"><strong></strong> No Path Data Found. </div>'
			$('#ReversePath').append(path_html);	
	}else{
		var obj = response.flowpath_reverse;
		for (var i = 0; i < obj.length; i++) {

			if(obj[i].input_port==null) {
				obj[i].input_port="NA";
			}
			
			if(obj[i].output_port==null) {
				obj[i].output_port="NA";
			}
			
			
			if(obj.length <= 5) {
				var path_html = '<div class="path"><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].input_port + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_name + '</div><div class="line"></div><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].output_port + '</div><div class="line"></div></div>';

				$('#ReversePath').append(path_html);		
			} else {
				var path_html = '';
				path_html = path_html + '<div class="path"><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].input_port + '</div><div class="line"></div><div class="text" id ="switch-name">' 
					+ obj[i].switch_name + '</div><div class="line"></div><div class="number" data-balloon="' 
					+ obj[i].switch_name + '" data-balloon-pos="up" id="port-number-a">'
					+ obj[i].output_port + '</div><div class="line"></div><div class="vertical-line"></div><div class="horizontal-line"></div><div class="vertical-line-2"></div></div>';
				
				$('#ReversePath').append(path_html);
			}	
		}
	}
	
	$(".path:last-child .line:nth-child(6)").hide();	
	if(isloader){
		$("#path_reroute_loader").hide();
	}
}
/* ]]> */