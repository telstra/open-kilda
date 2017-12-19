/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/




/** show switch details when page is loaded or
 *  when user is redirected to this page*/
$(document).ready(function(){
	
	var switchname=window.location.href.split("#")[1]
	
	$("#kilda-switch-name").parent().append(switchname)
	
	$.ajax({
		url : APP_CONTEXT+"/switch",
		type : 'GET',
		success : function(response) {	
			
			showSwitchData(response); // method call showSwitchData()
		},
		dataType : "json"
	});
	
	callPortDetailsAPI(switchname);	// method call callPortDetailsAPI()
	
	$(".rep_div").on("click",function(e){

		setPortData(switchname,this);
	})
})



/** function to retrieve and show port details*/
 function callPortDetailsAPI(switchname){	
	$.ajax({
		url : APP_CONTEXT+"/switch/"+switchname+"/ports",
		type : 'GET',
		success : function(response) {
			
			$("#wait1").css("display", "none");
			showPortData(response);  //method call showPortData()
		},
		dataType : "json"
	});
}

/** function to retrieve and show switch details from 
 * the switch response json object and display on the html page*/
function showSwitchData(response){	
	
	console.log (response)
    for(var i = 0; i < response.switches.length; i++) {
        var obj = response.switches[i];
        console.log(response.switches[i].name);
        
    	var switchname=window.location.href.split("#")[1]

        if(response.switches[i].name == switchname) {
            
            $(".switchdetails_div_controller").html(response.switches[i].controller);
            $(".switchdetails_div_address").html(response.switches[i].address);
            $(".switchdetails_div_name").html(response.switches[i].name);
            $(".switchdetails_div_desc").html(response.switches[i].description);   
        }
    } 
}


/** function to retrieve and show port details from 
 * the port response json object and display on the html page*/
function showPortData(response){	

	var tmp_obj =''; 
	var last_id = '1';
	var last_html = '';
	var tmp_html = '';
	 for(var i = 0; i < response.length; i++) {
//		 	console.log(i+" Response is ");
//		 	console.log(response[i]);
		 	if(i!=0){
		 		tmp_obj  = $("#portdetails_div .rep_div").last().attr("id");
		 		if(tmp_obj != ""){		 			
		 			last_id = tmp_obj.split("_")[1];
		 			++last_id;
		 			tmp_html = '<div class="row rep_div" id="div_'+last_id+'">'+$("#portdetails_div .rep_div").last().html()+'</div>';
		 			$("#port-details1").append(tmp_html);		 			
		 		}
		 	}
		 	
	        $(".rep_div#div_"+last_id+" .portdetails_div_interface").html(response[i].interfacetype);
	        $(".rep_div#div_"+last_id+" .portdetails_div_port_name").html(response[i].port_name);
	        $(".rep_div#div_"+last_id+" .portdetails_div_port_number").html(response[i].port_number);
	        $(".rep_div#div_"+last_id+" .portdetails_div_status").html(response[i].status);
	        
	      
	        if(response[i].status == "OK") {
	        	$("#port-details1").addClass('up-state');
	        } else {
	        	$("#port-details1").addClass('down-state');
	        }
	 }
}


/*function showportDetails(switchname) {


}

*/

function setPortData(switchname,domObj){
	
	$(domObj).html()
	
	var portData = {'interface':"",'port_name':"",'port_number':"",'status':""};
	
	if($(domObj).find(".portdetails_div_interface")){
		portData.interface = $(domObj).find(".portdetails_div_interface").html();
	}

	if($(domObj).find(".portdetails_div_port_name")){
		portData.port_name = $(domObj).find(".portdetails_div_port_name").html();
	}
	
	if($(domObj).find(".portdetails_div_port_number")){
		portData.port_number = $(domObj).find(".portdetails_div_port_number").html();
	}
	
	if($(domObj).find(".portdetails_div_status")){
		portData.status = $(domObj).find(".portdetails_div_status").html();
	}

	
	localStorage.setItem('portDetails',JSON.stringify(portData));

	url = "portdetails#" + switchname;
	window.location = url;
}

/* ]]> */