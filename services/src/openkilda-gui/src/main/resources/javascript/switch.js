/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/

/** show switch details when page is loaded or
 *  when user is redirected to this page*/
$(document).ready(function(){
	
	
	var switchData = localStorage.getItem("switchDetailsJSON");
	var obj = JSON.parse(switchData)
	var switchname=window.location.href.split("#")[1]
	
	
	console.log(switchname)
	
	if(switchname.includes("id")) {
	var switchname=window.location.href.split("#")[2];
	console.log("switch")
		
	var tmp_anchor = '<a href="/openkilda/switch">' + "Switch" + '</a>';
	$("#kilda-nav-label").parent().append(tmp_anchor)
	
		$("#topology-menu-id").removeClass("active");
		$("#switch-menu-id").addClass("active");

	} else {
		console.log("topology")
				
	var tmp_anchor = '<a href="/openkilda/topology">' + "Topology" + '</a>';
	$("#kilda-nav-label").parent().append(tmp_anchor)
		
		$("#switch-menu-id").removeClass("active");
		$("#topology-menu-id").addClass("active");
	}
	

	
	$("#kilda-switch-name").parent().append(switchname)	
	common.getData("/switch/list","GET").then(function(response){
		showSwitchData(response,switchname); 
	})
	
	callPortDetailsAPI(switchname);	// method call callPortDetailsAPI()
	
	$(document).on("click",".rep_div",function(e){
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
			$('body').css('pointer-events','all'); 	
			showPortData(response);  //method call showPortData()
		},
		dataType : "json"
	});
}

/** function to retrieve and show switch details from 
 * the switch response json object and display on the html page*/
function showSwitchData(response,switchname){	

	for(var i = 0; i < response.length; i++) {
        var obj = response[i];

        if(response[i].name == switchname) {
            
            $(".switchdetails_div_hostname").html(response[i].hostname);
            $(".switchdetails_div_address").html(response[i].address);
            $(".switchdetails_div_switch_id").html(response[i].switch_id);
            $(".switchdetails_div_desc").html(response[i].description);   
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
	        
	      
	        if(response[i].status == "LIVE") {
	        	$("#div_"+(i+1)).addClass('up-state');
	        } else {
	        	$("#div_"+(i+1)).addClass('down-state');
	        }
	         
	 }
	 
	 $('#switchdetails_div').show();
	 $('#portdetails_div').show();
}


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
	url = "portdetails#" + switchname +"#" +portData.port_name;
	window.location = url;
}

/* ]]> */