/*<![CDATA[*/


/*Global Variable Constant*/
var metricVarList = ["bits:Bits/sec","bytes:Bytes/sec","megabytes:MegaBytes/sec","packets:Packets/sec","drops:Drops/sec","errors:Errors/sec", "collisions:Collisions","frameerror:Frame Errors","overerror:Overruns","crcerror:CRC Errors"];


var common = {	
		getData:function(apiUrl,requestType){	
		return $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,dataType : "json"});							
		},
		infoMessage:function(msz,type){
		$.toast({heading:(type =='info'?'Information':type), text: msz, showHideTransition: 'fade',position: 'top-right', icon: type, hideAfter : 6000})
		}
}

/** sub menu related code start **/
var urlString = window.location.href;
	$("#menubar-tbn li").each(function(){
	$(this).removeClass();
})

if(urlString.indexOf("topology") != -1 || urlString.indexOf("portdetails") != -1 || urlString.indexOf("isl") != -1){
	$("#topology-menu-id").addClass("active")
}

else if(urlString.indexOf("flows") != -1 || urlString.indexOf("flowdetails") != -1) {
	$("#flows-menu-id").addClass("active")
}

else if( urlString.indexOf("switch") != -1) {
	$("#switch-menu-id").addClass("active")
}

else if(urlString.indexOf("home") != -1) {
	$("#home-menu-id").addClass("active")
}
/** sub menu related code End **/

	
$('.t-logy-icon').click(function(e){
	 e.stopPropagation();
	 $("#topology-txt").slideToggle();
});

$('#topology-txt').click(function(e){
    e.stopPropagation();
});

$(document).click(function(){
    $('#topology-txt').slideUp();
});


var loadGraph = {	
		loadGraphData:function(apiUrl,requestType,selMetric){	
		return $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,
			dataType : "json",
			error : function(errResponse) {
				$("#wait1").css("display", "none");	
				showStatsGraph.showStatsData(errResponse,selMetric);
			}
		});							
	}
}

var graphAutoReload = {	
		autoreload:function(){
			$("#autoreload").toggle();
			var checkbox =  $("#check").prop("checked");
			if(checkbox == false){
				
				$("#autoreload").val('');
				clearInterval(callIntervalData);
				clearInterval(graphInterval);
			}
		}
}


 var showStatsGraph = {	

	showStatsData:function(response,metricVal) {	
	
		var metric1 = "";
		var metric2 = "";
		var data = response;
		var jsonResponse = response.responseJSON;
				
		 var graphData = [];		
		 
		if(!jsonResponse) {
			
		    	var getValue = data[0].dps;	    	
		    	 metric1 = data[0].metric;	
		    	 
		    	if(data.length == 2) {
		    		var getVal = data[1].dps;
		    		 metric2 = data[1].metric;
		    	}
		    	    
				    if(!getValue) {
				    	metric1 = "F";
				    	metric2 = "R";		    	
				    } else {
				    	 for(i in getValue) {
						    	
						      var temparr = [];
						      temparr[0] = new Date(Number(i*1000));
						      if(metricVal == "megabytes"){
						    	  temparr[1] = getValue[i] / 1048576;
						      }
						      else{
						    	  temparr[1] = getValue[i]
						      }
						      
						      if(data.length == 2) {
						    	  if(metricVal == "megabytes"){
						    	  	temparr[2] = getVal[i] / 1048576;
						    	  }
						    	  else{
						    			temparr[2] = getVal[i];
						    	  }
						      }
						      graphData.push(temparr)
						 }
				    }
				    if(metric1 && metric2){
				    	var labels = ['Time', metric1,metric2];
				    }else if(metric1){
				    	var labels = ['Time', metric1];
				    }
				    else{
				    	var labels = ['Time', metric2];
				    }	
		    	
		}else{
			metric1 = "F";
	    	metric2 = "R";
			var labels = ['Time', metric1,metric2];
		}
		
		  
		    
		     var g = new Dygraph(document.getElementById("graphdiv"), graphData,
		 	 {
		 		      drawPoints: false,
		 		      labels: labels,	 		      
		 		      colors: ["#495cff","#aad200"],
		 	  });	
	}
}


var getMetricDetails = {	
		getIslMetricData:function(response) {
			var linkData = localStorage.getItem("linkData");
			var obj = JSON.parse(linkData);
			
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if( metricArray[i].includes("megabytes")) {
				} else {
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}
				
			} if (obj.hasOwnProperty("flowid")) {
				$("select.selectbox_menulist").html("").html(optionHTML);
				$('#menulist').val('pen.flow.packets');
			} else {
				$("select.selectbox_menulist").html("").html(optionHTML);			
				$('#menulist').val('bits');
			}						
		},
		getFlowMetricData:function(response){
						
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if(metricArray[i].includes("bits") || metricArray[i].includes("packets") || metricArray[i].includes("bytes") || metricArray[i].includes("megabytes")) {
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}
			}
			$("select.selectbox_menulist").html("").html(optionHTML);
			$('#menulist').val('packets');
		},
		getPortMetricData:function(response){
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if( metricArray[i].includes("megabytes")) {
				} else{
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}			
			}
			$("select.selectbox_menulist").html("").html(optionHTML);
			$('#menulist').val('bits');
		}
}


/* ]]> */