/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the isl and the flow details*/

/**
 * Retieve object of the isl/links details json object
 * and parse it
 */
$(document).ready(function() {

	var linkData = localStorage.getItem("linkData");
	$("#topology-menu-id").addClass("active");
	if(!linkData){
		window.location = APP_CONTEXT+ "/topology";
	}
	var obj = JSON.parse(linkData);
	$('body').css('pointer-events','all'); 
	showLinkDetails(obj);
})

/**call the metrics api to show list of the metric values in the drop down*/

/**
 generate and display the isl/low details in the html
 */
function showLinkDetails(linkData) {
	
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
		$(".isl_div_speed").html(linkData.speed/1000+" Mbps");
		$(".isl_div_latency").html(linkData.latency);
		$(".isl_div_avaliable_bandwidth").html(linkData.available_bandwidth/1000+" Mbps");
	
}

/* ]]> */