/*<![CDATA[*/
/** Below the javascript/ajax/jquery code to generate and display the stats api results.
* By default api will show stats of the previous day
* user can generate stats via filter elements on the html page */


/**
* Execute  getGraphData function when onchange event is fired 
* on the filter input values of datetimepicker, downsampling and menulist.
*/

var graphInterval;

$(function() {
		
		$("#datetimepicker7,#datetimepicker8,#downsampling,#menulist,#autoreload").on("change",function() {
			getGraphData();
		});
	});
	

/**
* Execute this function to  show stats data whenever user filters data in the
* html page.
*/
function getGraphData() {
	
	
	var regex = new RegExp("^\\d+(s|h|m){1}$");
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7").val());
	var endDate =  new Date($("#datetimepicker8").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	var downsampling = $("#downsampling").val();
	var downsamplingValidated = regex.test(downsampling);
	var selMetric=$("select.selectbox_menulist").val();
		
	var valid=true;
	
	if(downsamplingValidated == false) {	
		
		common.infoMessage('Please enter correct downsampling','error');		
		valid=false;
		return
	}
	
	if(startDate.getTime() > currentDate.getTime()) {
		
		common.infoMessage('startDate should not be greater than currentDate','error');	
		valid=false;
		return;
	}
	
	else if(endDate.getTime() < startDate.getTime()){
		
		common.infoMessage('endDate should not be less than startDate','error');
		valid=false;
		return;
	}
	
		
	var autoreload = $("#autoreload").val();
	var numbers = /^[-+]?[0-9]+$/;  
	var checkNo = $("#autoreload").val().match(numbers);
	var checkbox =  $("#check").prop("checked");
		
	
	//if filter values are valid then call stats api
	if(valid){
		
		
		var linkData = localStorage.getItem("linkData");	
		var obj = JSON.parse(linkData)
		

		var source = obj.source_switch.replace(/:/g, "")
		var target = obj.target_switch.replace(/:/g, "")
	
		$.ajax({
			dataType: "jsonp",					
			url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/"+selMetric+"?"+"src_switch="+source+"&src_port="+obj.src_port+"&dst_switch="+target+"&dst_port="+obj.dst_port+"&averageOf="+downsampling,
			
			type : 'GET',
			success : function(response) {	
					
				$("#wait1").css("display", "none");	
				commonGraphMethods.showStatsData(response);
				
			},
			dataType : "json"
		});
		
		
			try {
				clearInterval(graphInterval);
			} catch(err) {

			}
			
			if(autoReload.autoreloadGraph){
				graphInterval = setInterval(function(){
					callIntervalData() 
				}, 1000*autoreload);
			}
	}
	
}

function callIntervalData(){
	
	var currentDate = new Date();
	
	var startDate = new Date($("#datetimepicker7").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
	
	var endDate = new Date()
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	
	var downsampling =$("#downsampling").val()
	
	var linkData = localStorage.getItem("linkData");	
	var obj = JSON.parse(linkData)
	
	
	var selMetric=$("select.selectbox_menulist").val();

	var source = obj.source_switch.replace(/:/g, "")
	var target = obj.target_switch.replace(/:/g, "")
		

		$.ajax({
			dataType: "jsonp",

			url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/"+selMetric+"?"+"src_switch="+source+"&src_port="+obj.src_port+"&dst_switch="+target+"&dst_port="+obj.dst_port+"&averageOf="+downsampling,

			type : 'GET',
			success : function(response) {	
				commonGraphMethods.showStatsData(response);
			},
			dataType : "json"
		});	
}
/* ]]> */