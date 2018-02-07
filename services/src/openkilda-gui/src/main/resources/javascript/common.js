/*<![CDATA[*/


/*Global Variable Constant*/
var metricVarList = ["latency:latency","bits:Bits/sec","megabytes:MegaBytes/sec","packets:Packets/sec","drops:Drops/sec","errors:Errors/sec", "collisions:Collisions","frameerror:Frame Errors","overerror:Overruns","crcerror:CRC Errors"];


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

var requests = null;
var loadGraph = {	
		loadGraphData:function(apiUrl,requestType,selMetric){	
			requests =  $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,
					dataType : "json",
					error : function(errResponse) {
						$("#wait1").css("display", "none");	
						showStatsGraph.showStatsData(errResponse,selMetric);
					}
				});			
			
		return requests;				
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
				$("#autoreloadId").removeClass("has-error")	
			    $(".error-message").html("");
				$('#wait1').hide();	
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
		 if(data){
			 if(data.length == 0){
			 var g = new Dygraph(document.getElementById("graphdiv"), [],
				 	 {
				 		      drawPoints: false,
				 		      labels: "test",	 		      
				 		      colors: ["#495cff","#aad200"],
				 	  });	
			 return;
			 } 
		
		 }
		 
		 
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
				$('#menulist').val('latency');
			}						
		},
		getFlowMetricData:function(response){
						
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if(metricArray[i].includes("bits") || metricArray[i].includes("packets") || metricArray[i].includes("megabytes")) {
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
				
				if(metricArray[i].includes("megabytes") || metricArray[i].includes("latency")) {
				} else{
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}			
			}
			$("select.selectbox_menulist").html("").html(optionHTML);
			$('#menulist').val('bits');
		}
}


var autoVal = {	
			
			reloadValidation:function(callback) {
				
				var autoreload = $("#autoreload").val();
				var numbers = /^[-+]?[0-9]+$/;  
				var checkNo = $("#autoreload").val().match(numbers);
				var checkbox =  $("#check").prop("checked");
			
				if(checkbox) {
					
					if($("#autoreload").val().length > 0) {	
						if(autoreload < 0) {
							common.infoMessage('Autoreload cannot be negative','error');
							valid=false;
							clearInterval(graphInterval);
							callback(valid)
						} else if(autoreload == 0) {
							common.infoMessage('Autoreload cannot be zero','error');
							valid=false;							
							clearInterval(graphInterval);
							callback(valid)
						}else if(!checkNo) {
							
							$("#autoreloadId").addClass("has-error")	
							$(".error-message").html("Please enter positive number only");			
							valid=false;
							clearInterval(graphInterval);
							callback(valid)
						}
						else{
							valid = true;
							callback(valid)
						}
					}
				}
		   }
	}
 


/* ]]> */