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
		
		$("#datetimepicker7,#datetimepicker8,#menulist,#autoreload").on("change",function() {
			getGraphData();
		});
	});
	


/**
* Execute this function when page is loaded
* or when user is directed to this page.
*/
$(document).ready(function() {

	
	var flowData = localStorage.getItem("flowDetails");
	var obj = JSON.parse(flowData)
	
	
	
	var source = obj.source_switch.replace(/:/g, "")
	var target = obj.target_switch.replace(/:/g, "")
	
	
	$.datetimepicker.setLocale('en');
	var date = new Date()
	
	var yesterday = new Date(date.getTime());
	yesterday.setDate(date.getDate() - 1);
	
	var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
	var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
	
	var convertedStartDate = moment(YesterDayDate).format("YYYY-MM-DD-HH:mm:ss");
	var convertedEndDate = moment(EndDate).format("YYYY-MM-DD-HH:mm:ss");
	
	var downsampling = "10m";
	
	$("#datetimepicker7").val(YesterDayDate);
	$("#datetimepicker8").val(EndDate);

	$('#datetimepicker7').datetimepicker({
		  format:'Y/m/d h:i:s',
	});

	$('#datetimepicker8').datetimepicker({
		  format:'Y/m/d h:i:s',
	});

	$('#datetimepicker_dark').datetimepicker({theme:'dark'})
	var obj = JSON.parse(flowData)
	

		$.ajax({
			dataType: "jsonp",				
			url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/pen.flow.packets"+"?"+"switchid="+source,	
			
			type : 'GET',
			success : function(response) {	
					
				$("#wait1").css("display", "none");	
				showStatsData(response);				
			},
			dataType : "json"
		});
	
})


/**
* Execute this function to show visulization of stats graph
* represnting time and metric on the axis.
*/
function showStatsData(response) {	
	var data = response
		var graphData = [];
		if(data.length){
			var getValue = data[0].dps;
			$.each(getValue, function (index, value) {
				
			  graphData.push([new Date(Number(index*1000)),value])

			 }) 
		}
		
		var g = new Dygraph(document.getElementById("graphdiv"), graphData,
        {
		    drawPoints: true,
		    labels: ['Time', $("select.selectbox_menulist").val()]
		});
}


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
		
	var selMetric=$("select.selectbox_menulist").val();
		
	
	var valid=true;
		
	if(startDate.getTime() > currentDate.getTime()) {
		$.toast({
		    heading: 'Error',
		    text: 'startDate should not be greater than currentDate.',
		    showHideTransition: 'fade',
		    position: 'top-right',
		    icon: 'error'
		})
		
		
		valid=false;
		return;
	}
	
	else if(endDate.getTime() < startDate.getTime()){
		$.toast({
		    heading: 'Error',
		    text: 'endDate should not be less than startDate',
		    showHideTransition: 'fade',
		    position: 'top-right',
		    icon: 'error'
		})
		valid=false;
		return;
	}
	
		
	var autoreload = $("#autoreload").val();
	
	var numbers = /^[-+]?[0-9]+$/;  
	
	var checkNo = $("#autoreload").val().match(numbers);
	
	var checkbox =  $("#check").prop("checked");
		
	/*if(autoreload < 0 || autoreload % 1 != 0)
	{
		$.toast({
		    heading: 'Error',
		    text: 'Autoreload input cannot be negative or decimal',
		    showHideTransition: 'fade',
		    position: 'top-right',
		    icon: 'error'
		})
		valid=false;
		return;
	}
	
		if(autoreload != checkNo)
		{
			$.toast({
			    heading: 'Error',
			    text: 'Autoreload input should only be in numbers',
			    showHideTransition: 'fade',
			    position: 'top-right',
			    icon: 'error'
			})
			valid=false;
			return;
		}*/
		
	
	//if filter values are valid then call stats api
	if(valid){
		
		
		var flowData = localStorage.getItem("flowDetails");
		var obj = JSON.parse(flowData)
		

		var source = obj.source_switch.replace(/:/g, "")
		var target = obj.target_switch.replace(/:/g, "")
		

		
		$.ajax({
			dataType: "jsonp",				
			url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/"+selMetric+"?"+"switchid="+source,	
			
			type : 'GET',
			success : function(response) {	
					
				$("#wait1").css("display", "none");	
				showStatsData(response);
				
			},
			dataType : "json"
		});
	
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
		
	var flowData = localStorage.getItem("flowDetails");
	var obj = JSON.parse(flowData);
	
	var selMetric=$("select.selectbox_menulist").val();

	var source = obj.source_switch.replace(/:/g, "")
	var target = obj.target_switch.replace(/:/g, "")
		
		
		$.ajax({
			dataType: "jsonp",				
			url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/"+selMetric+"?"+"switchid="+source,	
			type : 'GET',
			success : function(response) {	
					
				$("#wait1").css("display", "none");	
				showStatsData(response);
			},
			dataType : "json"
		});
	 
	
}

function autoreload(){
	$("#autoreload").toggle();
	var checkbox =  $("#check").prop("checked");
	if(checkbox == false){
		
		$("#autoreload").val('');
		clearInterval(callIntervalData);
		clearInterval(graphInterval);
	}
}

/* ]]> */