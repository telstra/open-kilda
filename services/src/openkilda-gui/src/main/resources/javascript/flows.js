/*<![CDATA[*/


$(document).ready(function(){
		
	
	common.getData("/flows/list","GET").then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response); 
	},
	function(error){
		response=[]
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response);
	})
	
	$(document).on("click",".flowDataRow",function(e){
		setFlowData(this);
	})
})



function showflowData(response){
		
	if(response.length==0) {
		common.infoMessage('No Flows Avaliable','info');
	}
	
	var flowDetailsData = localStorage.getItem("flowDetailsData");
	var obj = JSON.parse(flowDetailsData)
	
	 for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 			    +"<td class='divTableCell' title ='"+((response[i].flowid == "" || response[i].flowid == undefined)?"-":response[i].flowid)+"'>"+((response[i].flowid == "" || response[i].flowid == undefined)?"-":response[i].flowid)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].source_switch == "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"'>"+((response[i].source_switch == "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].src_port == "" || response[i].src_port == undefined)?"-":response[i].src_port)+"'>"+((response[i].src_port == "" || response[i].src_port == undefined)?"-":response[i].src_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].src_vlan == "" || response[i].src_vlan == undefined)?"-":response[i].src_vlan)+"'>"+ ((response[i].src_vlan == "" || response[i].src_vlan == undefined)?"-":response[i].src_vlan)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].target_switch == "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"'>"+((response[i].target_switch == "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].dst_port == "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"'>"+((response[i].dst_port == "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].dst_vlan == "" || response[i].dst_vlan == undefined)?"-":response[i].dst_vlan)+"'>"+((response[i].dst_vlan == "" || response[i].dst_vlan == undefined)?"-":response[i].dst_vlan)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].maximum_bandwidth == "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth)+"'> "+ ((response[i].maximum_bandwidth == "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].status == "" || response[i].status == undefined)?"-":response[i].status)+"'>"+((response[i].status == "" || response[i].status == undefined)?"-":response[i].status)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].description == "" || response[i].description == undefined)?"-":response[i].description)+"'>"+((response[i].description == "" || response[i].description == undefined)?"-":response[i].description)+"</td>"
		 			    +"</tr>";
		 
		 			   $("#flowTable").append(tableRow);
		 			   if(response[i].status == "UP" || response[i].status == "ALLOCATED") {
		 				   	$("#div_"+(i+1)).addClass('up-state');
		 		        } else {
		 		        	console.log(response[i].status)
		 		        	$("#div_"+(i+1)).addClass('down-state');
		 		        }
	 }
	 
	 var tableVar  =  $('#flowTable').DataTable( {
		 "iDisplayLength": 10,
		 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
		  "responsive": true,
		  "bSortCellsTop": true,
		  "autoWidth": false,
		  "aoColumns": [
		                { sWidth: '10%' },
		                { sWidth:  '15%' },
		                { sWidth: '8%' },
		                { sWidth: '8%' },
		                { sWidth: '14%' },
		                { sWidth: '9%' },
		                { sWidth: '9%' },
		                { sWidth: '9%' },
		                { sWidth: '8%' },
		                { sWidth: '10%' } ]
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
		 	$("#flowTable_filter").find('input').val(switchInfo[0]+' '+switchInfo[1]).trigger($.Event("keyup", { keyCode: 13 }));;
	 }
	
}


function setFlowData(domObj){
	
	$(domObj).html()
	var flowData = {'flowid':"",'source_switch':"",'src_port':"",'src_vlan':"",'target_switch':"",'dst_port':"",'dst_vlan':"",'maximum_bandwidth':"",'status':"",'description':""};
	if($(domObj).find('td:nth-child(1)').html()){
		flowData.flow_id = $(domObj).find('td:nth-child(1)').html();
	}
	if($(domObj).find('td:nth-child(2)')){
		flowData.source_switch = $(domObj).find('td:nth-child(2)').html();
	}	
	if($(domObj).find('td:nth-child(3)')){
		flowData.src_port = $(domObj).find('td:nth-child(3)').html();
	}	
	if($(domObj).find('td:nth-child(4)')){
		flowData.src_vlan = $(domObj).find('td:nth-child(4)').html();
	}	
	if($(domObj).find('td:nth-child(5)')){
		flowData.target_switch = $(domObj).find('td:nth-child(5)').html();
	}	
	if($(domObj).find('td:nth-child(6)')){
		flowData.dst_port = $(domObj).find('td:nth-child(6)').html();
	}	
	if($(domObj).find('td:nth-child(7)')){
		flowData.dst_vlan = $(domObj).find('td:nth-child(7)').html();
	}	
	if($(domObj).find('td:nth-child(8)')){
		flowData.maximum_bandwidth = $(domObj).find('td:nth-child(8)').html();
	}	
	if($(domObj).find('td:nth-child(9)')){
		flowData.status = $(domObj).find('td:nth-child(9)').html();
	}
	if($(domObj).find('td:nth-child(10)')){
		flowData.description = $(domObj).find('td:nth-child(10)').html();
	}
	localStorage.setItem('flowDetails',JSON.stringify(flowData));
	url = "flows/details#" + flowData.flow_id;
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