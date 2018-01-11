/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/

/** show switch details when page is loaded or
 *  when user is redirected to this page*/
$(document).ready(
		function() {

			var switchname = window.location.href.split("#")[1]

			var tmp_anchor = '<a href="switchport#' + switchname + '">'+ switchname + '</a>';
			
			$("#kilda-switch-name").parent().append(tmp_anchor)

			var portData = localStorage.getItem("portDetails");

			var obj = JSON.parse(portData)

			$("#kilda-port-name").parent().append(obj.port_name)

			showSwitchData(obj); 
			getMetric();

		})

/** function to retrieve and show switch details from 
 * the switch response json object and display on the html page*/
function showSwitchData(obj) {
	$(".graph_div").show();
	$(".port_details_div_status").html(obj.status);
	$(".port_details_div_name").html(obj.port_name);
	$(".switchdetails_div_number").html(obj.port_number);
	$(".switchdetails_div_interface").html(obj.interface);

}

function getMetric() {
	
	
	var linkData = localStorage.getItem("linkData");	
	var obj = JSON.parse(linkData)
	
	$.ajax({
		url : APP_CONTEXT + "/stats/metrics",
		type : 'GET',
		success : function(response) {
			var metricList=response;
			var optionHTML="";
			for(var i=0;i<=metricList.length-1;i++){
				optionHTML+="<option value="+metricList[i]+">"+metricList[i]+"</option>";
							
			}
			
				$("select.selectbox_menulist").html("").html(optionHTML);
				$('#menulist').val('pen.isl.latency');
		
			
		},
		dataType : "json"
	});
}

/* ]]> */