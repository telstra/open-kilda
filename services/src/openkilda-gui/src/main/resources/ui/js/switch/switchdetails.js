/*<![CDATA[*/

/** Below the javascript/ajax/jquery code to generate and display the switch and its corresponding details*/

/** show switch details when page is loaded or
 *  when user is redirected to this page*/
 var obj = {};
 var PortTimeInterval;
$(document).ready(function(){
		
	var switchData = localStorage.getItem("switchDetailsJSON");
	var switchname=window.location.href.split("#")[1]	
	if(switchname.includes("id")) {
		
		if(!switchData || switchData ==""){
			window.location = APP_CONTEXT+ "/switch";
		}
		var switchname=window.location.href.split("#")[2];
		var tmp_anchor = '<a href="/openkilda/switch">' + "Switches" + '</a>';
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
	obj = JSON.parse(switchData);
	showSwitchData(obj); 
	callPortDetailsAPI(switchname,true);
	PortTimeInterval = setInterval(function(){
		callPortDetailsAPI(switchname,false);
	},30000);
  localStorage.removeItem("portDetails");
  
	$(document).on("click",".flowDataRow",function(e){
		  setPortData(switchname,this);
		 });
})

/** function to retrieve and show port details*/
 function callPortDetailsAPI(switchname,loader){
	 var switch_id =common.toggleSwitchID(switchname);
	 var endDate = moment().utc().format("YYYY-MM-DD-HH:mm:ss");
	 var startDate = moment().utc().subtract(30,'minutes').format("YYYY-MM-DD-HH:mm:ss");
	 var downSample = "30s";	
	 var url ='/stats/switchports/' + switch_id + '/'+ startDate +'/' + endDate + '/'+ downSample
	 if(loader){$('#port_loading').show();}
	 common.getData(url,"GET").then(function(response) { 
		$('body').css('pointer-events','all'); 	
		showPortData(response,loader);
	},function(error){
		var response=[]
		$('body').css('pointer-events','all'); 
		showPortData(response,loader);
	});
}

/** function to retrieve and show switch details from 
 * the switch response json object and display on the html page*/
function showSwitchData(response){
	
	$(".switchdetails_div_name").html(response.name);
	$(".switchdetails_div_hostname").html(response.hostname);
    $(".switchdetails_div_address").html(response.address);
    $(".switchdetails_div_switch_id").html(response.switch_id);
    $(".switchdetails_div_desc").html(response.description); 
    $(".switchdetails_div_state").html(response.state); 
}

var event;
$( 'input').on( 'click', function () {
	if(event != "undefined"){
		event.stopPropagation();
	}
});

/** function to retrieve and show port details from 
 * the port response json object and display on the html page*/
function showPortData(response,loader) {
	if ( $.fn.DataTable.isDataTable('#portsTable') ) {
		  $('#portsTable').DataTable().destroy();
		}
	
	if(!response || response.length==0) {
		response=[]
		if(loader){
			common.infoMessage('No Ports Available','info');
		}
		localStorage.removeItem('switchPortDetail');
	}else{
		localStorage.setItem('switchPortDetail',JSON.stringify(response));
		$('#portsTable tbody').empty();
	}
	if(loader){
		$("#port_loading").hide();
	}
	
	
		for(var i = 0; i < response.length; i++) {
			 var tableRow = "<tr rel='"+((response[i].port_number == undefined)?"-":response[i].port_number)+"'  id='div_"+(i+1)+"' class='flowDataRow'>"
			 				+"<td class='divTableCell' title ='"+((response[i].port_number == undefined)?"-":response[i].port_number)+"'><p>"+((response[i].port_number === "" || response[i].port_number == undefined)?"-":response[i].port_number)+"</p></td>"
			   				+"<td class='divTableCell' title ='"+((response[i].interfacetype == undefined)?"-":response[i].interfacetype)+"'><p>"+((response[i].interfacetype === "" || response[i].interfacetype == undefined)?"-":response[i].interfacetype)+"</p></td>"
			    		    +"<td class='divTableCell subPortTable' title =''><span title='"+((response[i].stats['tx-bytes'] == undefined)?"-":response[i].stats['tx-bytes'] * 1024)+"'>"+((response[i].stats['tx-bytes'] == undefined)?"-":response[i].stats['tx-bytes'] * 1024)+"</span><span title='"+((response[i].stats['rx-bytes'] == undefined)?"-":response[i].stats['rx-bytes'] * 1024)+"'>"+((response[i].stats['rx-bytes'] == undefined)?"-":response[i].stats['rx-bytes'] * 1024)+"</span></td>"
				 			+"<td class='divTableCell subPortTable' title =''><span title='"+((response[i].stats['tx-packets'] == undefined)?"-":response[i].stats['tx-packets'])+"'>"+((response[i].stats['tx-packets'] == undefined)?"-":response[i].stats['tx-packets'])+"</span><span title='"+((response[i].stats['rx-packets'] == undefined)?"-":response[i].stats['rx-packets'])+"'>"+((response[i].stats['rx-packets'] == undefined)?"-":response[i].stats['rx-packets'])+"</span></td>"
				 			+"<td class='divTableCell subPortTable' title =''><span title='"+((response[i].stats['tx-dropped'] == undefined)?"-":response[i].stats['tx-dropped'])+"'>"+((response[i].stats['tx-dropped'] == undefined)?"-":response[i].stats['tx-dropped'])+"</span><span title='"+((response[i].stats['rx-dropped'] == undefined)?"-":response[i].stats['rx-dropped'])+"'>"+((response[i].stats['rx-dropped'] == undefined)?"-":response[i].stats['rx-dropped'])+"</span></td>"
				 			+"<td class='divTableCell subPortTable' title =''><span title='"+((response[i].stats['tx-errors'] == undefined)?"-":response[i].stats['tx-errors'])+"'>"+((response[i].stats['tx-errors'] == undefined)?"-":response[i].stats['tx-errors'])+"</span><span title='"+((response[i].stats['rx-errors'] == undefined)?"-":response[i].stats['rx-errors'])+"'>"+((response[i].stats['rx-errors'] == undefined)?"-":response[i].stats['rx-errors'])+"</span></td>"
				 			+"<td class='divTableCell' title ='"+((response[i].stats["collisions"] == undefined)?"-":response[i].stats["collisions"])+"'><p>"+((response[i].stats["collisions"] == undefined)?"-":response[i].stats["collisions"])+"</p></td>"
				 			+"<td class='divTableCell' title ='"+((response[i].stats["rx-frame-error"] == undefined)?"-":response[i].stats["rx-frame-error"])+"'><p>"+((response[i].stats["rx-frame-error"] == undefined)?"-":response[i].stats["rx-frame-error"])+"</p></td>"
				 			+"<td class='divTableCell' title ='"+((response[i].stats["rx-over-error"] == undefined)?"-":response[i].stats["rx-over-error"])+"'><p>"+((response[i].stats["rx-over-error"] == undefined)?"-":response[i].stats["rx-over-error"])+"</p></td>"
				 			+"<td class='divTableCell' title ='"+((response[i].stats["rx-crc-error"] == undefined)?"-":response[i].stats["rx-crc-error"])+"'><p>"+((response[i].stats["rx-crc-error"] == undefined)?"-":response[i].stats["rx-crc-error"])+"</p></td>"
		 			        +"</tr>";
			 		 
			 
				$("#portsTable").append(tableRow);
			 			   
			 	if(response[i].status == "UP") {
			 		$("#div_"+(i+1)).addClass('up-status');
			 	} else {
			 		$("#div_"+(i+1)).addClass('down-status');
			 	}
		 }
		
		 var tableVar  =  $('#portsTable').DataTable( {
			 "iDisplayLength": 10,
			 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
			 "responsive": true,
			 "bPaginate":false,
			 "bSortCellsTop": true,
			  language: {searchPlaceholder: "Search"},
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
	var port_number = $(domObj).attr('rel');
	var portData = null;
	var port_data = JSON.parse(localStorage.getItem('switchPortDetail'));
	for(var i = 0; i < port_data.length ; i++){
		if(port_number == port_data[i].port_number){
			portData = port_data[i];
			break;
		}
	}
	if(portData){
		localStorage.setItem('port_'+common.toggleSwitchID(switchname)+"_"+portData.port_number,JSON.stringify(portData));
		url = "portdetails#" + switchname +"#" +portData.port_number;
		window.location = url;
	}
}

function showSearch(idname,$event) {
	$event.stopPropagation()
	if($('#'+idname+'.heading_search_box').is(":visible")){
		$('#'+idname+'.heading_search_box').css('display', 'none');
	}else{
		$('#'+idname+'.heading_search_box').css('display', 'inline-block');
	}
}

function callSwitchRules(switch_id){
	$('#switch_rules_loader').show();
	$('#rules_json').html("")
		common.getData("/switch/" + switch_id+"/rules","GET").then(function(response) { // calling re-route api
				var responseData = JSON.stringify(response,null,2);
				$('#rules_json').html(responseData)
				$('#switch_rules_loader').hide();
		})
}


$('#switch_rules_btn').click(function(e){
		e.preventDefault(); 
		callSwitchRules(obj.switch_id);
	});
/* ]]> */