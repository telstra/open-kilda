/*<![CDATA[*/
/** Below the javascript/ajax/jquery code to generate and display the stats api results.
* By default api will show stats of the previous day
* user can generate stats via filter elements on the html page */


/**
* Execute  getGraphData function when onchange event is fired 
* on the filter input values of datetimepicker, downsampling and menulist.
*/

var flowid = window.location.href.split("#")[1];
var graphInterval;
$(function() {				
		var count = 0;
		$("#datetimepicker7,#datetimepicker8").on("change",function(event) {
			count++;
			if(count == 1){
				count = -1;
				getGraphData();
				return;
			}			
		});
		
		$("#downsampling,#menulist,#autoreload,#directionlist").on("change",function(event) {
				getGraphData();	
		});
		
		$('#reverseFromdatepicker,#reverseTodatepicker').on('change',function(event){
			var fromDate =new Date($('#reverseFromdatepicker').val());
			var toDate = new Date($('#reverseTodatepicker').val());
			if(moment(fromDate).isAfter(toDate)){
				common.infoMessage("Start date can not be after End date",'error');
				return;
			}
			loadReverseGraph(fromDate,toDate);
			
		})
		$('#forwardFromdatepicker,#forwardTodatepicker').on('change',function(event){
			var fromDate = new Date($('#forwardFromdatepicker').val());
			var toDate = new Date($('#forwardTodatepicker').val());
			if(moment(fromDate).isAfter(toDate)){
				common.infoMessage("Start date can not be after End date",'error');
				return;
			}
			loadForwardGraph(fromDate,toDate);
		})
		$('#timezoneForward').on('change',function(){
			var timezone = $('#timezoneForward option:selected').val();
			var dat2 = new Date();
			var dat1 = new Date(dat2.getTime());
			dat1.setHours(dat2.getHours() - 4);		
			if(timezone == 'UTC'){
				var startDate = moment(dat1).utc().format("YYYY/MM/DD HH:mm:ss");
				var endDate = moment(dat2).utc().format("YYYY/MM/DD HH:mm:ss");
				$('#forwardFromdatepicker').val(startDate);
				$('#forwardTodatepicker').val(endDate)
			}else{
				var startDate = moment(dat1).format("YYYY/MM/DD HH:mm:ss");
				var endDate = moment(dat2).format("YYYY/MM/DD HH:mm:ss");
				$('#forwardFromdatepicker').val(startDate);
				$('#forwardTodatepicker').val(endDate)
			}
			loadForwardGraph(new Date(startDate),new Date(endDate));
		})
		$('#timezoneReverse').on('change',function(){
			var timezone = $('#timezoneReverse option:selected').val();
			var dat2 = new Date();
			var dat1 = new Date(dat2.getTime());
			dat1.setHours(dat2.getHours() - 4);		
			if(timezone == 'UTC'){
				var startDate = moment(dat1).utc().format("YYYY/MM/DD HH:mm:ss");
				var endDate = moment(dat2).utc().format("YYYY/MM/DD HH:mm:ss");
				$('#reverseFromdatepicker').val(startDate);
				$('#reverseTodatepicker').val(endDate)
			}else{
				var startDate = moment(dat1).format("YYYY/MM/DD HH:mm:ss");
				var endDate = moment(dat2).format("YYYY/MM/DD HH:mm:ss");
				$('#reverseFromdatepicker').val(startDate);
				$('#reverseTodatepicker').val(endDate)
			}
			loadReverseGraph(new Date(startDate),new Date(endDate));
		})
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
			getGraphData();	
		})
		
	
		
	$('#flowselectedGraph').on('change',function(){
		if($(this).val() == 'flow'){
			$('#directionDropdown').hide();
			$('#islmenuListDropdown').show();
			$('#flow_graph_directions').show();
			$('#menulist').val('packets');
			getGraphData();	
		}else{
			$('#directionDropdown').show();
			$('#islmenuListDropdown').hide();
			$('#directionDropdown').val('forward');
			$('#flow_graph_directions').hide();
			getGraphData();	
			
		}
	})
});	

