/*<![CDATA[*/
/** Below the javascript/ajax/jquery code to generate and display the stats api results.
* By default api will show stats of the previous day
* user can generate stats via filter elements on the html page */


/**
* Execute  getGraphData function when onchange event is fired 
* on the filter input values of datetimepicker, downsampling and menulist.
*/

var linkData = localStorage.getItem("linkData");	
var obj = JSON.parse(linkData)
var sourceSwitch = obj.source_switch;
var targetSwitch = obj.target_switch;
var sourcePort = obj.src_port;
var targetPort = obj.dst_port;
var source = sourceSwitch.replace(/:/g, "")
var target = targetSwitch.replace(/:/g, "")	

var graphInterval;

$(function() {
		
		$("#datetimepicker7,#datetimepicker8,#downsampling,#menulist,#autoreload").on("change",function() {
			getGraphData();
		});
	});
/**
* Execute this function when page is loaded
* or when user is directed to this page.
*/
$(document).ready(function() {
	
	$.datetimepicker.setLocale('en');
	var date = new Date()
	var yesterday = new Date(date.getTime());
	yesterday.setDate(date.getDate() - 1);
	var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
    var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
	var convertedStartDate = moment(YesterDayDate).format("YYYY-MM-DD-HH:mm:ss");
	var convertedEndDate = moment(EndDate).format("YYYY-MM-DD-HH:mm:ss");
	var downsampling = "1s";
	$("#downsampling").val(downsampling)
	$("#datetimepicker7").val(YesterDayDate);
	$("#datetimepicker8").val(EndDate);
	$('#datetimepicker7').datetimepicker({
		  format:'Y/m/d h:i:s',
	});
	$('#datetimepicker8').datetimepicker({
		  format:'Y/m/d h:i:s',
	});
	$('#datetimepicker_dark').datetimepicker({theme:'dark'})
		
	var selMetric="bits";

	
	loadGraph.loadGraphData("/stats/isl/"+source+"/"+sourcePort+"/"+target+"/"+targetPort+"/"+convertedStartDate+"/"+convertedEndDate+"/1s/"+selMetric,"GET",selMetric).then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData(response,selMetric); 
	})
})


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

		common.infoMessage('From Date should not be greater than currentDate.','error');		
		valid=false;
		return;
	} else if(endDate.getTime() < startDate.getTime()){
		common.infoMessage('To Date should not be less than From Date.','error');		
		valid=false;
		return;
	}
	
	var autoreload = $("#autoreload").val();
	var numbers = /^[-+]?[0-9]+$/;  
	var checkNo = $("#autoreload").val().match(numbers);
	var checkbox =  $("#check").prop("checked");
		
	if(valid) {
		
		loadGraph.loadGraphData("/stats/isl/"+source+"/"+sourcePort+"/"+target+"/"+targetPort+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET",selMetric).then(function(response) {
		
			$("#wait1").css("display", "none");
			$('body').css('pointer-events', 'all');
			showStatsGraph.showStatsData(response,selMetric); 
	})
			
			try {
				clearInterval(graphInterval);
			} catch(err) {

			}
			
			if(autoreload){
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
	var selMetric=$("select.selectbox_menulist").val();	
	
	loadGraph.loadGraphData("/stats/isl/"+source+"/"+sourcePort+"/"+target+"/"+targetPort+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET",selMetric).then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData(response,selMetric); 
	})
}

/* ]]> */