/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/

/** show switch details when page is loaded or
 *  when user is redirected to this page*/
$(document).ready(function(){
		
	var switchData = localStorage.getItem("switchDetailsJSON");
	var switchname=window.location.href.split("#")[1]	
	
	if(switchname.includes("id")) {
		
		if(!switchData || switchData ==""){
			window.location = APP_CONTEXT+ "/switch";
		}
		var switchname=window.location.href.split("#")[2];
		var tmp_anchor = '<a href="/openkilda/switch">' + "Switch" + '</a>';
		$("#kilda-nav-label").parent().append(tmp_anchor)
		$("#topology-menu-id").removeClass("active");
		$("#switch-menu-id").addClass("active");

	} else {	
		if(!switchData || switchData ==""){
			window.location =  APP_CONTEXT+ "/topology";
		}
		var tmp_anchor = '<a href="/openkilda/topology">' + "Topology" + '</a>';
		$("#kilda-nav-label").parent().append(tmp_anchor)	
		$("#switch-menu-id").removeClass("active");
		$("#topology-menu-id").addClass("active");
	}
	
	$("#kilda-switch-name").parent().append(switchname)	
	var obj = JSON.parse(switchData);
	
	showSwitchData(obj); 
	callPortDetailsAPI(switchname);
	$(document).on("click",".flowDataRow",function(e){
		setPortData(switchname,this);
	})
	
	  localStorage.removeItem("portDetails");
})

/** function to retrieve and show port details*/
 function callPortDetailsAPI(switchname){
	
	common.getData("/switch/"+switchname+"/ports","GET").then(function(response) { 
		
		$('body').css('pointer-events','all'); 	
		showPortData(response);
	},
	function(error){
		response=[]
		$('body').css('pointer-events','all'); 
		showPortData(response);
	})
}

/** function to retrieve and show switch details from 
 * the switch response json object and display on the html page*/
function showSwitchData(response){
	
	$(".switchdetails_div_name").html(response.name);
	$(".switchdetails_div_hostname").html(response.hostname);
    $(".switchdetails_div_address").html(response.address);
    $(".switchdetails_div_switch_id").html(response.switch_id);
    $(".switchdetails_div_desc").html(response.description);  
}

var event;
$( 'input').on( 'click', function () {
	if(event != "undefined"){
		event.stopPropagation();
	}
});

/** function to retrieve and show port details from 
 * the port response json object and display on the html page*/
function showPortData(response) {

	if(!response || response.length==0) {
		response=[]
		common.infoMessage('No Ports Avaliable','info');
	}
	
	$("#flowTable #div_loader").remove();
	
		for(var i = 0; i < response.length; i++) {
			 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
			 			    +"<td class='divTableCell' title ='"+((response[i].interfacetype == undefined)?"-":response[i].interfacetype)+"'>"+((response[i].interfacetype === "" || response[i].interfacetype == undefined)?"-":response[i].interfacetype)+"</td>"
			 			    +"<td class='divTableCell' title ='"+((response[i].port_name == undefined)?"-":response[i].port_name)+"'>"+((response[i].port_name === "" || response[i].port_name == undefined)?"-":response[i].port_name)+"</td>"
			 			    +"<td class='divTableCell' title ='"+((response[i].port_number == undefined)?"-":response[i].port_number)+"'>"+((response[i].port_number === "" || response[i].port_number == undefined)?"-":response[i].port_number)+"</td>"
			 			    +"<td class='divTableCell' title ='"+((response[i].status == undefined)?"-":response[i].status)+"'>"+((response[i].status == undefined)?"-":response[i].status)+"</td>"
			 			    +"</tr>";
			 		 
			 
				$("#flowTable").append(tableRow);
			 			   
			 	if(response[i].status == "LIVE") {
			 		$("#div_"+(i+1)).addClass('up-state');
			 	} else {
			 		$("#div_"+(i+1)).addClass('down-state');
			 	}
		 }
		
		 var tableVar  =  $('#flowTable').DataTable( {
			 "iDisplayLength": 10,
			 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
			 "responsive": true,
			 "bSortCellsTop": true,
			 "autoWidth": false
		 });
		 
		 tableVar.columns().every( function () {		 
		 var that = this;
		 $( 'input', this.header() ).on( 'keyup change', function () {
		      if ( that.search() !== this.value ) {
		             that.search(this.value).draw();
		         }
		     } );
		 } );
		 
		 $('#portdetails_div').show();
}


function setPortData(switchname,domObj){
	
	$(domObj).html()
	var portData = {'interface':"",'port_name':"",'port_number':"",'status':""};
	if($(domObj).find('td:nth-child(1)')){
		portData.interface = $(domObj).find('td:nth-child(1)').html();
	}
	if($(domObj).find('td:nth-child(2)')){
		portData.port_name = $(domObj).find('td:nth-child(2)').html();
	}	
	if($(domObj).find('td:nth-child(3)')){
		portData.port_number = $(domObj).find('td:nth-child(3)').html();
	}	
	if($(domObj).find('td:nth-child(4)')){
		portData.status = $(domObj).find('td:nth-child(4)').html();
	}
	localStorage.setItem('portDetails',JSON.stringify(portData));
	url = "portdetails#" + switchname +"#" +portData.port_name;
	window.location = url;
}

function showSearch(idname,$event) {
	$event.stopPropagation()
	if($('#'+idname+'.heading_search_box').is(":visible")){
		$('#'+idname+'.heading_search_box').css('display', 'none');
	}else{
		$('#'+idname+'.heading_search_box').css('display', 'inline-block');
	}
}

/* ]]> */