/**
* Execute this function when page is loaded
* or when user is directed to this page.
*/
$(document).ready(function() {

	
	$.datetimepicker.setLocale('en');
	var date = new Date()
	$('#timezone').val("LOCAL");
	$('#timezoneForward').val('LOCAL');
	$('#timezoneReverse').val('LOCAL');
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
	$('#forwardFromdatepicker').datetimepicker({
		  format:'Y/m/d H:i:s',
	});
	$('#forwardTodatepicker').datetimepicker({
		  format:'Y/m/d H:i:s',
	});
	$('#reverseFromdatepicker').datetimepicker({
		  format:'Y/m/d H:i:s',
	});
	$('#reverseTodatepicker').datetimepicker({
		  format:'Y/m/d H:i:s',
	});
	
	$('#datetimepicker_dark').datetimepicker({theme:'dark'});
	
	var selMetric="packets";
	fetchAndLoadGraphData(flowid,convertedStartDate,convertedEndDate,downsampling,selMetric,yesterday,EndDate,timezone);
	
})

function fetchAndLoadGraphData(flowid,convertedStartDate,convertedEndDate,downsampling,selMetric,yesterday,EndDate,timezone){
	var selectedGraph = $('#flowselectedGraph').val();
	var direction = $('#directionlist option:selected').val();
	if(selectedGraph == 'flow'){
		var url ="/stats/flowid/"+flowid+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric;
	}else{
		var url ="/stats/flow/losspackets/"+flowid+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+direction;
	}
	loadGraph.loadGraphData(url,"GET",selMetric).then(function(response) {
		var timezone = $('#timezone option:selected').val();
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData(response,selMetric,null,null,yesterday,EndDate,timezone); 
	},function(error){
		var timezone = $('#timezone option:selected').val();
		$("#wait1").css("display", "none");
		$('body').css('pointer-events', 'all');
		showStatsGraph.showStatsData([],selMetric,null,null,yesterday,EndDate,timezone); 
	})
}
function loadForwardGraph(fromDate, toDate){
	$('#forward_path_graph').html('');
	var isGraphLoaded = $('#forward_path_graph').css('display') == 'block';
	var timezone = $('#timezoneForward option:selected').val();
	if(typeof(fromDate) == 'undefined' && typeof(toDate) == 'undefined') {
		$('#reverse_path_graph').hide();
		$('#reverse_graph').hide();
		$('#forward_graph').slideToggle();
		$('#forward_path_graph').slideToggle('slow');
		toggleOpenCloseClass("forward_graph_icon","glyphicon-plus","glyphicon-minus");	
	}
	if(!isGraphLoaded || (typeof(fromDate) !==' undefined' && typeof(toDate) !== 'undefined')){
		$('#reverse_graph_icon').removeClass('glyphicon-minus').addClass('glyphicon-plus');
		if(typeof(fromDate) !==' undefined' && typeof(toDate) !== 'undefined') {
			if(timezone == 'UTC'){
				var startDate = moment(fromDate).format("YYYY-MM-DD-HH:mm:ss"); 
				var endDate = moment(toDate).format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate =  moment(fromDate).format("YYYY-MM-DD HH:mm:ss"); 
				var toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss"); 
			}else{
				var startDate = moment(fromDate).utc().format("YYYY-MM-DD-HH:mm:ss"); 
				var endDate = moment(toDate).utc().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate =  moment(fromDate).format("YYYY-MM-DD HH:mm:ss"); 
				var toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss"); 
			}
		}else{
			if(timezone == 'UTC'){
				var startDate = moment().subtract(4,'hour').format("YYYY-MM-DD-HH:mm:ss"); 
				var endDate = moment().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate = moment().subtract(4,'hour').format("YYYY/MM/DD HH:mm:ss");
				var toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
				$('#forwardFromdatepicker').val(fromStartDate);
				$('#forwardTodatepicker').val(toEndDate);
			}else{
				var startDate = moment().subtract(4,'hour').utc().format("YYYY-MM-DD-HH:mm:ss");
				var endDate = moment().utc().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate = moment().subtract(4,'hour').format("YYYY/MM/DD HH:mm:ss");
				var toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
				$('#forwardFromdatepicker').val(fromStartDate);
				$('#forwardTodatepicker').val(toEndDate);
			}
			
		}
		var downsampling ='30s';
		var flowPathData = flowObj.getFlowPathObj(); 
		var flowid = (typeof(flowPathData) !== 'undefined' && flowPathData) ? flowPathData.flowid : null;
		var forwardPathData = (flowPathData && flowPathData.flowpath_forward && flowPathData.flowpath_forward.length)? flowPathData.flowpath_forward : null;
		if(forwardPathData && flowid){
			var switches = forwardPathData.map(function(d){
				return common.toggleSwitchID(d.switch_id);
			});
			var url = '/stats/flowpath';
			var postData ={
							"flowid":flowid,
							"switches":switches,
							"startdate":startDate,
							"enddate":endDate,
							"downsample":downsampling,
							"direction":'forward',
						  }
			$('#waitforward').show();
			setTimeout(function(){
				$.ajax({
						url : APP_CONTEXT+url,
						contentType:'application/json',
						dataType : "json",
						type : "POST",
						data:JSON.stringify(postData)
					}).then(function(response){
					loadPathGraph(response,fromStartDate,toEndDate,'forward',timezone);
				},function(error){
					$('#waitforward').hide();
					loadPathGraph([],fromStartDate,toEndDate,'forward',timezone);
				})
				
			},500);
			
		}else{
			common.infoMessage('No Flow Path data found','warning');
		}
		
	}
}

