/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the isl and the flow details*/

/**
 * Retieve object of the isl/links details json object
 * and parse it
 */
$(document).ready(function() {
	$("#isl-menu-id").addClass("active");
	var linkData = localStorage.getItem("linkData");
	if(!linkData){
		window.location = APP_CONTEXT+ "/topology";
	}
	var obj = JSON.parse(linkData);
	$('body').css('pointer-events','all'); 
	var source_switch = obj.source_switch;
	var dest_switch = obj.target_switch;
	var src_port =obj.src_port;
	var dst_port =obj.dst_port;
	var url ="/switch/link/props?src_switch="+source_switch+"&src_port="+src_port+"&dst_switch="+dest_switch+"&dst_port="+dst_port;
	var method ="GET";
	common.getData(url,method).then(function(response) {
		$("#loading").css("display", "none");
		var isvalidCost = false;
		if(response){
			var isvalidCost = checkvalidatedCostData(obj,response);
		}
		if(isvalidCost){ 
			showLinkDetails(obj,response,false);
		}else{ 
			common.infoMessage("Error in getting ISL cost!",'failure');
			showLinkDetails(obj,response,true);
		}
		
	},function(error){
		$("#loading").css("display", "none");
		if(error && error.status != 200){
			var errMsg = (error && error.responseJSON && error.responseJSON["error-message"]) ? error.responseJSON["error-auxiliary-message"] : "Error in getting ISL cost";
				common.infoMessage(errMsg,'failure');
				showLinkDetails(obj,null,true);
			
		}else{
			showLinkDetails(obj,null,false);
		}
		
		
	}).fail(function(error){
		$("#loading").css("display", "none");
		var errMsg = (error && error.responseJSON && error.responseJSON["error-message"]) ? error.responseJSON["error-auxiliary-message"] : "Error in getting ISL cost";
		common.infoMessage(errMsg,'failure');
		showLinkDetails(obj,null,true);
	})
	
	$('#edit_isl_cost').click(function(e){
		e.preventDefault();
		$('#text_cost_details').hide();
		$('#isl_cost_update').show();
		$('#isl_cost_lbl').addClass('mt-2');
	})
	
	$('#cancel_isl_cost_update').click(function(e){
		e.preventDefault();
		$('#text_cost_details').show();
		$('#isl_cost_update').hide();
		$('#isl_cost_lbl').removeClass('mt-2');
	})
	
	$('#update_isl_cost').click(function(e){
		e.preventDefault();
		var newCost = $('#isl_cost').val();
		var costObj = JSON.parse($("#isl_cost_obj").val());
		var newCostObj = costObj;
		newCostObj.props.cost = newCost;
		updateIslCost(newCostObj,costObj);
		
	})
	
})
function checkvalidatedCostData(link,costData){
	return (link && costData && link.source_switch == costData.src_switch && link.target_switch == costData.dst_switch && link.src_port == costData.src_port && link.dst_port == costData.dst_port)
}
function updateIslCost(forwardislCostData,oldCost){
	var linkData = localStorage.getItem("linkData");
	var obj = JSON.parse(linkData);
	var data =[];
	var reverseIslCostData ={"src_switch":forwardislCostData.dst_switch,
			"src_port":forwardislCostData.dst_port,
			"dst_switch":forwardislCostData.src_switch,
			"dst_port":forwardislCostData.src_port,
			"props":{"cost":forwardislCostData.props.cost}
	}
	data.push(forwardislCostData);
	data.push(reverseIslCostData);
	$('#cancel_isl_cost').trigger('click');
	$('#cancel_isl_cost_update').trigger('click');
	common.updateData("/switch/link/props","PUT",data).then(function(res){
		if(typeof(res.successes)!=='undefined' && res.successes > 0){
			common.infoMessage("ISL cost updated successfully!",'success');
			showLinkDetails(obj,forwardislCostData);
		}else if(typeof(res.failures)!=='undefined' && res.failures > 0){
			common.infoMessage("Error in updating ISL cost!",'Failure');
			showLinkDetails(obj,oldCost);
		}
		
	}).fail(function(error){
		common.infoMessage("Error in updating ISL cost!",'Failure');
		showLinkDetails(obj,oldCost);
	})

}


/**call the metrics api to show list of the metric values in the drop down*/

/**
 generate and display the isl/low details in the html
 */
function showLinkDetails(linkData,costData,error) {
	var speed = linkData.speed;
	var available_bandwidth = linkData.available_bandwidth;
	if(typeof available_bandwidth !== 'string') {
		available_bandwidth = available_bandwidth/1000+" Mbps";
	}
	if(typeof speed !== 'string') {
		speed = speed/1000+" Mbps";
	}
	$(".graph_div").show();
	$('.isl_details_div').show();
	$('#DownsampleID').show();
	var size = 0, key;
	 var source_switch_toggle = $('#on-off-switch-src').val();
	 var target_switch_toggle = $('#on-off-switch-dest').val();
	 if(source_switch_toggle == 1) {
		 $(".link_div_source_switch").html(common.toggleSwitchID(linkData.source_switch));
	 }else{
		$(".link_div_source_switch").html(linkData.source_switch);
	 }
	$(".link_div_source_switch_name").html(linkData.source_switch_name);
	$(".link_div_source_port").html(linkData.src_port);
	if(target_switch_toggle == 1) {
		$(".link_div_destination_switch").html(common.toggleSwitchID(linkData.target_switch));
	}else {
		$(".link_div_destination_switch").html(linkData.target_switch);
	}
	$(".link_div_destination_switch_name").html(linkData.target_switch_name);
	$(".link_div_destination_port").html(linkData.dst_port);
	$(".isl_div_speed").html(speed);
	$(".isl_div_latency").html(linkData.latency);
	$(".isl_div_avaliable_bandwidth").html(available_bandwidth);
	$(".isl_div_state").html(linkData.state);
	 if(typeof(error) != 'undefined' && error){
			$(".isl_div_cost").addClass('from-error-message').html("Not able to get the cost").css('float','left');
			$('#edit_isl_cost').addClass('hidePermission').removeClass('showPermission');
	}else if(costData && costData.props && costData.props.cost){
		$(".isl_div_cost").html(costData.props.cost)
		$('#isl_cost').val(costData.props.cost);
		$('#isl_cost_obj').val(JSON.stringify(costData));
	}else{
		$(".isl_div_cost").html("-")
		var noCostData ={"src_switch":linkData.source_switch,
			"src_port":linkData.src_port,
			"dst_switch":linkData.target_switch,
			"dst_port":linkData.dst_port,
			"props":{"cost":"-"}
	}
		$('#isl_cost_obj').val(JSON.stringify(noCostData));
	}
	
}

/* ]]> */