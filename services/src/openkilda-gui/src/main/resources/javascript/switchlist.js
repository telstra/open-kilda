/*<![CDATA[*/


$(document).ready(function(){
		common.getData("/switch/list","GET").then(function(response) {
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showSwitchData(response);
	},function(error){
		response=[]
		$("#wait1").css("display", "none");
		$('body').css('pointer-events','all'); 
		showSwitchData(response);
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

function showSwitchData(response){

	if(!response || response.length==0) {
		response=[]
		common.infoMessage('No Switch Available','info');
	}
	
	
	 for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 				+"<td class='divTableCell' title ='"+((response[i].name === "" || response[i].name == undefined)?"-":response[i].name)+"'>"+((response[i].name === "" || response[i].name == undefined)?"-":response[i].name)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].switch_id === "" || response[i].switch_id == undefined)?"-":response[i].switch_id)+"'>"+((response[i].switch_id === "" || response[i].switch_id == undefined)?"-":response[i].switch_id)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].address === "" || response[i].address == undefined)?"-":response[i].address)+"'>"+((response[i].address === "" || response[i].address == undefined)?"-":response[i].address)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].hostname === "" || response[i].hostname == undefined)?"-":response[i].hostname)+"'>"+((response[i].hostname === "" || response[i].hostname == undefined)?"-":response[i].hostname)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].description ==="" || response[i].description == undefined)?"-":response[i].description)+"'>"+((response[i].description === "" || response[i].description == undefined)?"-":response[i].description)+"</td>"
		 			    +"</tr>";
		
		$("#flowTable").append(tableRow);
	 }
	 
	 var tableVar  =  $('#flowTable').DataTable( {
		 "iDisplayLength": 10,
		 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
		  "responsive": true,
		  "bSortCellsTop": true,
		   language: {searchPlaceholder: "Search"},
		  "autoWidth": false,
		  "aoColumns": [
		                { sWidth: '15%' },
		                { sWidth: '15%' },
		                { sWidth: '15%' },
		                { sWidth: '25%' },
		                { sWidth: '30%' } ]
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


function setFlowData(domObj) {
	
	$(domObj).html()
	
	var flowData = {'name':"",'switch_id':"",'address':"",'hostname':"",'description':""};
	
	if($(domObj).find('td:nth-child(1)').html()){
		flowData.name = $(domObj).find('td:nth-child(1)').html();
	}
	if($(domObj).find('td:nth-child(2)').html()){
		flowData.switch_id = $(domObj).find('td:nth-child(2)').html();
	}
	if($(domObj).find('td:nth-child(3)')){
		flowData.address = $(domObj).find('td:nth-child(3)').html();
	}	
	if($(domObj).find('td:nth-child(4)')){
		flowData.hostname= $(domObj).find('td:nth-child(4)').html();
	}	
	if($(domObj).find('td:nth-child(5)')){
		flowData.description = $(domObj).find('td:nth-child(5)').html();
	}
	localStorage.setItem('switchDetailsJSON',JSON.stringify(flowData));
	window.location = "switch/details#"+"id#"+ flowData.switch_id;
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