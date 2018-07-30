/*<![CDATA[*/
/** Below the javascript/ajax/jquery code to generate and display the stats api results.
* By default api will show stats of the previous day
* user can generate stats via filter elements on the html page */


/**
 * Execute getGraphData function when onchange event is fired on the filter
 * input values of datetimepicker, downsampling and menulist.
 */


var portData = localStorage.getItem("portDetails");
var portObj = JSON.parse(portData)
var portNum = portObj.port_number;
var switchname = window.location.href.split("#")[1];
var sourceswitch = common.toggleSwitchID(switchname);


var graphInterval;

$(function() {
	$('#timezone').on('change',function(){
		var timezone = $('#timezone option:selected').val();
		var dat2 = new Date();
		var dat1 = new Date(dat2.getTime());
		dat1.setDate(dat2.getDate() - 1);		
		if(timezone == 'UTC'){
			var startDate = moment(dat1).utc().format("YYYY/MM/DD HH:mm:ss");
			var endDate = moment(dat2).utc().format("YYYY/MM/DD HH:mm:ss");
			$('#datetimepicker7').val(startDate);
			$('#datetimepicker8').val(endDate)
		}else{
			var startDate = moment(dat1).format("YYYY/MM/DD HH:mm:ss");
			var endDate = moment(dat2).format("YYYY/MM/DD HH:mm:ss");
			$('#datetimepicker7').val(startDate);
			$('#datetimepicker8').val(endDate)
		}
	})
		$("#datetimepicker7,#datetimepicker8,#downsampling,#menulist,#autoreload,#timezone").on("change",function(event) {
				getGraphData();
		});
});
	
/**
 * Execute this function when page is loaded or when user is directed to this
 * page.
 */
$(document).ready(function() {

	$.datetimepicker.setLocale('en');
	$('#timezone').val("LOCAL");
	var date = new Date()
	var yesterday = new Date(date.getTime());
	yesterday.setDate(date.getDate() - 1);
	var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
    var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
    if($('#timezone option:selected').val() == 'UTC'){
    	var convertedStartDate = moment(yesterday).format("YYYY-MM-DD-HH:mm:ss");
    	var convertedEndDate = moment(date).format("YYYY-MM-DD-HH:mm:ss");
    }else{
    	var convertedStartDate = moment(yesterday).utc().format("YYYY-MM-DD-HH:mm:ss");
    	var convertedEndDate = moment(date).utc().format("YYYY-MM-DD-HH:mm:ss");
    }
	var selMetric="bits";
	var downsampling = "30s";

	$("#downsampling").val(downsampling)
	$("#datetimepicker7").val(YesterDayDate);
	$("#datetimepicker8").val(EndDate);
	$('#datetimepicker7').datetimepicker({
		  format:'Y/m/d H:i:s',
	});
	$('#datetimepicker8').datetimepicker({
		  format:'Y/m/d H:i:s',
	});

	$('#datetimepicker_dark').datetimepicker({theme:'dark'})
	
		loadGraph.loadGraphData("/stats/switchid/"+sourceswitch+"/port/"+portNum+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET",selMetric).then(function(response) {
			$("#wait1").css("display", "none");
			$('body').css('pointer-events', 'all');
			showStatsGraph.showStatsData(response,selMetric,null,null,YesterDayDate,EndDate); 
		})
})

/**
 * Execute this function to show stats data whenever user filters data in the
 * html page.
 */
function getGraphData() {
		
	var regex = new RegExp("^\\d+(s|h|m){1}$");
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7").val());
	var endDate =  new Date($("#datetimepicker8").val());
	var timezone = $('#timezone option:selected').val();
	if(timezone == 'UTC'){
		var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");	
		var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	}else{
		var convertedStartDate = moment(startDate).utc().format("YYYY-MM-DD-HH:mm:ss");	
		var convertedEndDate = moment(endDate).utc().format("YYYY-MM-DD-HH:mm:ss");
	}
	var downsampling = $("#downsampling option:selected").val();
	var downsamplingValidated = regex.test(downsampling);
	var selMetric=$("select.selectbox_menulist").val();
	var valid=true;
	
	if(downsamplingValidated == false) {	
		$("#DownsampleID").addClass("has-error")	
		$(".downsample-error-message").html("Please enter valid input.");			
		valid=false;
		return
	}
	if(startDate.getTime() > currentDate.getTime()) {
		$("#fromId").addClass("has-error")	
		$(".from-error-message").html("From date should not be greater than currentDate.");			
		valid=false;
		return;
	} else if(endDate.getTime() < startDate.getTime()){
		$("#toId").addClass("has-error")	
		$(".to-error-message").html("To date should not be less than from date.");		
		valid=false;
		return;
	}
		
	var autoreload = $("#autoreload").val();
	var numbers = /^[-+]?[0-9]+$/;  
	var checkNo = $("#autoreload").val().match(numbers);
	var checkbox =  $("#check").prop("checked");

	var test = true;	
    autoVal.reloadValidation(function(valid){
	  
	  if(!valid) {
		  test = false;		  
		  return false;
	  }
  });
  
if(test) {
	$('#wait1').show();
	$("#fromId").removeClass("has-error")
    $(".from-error-message").html("");
	
	$("#toId").removeClass("has-error")
    $(".to-error-message").html("");
	
	$("#autoreloadId").removeClass("has-error")
    $(".error-message").html("");
	
  	$("#DownsampleID").removeClass("has-error")
	$(".downsample-error-message").html("");
	
  loadGraph.loadGraphData("/stats/switchid/"+sourceswitch+"/port/"+portNum+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET",selMetric).then(function(response) {
		
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData(response,selMetric,null,null,startDate,endDate); 
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
	var timezone = $('#timezone option:selected').val();
	var endDate = new Date() ;
	if(timezone == 'UTC'){
		var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");	
		var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	}else{
		var convertedStartDate = moment(startDate).utc().format("YYYY-MM-DD-HH:mm:ss");	
		var convertedEndDate = moment(endDate).utc().format("YYYY-MM-DD-HH:mm:ss");
	}
		
	var downsampling =$("#downsampling option:selected").val();
	var selMetric=$("select.selectbox_menulist").val();
	
	loadGraph.loadGraphData("/stats/switchid/"+sourceswitch+"/port/"+portNum+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric,"GET",selMetric).then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData(response,selMetric,null,null,startDate,endDate); 
	})
}

/* ]]> */