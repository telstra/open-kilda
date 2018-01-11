/*<![CDATA[*/


$(document).ready(function(){
		
	$.ajax({
		url : APP_CONTEXT+"/switch"+"/flows",
		type : 'GET',
		success : function(response) {
			
			$("#wait1").css("display", "none");
			$('body').css('pointer-events','all'); 
			showflowData(response);  
		},
		
		dataType : "json"
	});
	
	$(document).on("click",".flowDataRow",function(e){
		setFlowData(this);
	})
})



function showflowData(response){
		
	if(response.flows.length==0) {
		$.toast({
		    heading: 'Flows',
		    text: 'No Data Avaliable',
		    showHideTransition: 'fade',
		    position: 'top-right',
			hideAfter : 6000,
		    icon: 'warning'
		})
	}
	
	//return false;
	
	var tmp_obj =''; 
	var last_id = '1';
	var last_html = '';
	var tmp_html = '';
	

	 for(var i = 0; i < response.flows.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].flowid+"'>"+response.flows[i].flowid+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].source_switch+"'>"+response.flows[i].source_switch+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].src_port+"'>"+response.flows[i].src_port+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].src_vlan+"'>"+response.flows[i].src_vlan+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].target_switch+"'>"+response.flows[i].target_switch+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].dst_port+"'>"+response.flows[i].dst_port+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].dst_vlan+"'>"+response.flows[i].dst_vlan+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].maximum_bandwidth+"'> "+response.flows[i].maximum_bandwidth+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].status+"'>"+response.flows[i].status+"</td>"
		 			    +"<td class='divTableCell' title ='"+response.flows[i].description+"'>"+((response.flows[i].description == "")?"-":response.flows[i].description)+"</td>"
		 			    +"</tr>";
		
	
		 			   $("#flowTable").append(tableRow);
		 			   
		 			   if(response.flows[i].status == "UP" || response.flows[i].status == "ALLOCATED") {
		 		        	$("#div_"+(i+1)).addClass('up-state');
		 		        } else {
		 		        	$("#div_"+(i+1)).addClass('down-state');
		 		        }
		 
	 }
	 
	 var tableVar  =  $('#flowTable').DataTable( {
		 "iDisplayLength": 5,
		 "aLengthMenu": [[5, 10, 25, 50, -1], [5, 10, 25, 50, "All"]],
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
	url = "flowdetails#" + flowData.flow_id;
	window.location = url;
}


function showSearch(idname,$event) {
		
	
	$event.stopPropagation()
	
	if($('#'+idname+'.heading_search_box').is(":visible")){
		$('#'+idname+'.heading_search_box').css('display', 'none');
	}else{
		$('#'+idname+'.heading_search_box').css('display', 'inline-block');
	}
	//return false;
}


/* ]]> */