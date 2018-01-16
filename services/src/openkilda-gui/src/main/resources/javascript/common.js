var common = {	
			getData:function(apiUrl,requestType){	
				
				//alert('hello')
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


/** stats related code start **/
var islStats = {
	
	   getGraphDataPage:function(){

		var linkData = localStorage.getItem("linkData");	
		var obj = JSON.parse(linkData)
		
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
		var obj = JSON.parse(linkData)
		
			$.ajax({
				dataType: "jsonp",				
				url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/pen.isl.latency"+"?"+"src_switch="+source+"&src_port="+obj.src_port+"&dst_switch="+target+"&dst_port="+obj.dst_port+"&averageOf=10m",	
				type : 'GET',
				success : function(response) {	
					console.log(response);
					$("#wait1").css("display", "none");	
					commonGraphMethods.showStatsData(response);
				},
				dataType : "json"
			});
	}
} 



var portStats = {
		
			getGraphDataPage:function(){
			
				console.log("hi")
				var portData = localStorage.getItem("portDetails");
				var portObj = JSON.parse(portData)
				
				$.datetimepicker.setLocale('en');
				var date = new Date()
				
				var yesterday = new Date(date.getTime());
				yesterday.setDate(date.getDate() - 1);
				
				var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
				var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
				
				var convertedStartDate = moment(YesterDayDate).format("YYYY-MM-DD-HH:mm:ss");
				var convertedEndDate = moment(EndDate).format("YYYY-MM-DD-HH:mm:ss");
				
				var downsampling = "10m";
				
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
				
				var switchname = window.location.href.split("#")[1];
				
				var sourceswitch = switchname.replace(/:/g, "");
				

					$.ajax({
						dataType: "jsonp",				
						url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/pen.isl.latency"+"?"+"src_switch="+sourceswitch+"&src_port="+portObj.port_number+"&averageOf=10m",	
						type : 'GET',
						success : function(response) {	
								
							$("#wait1").css("display", "none");	
							commonGraphMethods.showStatsData(response);
						},
						dataType : "json"
					});
		}
	} 


var flowStats = {
		
		getGraphDataPage:function(){
		
			console.log("hi")
			var portData = localStorage.getItem("portDetails");
			var portObj = JSON.parse(portData)
			
			$.datetimepicker.setLocale('en');
			var date = new Date()
			
			var yesterday = new Date(date.getTime());
			yesterday.setDate(date.getDate() - 1);
			
			var YesterDayDate = moment(yesterday).format("YYYY/MM/DD HH:mm:ss");
			var EndDate = moment(date).format("YYYY/MM/DD HH:mm:ss");
			
			var convertedStartDate = moment(YesterDayDate).format("YYYY-MM-DD-HH:mm:ss");
			var convertedEndDate = moment(EndDate).format("YYYY-MM-DD-HH:mm:ss");
			
			var downsampling = "10m";
			
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
			
			var switchname = window.location.href.split("#")[1];
			
			var sourceswitch = switchname.replace(/:/g, "");
			

				$.ajax({
					dataType: "jsonp",				
					url : APP_CONTEXT + "/stats/"+convertedStartDate+"/"+convertedEndDate+"/pen.isl.latency"+"?"+"src_switch="+sourceswitch+"&src_port="+portObj.port_number+"&averageOf=10m",	
					type : 'GET',
					success : function(response) {	
							
						$("#wait1").css("display", "none");	
						commonGraphMethods.showStatsData(response);
					},
					dataType : "json"
				});
	}
}

var commonGraphMethods = {	
		showStatsData:function(response){	
			
			var data = response
			var graphData = [];
			if(data){
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


var autoReload = {	
		autoreloadGraph:function(){	
			
			$("#autoreload").toggle();
			var checkbox =  $("#check").prop("checked");
			if(checkbox == false){
				
				$("#autoreload").val('');
				clearInterval(callIntervalData);
				clearInterval(graphInterval);
			}						
		}
}





/** stats related code End **/

