/*<![CDATA[*/

$(document).ready(function(){
	
	var switchname=window.location.href.split("#")[1]
	
	$("#kilda-switch-name").parent().append(switchname)
	
	$.ajax({
		url : APP_CONTEXT+"/switch",
		type : 'GET',
		success : function(response) {	
			
			showSwitchData(response);
		},
		dataType : "json"
	});
	
	callPortDetailsAPI(switchname);	
})


 function callPortDetailsAPI(switchname){	
	$.ajax({
		url : APP_CONTEXT+"/switch/"+switchname+"/ports",
		type : 'GET',
		success : function(response) {
			
			$("#wait1").css("display", "none");
			showPortData(response);
		},
		dataType : "json"
	});
}


function showSwitchData(response){	
	
    for(var i = 0; i < response.switches.length; i++) {
        var obj = response.switches[i];
        $(".switchdetails_div_controller").html(response.switches[i].controller);
        $(".switchdetails_div_address").html(response.switches[i].address);
        $(".switchdetails_div_name").html(response.switches[i].name);
        $(".switchdetails_div_desc").html(response.switches[i].description);        
    } 
}

function showPortData(response){	

	var tmp_obj =''; 
	var last_id = '1';
	var last_html = '';
	var tmp_html = '';
	 for(var i = 0; i < response.length; i++) {
		 	console.log(i+" Response is ");
		 	console.log(response[i]);
		 	if(i!=0){
		 		tmp_obj  = $("#portdetails_div .rep_div").last().attr("id");
		 		if(tmp_obj != ""){		 			
		 			last_id = tmp_obj.split("_")[1];
		 			++last_id;
		 			tmp_html = '<div class="row rep_div" id="div_'+last_id+'">'+$("#portdetails_div .rep_div").last().html()+'</div>';
		 			$("#port-details1").append(tmp_html);		 			
		 			//$("#portdetails_div .rep_div").last().attr("id",++last_id);
		 		}
		 	}
		 	
	        $(".rep_div#div_"+last_id+" .portdetails_div_interface").html(response[i].interfacetype);
	        $(".rep_div#div_"+last_id+" .portdetails_div_port_name").html(response[i].port_name);
	        $(".rep_div#div_"+last_id+" .portdetails_div_port_number").html(response[i].port_number);
	        $(".rep_div#div_"+last_id+" .portdetails_div_status").html(response[i].status);
	 }
}

/* ]]> */