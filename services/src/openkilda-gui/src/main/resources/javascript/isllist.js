/*<![CDATA[*/


$(document).ready(function(){
		
	common.getData("/switch/links","GET").then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response); 
	},
	function(error){
		response=[];
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response);
	})
	
	$(document).on("click",".flowDataRow",function(e){
		setFlowData(this);
	})
	
	localStorage.clear();
})

var event;
$( 'input').on( 'click', function () {
	if(event != "undefined"){
		event.stopPropagation();
	}
});

function showflowData(response){
	
	if(!response || response.length==0) {
		response=[]
		common.infoMessage('No ISL Available','info');
	}
	
	var flowDetailsData = localStorage.getItem("flowDetailsData");
	var obj = JSON.parse(flowDetailsData)
	
	 for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 			    +"<td class='divTableCell' title ='"+((response[i].source_switch === "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"'>"+((response[i].source_switch === "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"'>"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"'>"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].target_switch === "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"'>"+((response[i].target_switch === "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"'>"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"'>"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].available_bandwidth === "" || response[i].available_bandwidth == undefined)?"-":response[i].available_bandwidth/1000 +" Mbps")+"'> "+ ((response[i].available_bandwidth === "" || response[i].available_bandwidth == undefined)?"-":response[i].available_bandwidth/1000 + " Mbps")+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"'>"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].speed === "" || response[i].speed == undefined)?"-":response[i].speed/1000 +" Mbps")+"'> "+ ((response[i].speed === "" || response[i].speed == undefined)?"-":response[i].speed/1000 + " Mbps")+"</td>"+"<td class='divTableCell' title ='"+((response[i].latency === "" || response[i].latency == undefined)?"-":response[i].latency)+"'>"+((response[i].latency === "" || response[i].latency == undefined)?"-":response[i].latency)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].unidirectional === "" || response[i].unidirectional == undefined)?"-":response[i].unidirectional)+"'>"+((response[i].unidirectional === "" || response[i].unidirectional == undefined)?"-":response[i].unidirectional)+"</td>"
		 			    +"</tr>";
		 
 			  $("#flowTable").append(tableRow);
 			  
 			  if (response[i].unidirectional || response[i].state && response[i].state.toLowerCase()== "failed"){
 				  $("#div_"+(i+1)).addClass('down-state');
	          } else {
	        	$("#div_"+(i+1)).addClass('up-state');
	          }
	 }
	 
	 var tableVar  =  $('#flowTable').DataTable( {
		 "iDisplayLength": 10,
		 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
		  "responsive": true,
		  "bSortCellsTop": true,
		  "autoWidth": false,
		  language: {searchPlaceholder: "Search"},
		  "aoColumns": [
		                { sWidth: '14%' },
		                { sWidth:  '8%' },
		                { sWidth: '12%' },
		                { sWidth: '14%' },
		                { sWidth: '8%' },
		                { sWidth: '12%' },
		                { sWidth: '12%' },
		                { sWidth: '12%' },
		                { sWidth: '12%' },
		                { sWidth: '8%' },
		                { sWidth: '8%' }]
	 });
	 
	 tableVar.columns().every( function () {

		 
	 var that = this;
	 $( 'input', this.header() ).on( 'keyup change', function () {
	      if ( that.search() !== this.value ) {
	             that.search(this.value).draw();
	         }
	     } );
	 } );
	 
	 $('#flowTable').show();
	
	 if(window.location.hash.substr(1)){
		 
		 var switchInfo = (window.location.hash.substr(1)).split("|");		 
		 $('#sourceIcon').trigger('click');
		 var input = $("#source-switch");
		 input.val(switchInfo[0]).trigger($.Event("keyup", { keyCode: 13 }));
		 
		 $('#targetIcon').trigger('click');
		 var input = $("#target-switch");
		 input.val(switchInfo[1]).trigger($.Event("keyup", { keyCode: 13 }));
	 }
}


function setFlowData(domObj){
	
	$(domObj).html()
	var flowData = {'source_switch':"",'src_port':"",'source_switch_name':"",'target_switch':"",'dst_port':"",'target_switch_name':"",'available_bandwidth':"",'speed':"",'state':"",'latency':"",'unidirectional':""};
	if($(domObj).find('td:nth-child(1)')){
		flowData.source_switch = $(domObj).find('td:nth-child(1)').html();
	}	
	if($(domObj).find('td:nth-child(2)')){
		flowData.src_port = $(domObj).find('td:nth-child(2)').html();
	}	
	if($(domObj).find('td:nth-child(3)')){
		flowData.source_switch_name = $(domObj).find('td:nth-child(3)').html();
	}	
	if($(domObj).find('td:nth-child(4)')){
		flowData.target_switch = $(domObj).find('td:nth-child(4)').html();
	}	
	if($(domObj).find('td:nth-child(5)')){
		flowData.dst_port = $(domObj).find('td:nth-child(5)').html();
	}	
	if($(domObj).find('td:nth-child(6)')){
		flowData.target_switch_name = $(domObj).find('td:nth-child(6)').html();
	}	
	if($(domObj).find('td:nth-child(7)')){
		flowData.available_bandwidth = $(domObj).find('td:nth-child(7)').html();
	}	
	if($(domObj).find('td:nth-child(8)')){
		flowData.state = $(domObj).find('td:nth-child(8)').html();
	}
	if($(domObj).find('td:nth-child(9)')){
		flowData.speed = $(domObj).find('td:nth-child(9)').html();
	}
	if($(domObj).find('td:nth-child(10)')){
		flowData.latency = $(domObj).find('td:nth-child(10)').html();
	}
	if($(domObj).find('td:nth-child(11)')){
		flowData.unidirectional = $(domObj).find('td:nth-child(11)').html();
	}
	localStorage.setItem("linkData", JSON.stringify(flowData));
	url = 'isl';
	window.location = url;
}


function showSearch(idname,$event) {
	$event.stopPropagation();
	if($('#'+idname+'.heading_search_box').is(":visible")){
		$('#'+idname+'.heading_search_box').css('display', 'none');
	}else{
		$('#'+idname+'.heading_search_box').css('display', 'inline-block');
	}
}


/* ]]> */