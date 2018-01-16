/*<![CDATA[*/


$(document).ready(function(){
		
	$.ajax({
		url : APP_CONTEXT+"/switch/list",
		type : 'GET',
		success : function(response) {
			
			$("#wait1").css("display", "none");
			$('body').css('pointer-events','all'); 
			showSwitchData(response);  
		},
		
		dataType : "json"
	});
	
	$(document).on("click",".flowDataRow",function(e){
		setFlowData(this);
	})
	
})



function showSwitchData(response){
	if(response.length==0) {
		
		common.infoMessage('No Data Avaliable','info');
	}
		
	var tmp_obj =''; 
	var last_id = '1';
	var last_html = '';
	var tmp_html = '';
	

	 for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 			    +"<td class='divTableCell' title ='"+response[i].controller+"'>"+response[i].controller+"</td>"
		 			    +"<td class='divTableCell' title ='"+response[i].address+"'>"+response[i].address+"</td>"
		 			    +"<td class='divTableCell' title ='"+response[i].name+"'>"+response[i].name+"</td>"
		 			    +"<td class='divTableCell' title ='"+response[i].description+"'>"+response[i].description+"</td>"
		 			    +"</tr>";
		
	
		 			   $("#flowTable").append(tableRow);
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
	 
	 $('#flowTable').show();
}


function setFlowData(domObj){
	
	$(domObj).html()
	
	var flowData = {'controller':"",'address':"",'name':"",'description':""};
	
	if($(domObj).find('td:nth-child(1)').html()){
		flowData.controller = $(domObj).find('td:nth-child(1)').html();
	}

	if($(domObj).find('td:nth-child(2)')){
		flowData.address = $(domObj).find('td:nth-child(2)').html();
	}
	
	if($(domObj).find('td:nth-child(3)')){
		flowData.name= $(domObj).find('td:nth-child(3)').html();
	}
	
	if($(domObj).find('td:nth-child(4)')){
		flowData.description = $(domObj).find('td:nth-child(4)').html();
	}
	
	localStorage.setItem('switchDetailsJSON',JSON.stringify(flowData));
	window.location = "switch/details#"+"id#"+ flowData.name;
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