function getColorCode(j,arr){
	var chars = '0123456789ABCDE'.split('');
    var hex = '#';
    for (var i = 0; i < 6; i++) {
        hex += chars[Math.floor(Math.random() * 16)];
    }
    var colorCode = hex;
	if(arr.indexOf(colorCode) < 0 ){
		return colorCode;
	}else{
		return getColorCode(j,arr);
	}
}
function computeGraphData(data,startDate,endDate,type,timezone) {
	var graphData = [];
	var labels =["Date"];
	var color = [];
	if(typeof(startDate)!=='undefined' && startDate!=null) {
		var dat = new Date(startDate);
		var startTime = dat.getTime();
		var usedDate = new Date();
		if(typeof(timezone) !== 'undefined' && timezone=='UTC') {
			startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
		}
		var arr = [new Date(startTime)];
		for(var j = 0; j < data.length; j++) {
			arr.push(null);
		}
		graphData.push(arr);
	 }
	  if(data) {
		  if(data.length > 0) { 
			   for(var j = 0; j < data.length; j++){ 
				   var dataValues = (typeof(data[j]) !=='undefined') ? data[j].dps : 0;
				   var metric = (typeof(data[j]) !=='undefined') ? data[j].metric : '';
				   if(metric !== 'pen.flow.packets'){
					   metric = metric + "("+data[j].tags.switchid+")";
					   labels.push(metric);
					   var colorCode = getColorCode(j,color);
			            color.push(colorCode);
					   var k = 1;
					   for(i in dataValues) {

				            if(dataValues[i]<0){
				            	dataValues[i] = 0;
				            }
				            
				            if(j == 0){
				            	 	var temparr = [];
				            	 	temparr[0] = new Date(Number(i*1000)); 
							      	temparr[1] = dataValues[i];
							      	graphData[k] = temparr;
							      	
				             }else{
				            	 var temparr = (typeof(graphData[k])!='undefined' && graphData[k]!=null) ? graphData[k] : [];
				            	 temparr.push(dataValues[i]);
				            	 graphData[k] = temparr;
				             }
						    k++;  	
						 }
				   }else if(metric === 'pen.flow.packets'){
					   metric = metric + "("+data[j].tags.flowid+")";
					   labels.push(metric);
					   color.push("#aad200");
					   var k = 1;
					   for(i in dataValues) {

				            if(dataValues[i]<0){
				            	dataValues[i] = 0;
				            }    
				             
				             if(j == 0){
				            	 	var temparr = [];
				            	 	temparr[0] = new Date(Number(i*1000)); 
							      	temparr[1] = dataValues[i];
							      	graphData[k] = temparr;
							      	
				             }else{
				            	 var temparr = (typeof(graphData[k])!='undefined' && graphData[k]!=null) ? graphData[k] : [];
				            	 temparr.push(dataValues[i]);
				            	 graphData[k] = temparr;
				             }
						    k++;  	
						 }
					  
				   }else{
					   continue;
				   }
			   }
			}
	  }
	  if(typeof(endDate)!=='undefined' && endDate!=null){ 
			var dat = new Date(endDate);
			var lastTime = dat.getTime();
			var usedDate = (graphData && graphData.length) ? new Date(graphData[graphData.length-1][0]): new Date();
			if(typeof(timezone) !== 'undefined' && timezone == 'UTC') {
				lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
			}
			var arr = [new Date(lastTime)];
			for(var j = 0; j < data.length; j++){
				arr.push(null);
			}
			graphData.push(arr);
		}
	 
	  return {labels:labels,data:graphData,color:color};
}
function loadPathGraph(data,startDate,endDate,type,timezone){
   var graph_data = computeGraphData(data,startDate,endDate,type,timezone);
  var graphData = graph_data['data'];
  var labels = graph_data['labels'];
  var series = {};
  var colors = graph_data['color'];
  if(labels && labels.length){
	  for(var k = 0; k < labels.length; k++){
		  if(k!=0){
			  series[labels[k]] = {color:colors[k-1]};
		  }
	  }
	
  }
  if(timezone == 'UTC'){
	  if(type == 'forward'){
		  var g = new Dygraph(
			        document.getElementById("forward_path_graph"),graphData,
			        {
			        	  labels: labels,	
			        	  labelsUTC:true,
			        	  series:series, 
			        }
			    );
			  $('#waitforward').hide();
	  }else if(type=='reverse'){
		  var g = new Dygraph(
			        document.getElementById("reverse_path_graph"),graphData,
			        {
			        	  labels: labels,		      
			        	  series:series,									 			 
			 		      labelsUTC:true,
			         }
			    );
		  $('#waitreverse').hide();
	  }
  }else{
	  if(type == 'forward'){
		  var g = new Dygraph(
			        document.getElementById("forward_path_graph"),graphData,
			        {
			        	  labels: labels,
			        	  series:series, 
			        }
			    );
			  $('#waitforward').hide();
	  }else if(type=='reverse'){
		  var g = new Dygraph(
			        document.getElementById("reverse_path_graph"),graphData,
			        {
			        	  labels: labels,		      
			        	  series:series,	
			         }
			    );
		  $('#waitreverse').hide();
	  }
  }  
  
}
function toggleOpenCloseClass(ID,closeClass,OpenClass){
	if($('#'+ID).hasClass(closeClass)){
		$('#'+ID).removeClass(closeClass).addClass(OpenClass);
	}else{
		$('#'+ID).removeClass(OpenClass).addClass(closeClass);
	}
}
function loadReverseGraph(fromDate,toDate){
	$('#reverse_path_graph').html('');
	var isGraphLoaded = $('#reverse_path_graph').css('display') == 'block';
	var timezone = $('#timezoneReverse option:selected').val();
	if(typeof(fromDate) =='undefined' && typeof(toDate) == 'undefined'){
		$('#forward_path_graph').hide();
		$('#forward_graph').hide();
		$('#reverse_graph').slideToggle('slow');
		$('#reverse_path_graph').slideToggle('slow');
		toggleOpenCloseClass("reverse_graph_icon","glyphicon-plus","glyphicon-minus");
	}
	if(!isGraphLoaded || (typeof(fromDate) !=='undefined' && typeof(toDate) !== 'undefined') ){
		$('#forward_graph_icon').removeClass('glyphicon-minus').addClass('glyphicon-plus');
		if(typeof(fromDate) !==' undefined' && typeof(toDate) !== 'undefined') {
			if(timezone == 'UTC'){
				var startDate = moment(fromDate).format("YYYY-MM-DD-HH:mm:ss"); 
				var endDate = moment(toDate).format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate =  moment(fromDate).format("YYYY-MM-DD HH:mm:ss"); 
				var toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss"); 
			}else{
				var startDate = moment(fromDate).utc().format("YYYY-MM-DD-HH:mm:ss");
				var endDate = moment(toDate).utc().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate =  moment(fromDate).format("YYYY-MM-DD HH:mm:ss"); 
				var toEndDate = moment(toDate).format("YYYY/MM/DD HH:mm:ss");
			}
			
			
		}else{
			if(timezone == 'UTC'){
				var startDate = moment().subtract(4,'hour').format("YYYY-MM-DD-HH:mm:ss"); 
				var endDate = moment().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate = moment().subtract(4,'hour').format("YYYY/MM/DD HH:mm:ss");
				var toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
				$('#reverseFromdatepicker').val(fromStartDate);
				$('#reverseTodatepicker').val(toEndDate);
			}else{
				var startDate = moment().subtract(4,'hour').utc().format("YYYY-MM-DD-HH:mm:ss"); // To do change the value 4 to 2 to change time difference to 2 hours in subtract function
				var endDate = moment().utc().format("YYYY-MM-DD-HH:mm:ss");
				var fromStartDate = moment().subtract(4,'hour').format("YYYY/MM/DD HH:mm:ss");
				var toEndDate = moment().format("YYYY/MM/DD HH:mm:ss");
				$('#reverseFromdatepicker').val(fromStartDate);
				$('#reverseTodatepicker').val(toEndDate);
			}
			
		}
		
		var downsampling ='30s';
		var url = '/stats/flowpath';
		var flowPathData = flowObj.getFlowPathObj();
		var flowid = (typeof(flowPathData) !== 'undefined' && flowPathData) ? flowPathData.flowid : null;
		var reversePathData = (flowPathData && flowPathData.flowpath_reverse && flowPathData.flowpath_reverse.length) ? flowPathData.flowpath_reverse:null;
		if(reversePathData && flowid){
			var switches = reversePathData.map(function(d){
				return common.toggleSwitchID(d.switch_id);
			});
			
			var postData ={
							"flowid":flowid,
							"switches":switches,
							"startdate":startDate,
							"enddate":endDate,
							"downsample":downsampling,
							"direction":'reverse',
						  }
			$('#waitreverse').show();
			setTimeout(function(){
				$.ajax({
						url : APP_CONTEXT+url,
						contentType:'application/json',
						dataType : "json",
						type : "POST",
						data:JSON.stringify(postData)
					}).then(function(response){
					loadPathGraph(response,fromStartDate,toEndDate,'reverse',timezone);
				},function(error){
					$('#waitreverse').hide();
					loadPathGraph([],fromStartDate,toEndDate,'reverse',timezone);
				})
			},500);
			
		}else{
			common.infoMessage('No Flow Path data found','warning');
		}
	}
	
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
	var downsampling = $("#downsampling option:selected").val();
	var timezone = $('#timezone option:selected').val();
	var downsamplingValidated = regex.test(downsampling);
	if(timezone == 'UTC'){ 
		var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
		var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");	
	}else{
		var convertedStartDate = moment(startDate).utc().format("YYYY-MM-DD-HH:mm:ss");
		var convertedEndDate = moment(endDate).utc().format("YYYY-MM-DD-HH:mm:ss");	
	}
		
	var selMetric=$("select.selectbox_menulist").val();
	var valid=true;
	
	
	if(downsamplingValidated == false) {	
		$("#DownsampleID").addClass("has-error")	
		$(".downsample-error-message").html("Please enter valid input.");			
		valid=false;
		return
	}
	
	if(startDate.getTime() >= currentDate.getTime()) {
		$("#fromId").addClass("has-error")	
		$(".from-error-message").html("From date should not be greater than currentDate.");		
		valid=false;
		return;
	} else if(endDate.getTime() <= startDate.getTime()){
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
    	
		var megaBytes = selMetric;
		if(megaBytes == "megabytes"){
			selMetric = "bytes";		
		}
		
		fetchAndLoadGraphData(flowid,convertedStartDate,convertedEndDate,downsampling,selMetric,startDate,endDate,timezone);
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

function callIntervalData() {
	
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7").val());
	var autoreload = $("#autoreload").val();
	var savedEnddate = new Date($('#savedEnddate').val());
	var timezone = $('#timezone option:selected').val();
	savedEnddate = new Date(savedEnddate.getTime() + (autoreload * 1000));
	$('#savedEnddate').val(savedEnddate);
	var endDate = savedEnddate ;// new Date() ||
	if(timezone == 'UTC'){
		var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");
		var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	}else{
		var convertedStartDate = moment(startDate).utc().format("YYYY-MM-DD-HH:mm:ss");
		var convertedEndDate = moment(endDate).utc().format("YYYY-MM-DD-HH:mm:ss");
	}
		
	var selMetric=$("select.selectbox_menulist").val();
	var downsampling = $("#downsampling option:selected").val();
	fetchAndLoadGraphData(flowid,convertedStartDate,convertedEndDate,downsampling,selMetric,startDate,endDate,timezone);
}

/* ]]> */