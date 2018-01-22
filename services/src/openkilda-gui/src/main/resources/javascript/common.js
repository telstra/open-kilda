/*<![CDATA[*/
var common = {	
		getData:function(apiUrl,requestType){	
		return $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,dataType : "json"});							
		},
		infoMessage:function(msz,type){
		$.toast({heading:(type =='info'?'information':type), text: msz, showHideTransition: 'fade',position: 'top-right', icon: type, hideAfter : 6000})
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
		loadGraphData:function(apiUrl,requestType){	
		return $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,
			dataType : "json",
			error : function(errResponse) {
				$("#wait1").css("display", "none");	
				showStatsGraph.showStatsData(errResponse);
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

	showStatsData:function(response) {	
			
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
}


/* ]]> */