/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the isl and the flow details*/

/**
 * Retieve object of the isl/links details json object
 * and parse it
 */
$(document).ready(function() {

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
		showLinkDetails(obj,response);
	},
	function(error){
		showLinkDetails(obj,null);
	})
	
})


/**call the metrics api to show list of the metric values in the drop down*/

/**
 generate and display the isl/low details in the html
 */
function showLinkDetails(linkData,costData) {
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
	$(".link_div_source_switch").html(linkData.source_switch);
	$(".link_div_source_switch_name").html(linkData.source_switch_name);
	$(".link_div_source_port").html(linkData.src_port);
	$(".link_div_destination_switch").html(linkData.target_switch);
	$(".link_div_destination_switch_name").html(linkData.target_switch_name);
	$(".link_div_destination_port").html(linkData.dst_port);
	$(".isl_div_speed").html(speed);
	$(".isl_div_latency").html(linkData.latency);
	$(".isl_div_avaliable_bandwidth").html(available_bandwidth);
	if(costData && costData.props && costData.props.cost){
		$(".isl_div_cost").html("$"+costData.props.cost)
	}else{
		$(".isl_div_cost").html("-")
	}
	
}

/* ]]> */