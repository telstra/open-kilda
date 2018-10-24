/*<![CDATA[*/

var storage = new LocalStorageHandler();
	
$(document).ready(function(){
	common.getData('/store/link-store-config',"GET").then(function(data){
		var JSONResponse = JSON.parse(JSON.stringify(data));
		if(JSONResponse && JSONResponse['urls'] && typeof(JSONResponse['urls']['get-link']) !='undefined' &&  typeof(JSONResponse['urls']['get-link']['url'])!='undefined'){
			localStorage.setItem('linkStoreSetting',JSONResponse);
			localStorage.setItem('haslinkStoreSetting',true);
			$('#activeFilter').prop('checked',true);
		}
	},function(error){
	}).fail(function(error){
	});
	if(localStorage.getItem('flowListDisplay')){
		localStorage.removeItem('flowListDisplay')
		$(document).find("#flow-list").trigger('click');
		var FLOWS_LIST = storage.get('FLOWS_LIST');
		if(FLOWS_LIST){
			showflowData(FLOWS_LIST);
		}else{
			flows();
		}
	}
	$(document).on("click","#flow-list",function(e){ 
		var FLOWS_LIST = storage.get('FLOWS_LIST');
		if(FLOWS_LIST){
			showflowData(FLOWS_LIST);
		}else{
			var hasStoreSetting = localStorage.getItem('haslinkStoreSetting');
			var filters = '';
			if(typeof(hasStoreSetting)!='undefined' && typeof(hasStoreSetting)!=null && hasStoreSetting == "true"){
				filters="active";
			}
			flows(filters);
		}
	});
	$('#flowid').keyup(function(e){
		if(e.keyCode === 13){
			validateFlowForm();
		}
	});
	$(document).on("click","#refresh_list",function(e){
		var hasStoreSetting = localStorage.getItem('haslinkStoreSetting');
		var filters = '';
		if(typeof(hasStoreSetting)!='undefined' && typeof(hasStoreSetting)!=null && hasStoreSetting == "true"){
		    filters = $('input[name="filterStatus[]"]:checked').map(function(){
				return $(this).val();
			}).get().join();
			
		}
		$('input[type="search"]').each(function(index){  
	        var input = $(this);
	        input.val("");
	    });
		window.location.hash = "";
		storage.remove('FLOWS_LIST');
		flows(filters);
		
	});
	
	$(document).on("dblclick",".flowDataRow",function(e){
		setFlowData(this);
	})
	$('input[name="filterStatus[]"]').on('click',function(){
		var filters = $('input[name="filterStatus[]"]:checked').map(function(){
			return $(this).val();
		}).get().join();
		flows(filters);
	})
	var hash = window.location.hash;
	if (hash.indexOf('|') > -1){
		//hash && $('ul.nav a[href="' + hash + '"]').tab('show');
		$('ul.nav a[href="#2a"]').tab('show');
		$("#flow-list").trigger("click");
	}  
	//localStorage.clear();
})

var event;
$( 'input').on( 'click', function () {
	if(event != "undefined"){
		event.stopPropagation();
	}
});


function flows(filters){
	$("#loading").css("display", "block");
	var hasStoreSetting = localStorage.getItem('haslinkStoreSetting');
	var query  = '';
	if(typeof(hasStoreSetting)!='undefined' && typeof(hasStoreSetting)!=null && hasStoreSetting == "true"){
		if(typeof(filters)!='undefined'){
			if(filters!=''){
				 query = '?status='+filters;
				}
		}else{
			var filters = $('input[name="filterStatus[]"]:checked').map(function(){
				return $(this).val();
			}).get().join();
			if(filters!=''){
				 query = '?status='+filters;
			}
		}
	}
	
	common.getData("/flows/list"+query,"GET").then(function(response) {
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response);
		storage.set("FLOWS_LIST",response);
	},
	function(error){
		response=[];
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all'); 
		showflowData(response);
	});
}
function validateFlowForm(){
	var flowid=$('#flowid').val();
    if(flowid.length == 0){
    	$("#flowid").addClass("has-error");	
		$(".flowid-error-message").html("Please enter flow id.");
		$("#searchbtn").css("top","-6px");
		return false;
    }
    else {
    	$("#flowid").removeClass("has-error");
    	$(".flowid-error-message").html("");
    	$("#searchbtn").css("top","0px");
    }
	window.location.href= APP_CONTEXT + "/flows/details#"+flowid;
}
function goToflowDetail(flowid,isEdit){
	if(isEdit){
		localStorage.setItem("flowEdit_"+flowid,true);
	}
	var url = "flows/details#" + flowid;
	window.location = url;
}
function showflowData(response){
	var hasStoreSetting = localStorage.getItem('haslinkStoreSetting');
	if(typeof(hasStoreSetting)!='undefined' && typeof(hasStoreSetting)!=null && hasStoreSetting == "true"){
		$('#storeFilter').show();
	}else{
		$('#storeFilter').hide();
	}
	if ( $.fn.DataTable.isDataTable('#flowTable') ) {
		  $('#flowTable').DataTable().destroy();
		}
	if(!response || response.length==0) {
		response=[]
		common.infoMessage('No Flow Available','info');
	}else{
		$('#flowTable tbody').empty();
	}
	
	 for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
		 			    +"<td class='divTableCell' title ='"+((response[i].flowid === "" || response[i].flowid == undefined)?"-":response[i].flowid)+"'>"+((response[i].flowid === "" || response[i].flowid == undefined)?"-":response[i].flowid)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"'>"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"'>"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].src_vlan === "" || response[i].src_vlan === undefined)?"-":response[i].src_vlan)+"'>"+ ((response[i].src_vlan === "" || response[i].src_vlan == undefined)?"-":response[i].src_vlan)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"'>"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"'>"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].dst_vlan === "" || response[i].dst_vlan == undefined)?"-":response[i].dst_vlan)+"'>"+((response[i].dst_vlan === "" || response[i].dst_vlan == undefined)?"-":response[i].dst_vlan)+"</td>"
		 			    //+"<td class='divTableCell' title ='"+((response[i].maximum_bandwidth === "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth/1000 +" Mbps")+"'> "+ ((response[i].maximum_bandwidth === "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth/1000 + " Mbps")+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].maximum_bandwidth === "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth/1000)+"'> "+ ((response[i].maximum_bandwidth === "" || response[i].maximum_bandwidth == undefined)?"-":response[i].maximum_bandwidth/1000)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].status === "" || response[i].status == undefined)?"-":response[i].status)+"'>"+((response[i].status === "" || response[i].status == undefined)?"-":response[i].status)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"'>"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"</td>"
		 			    +"<td class='divTableCell' title ='"+((response[i].description === "" || response[i].description == undefined)?"-":response[i].description)+"'>"+((response[i].description === "" || response[i].description == undefined)?"-":response[i].description)+"</td>"
		 			   +"<td class='divTableCell' title ='action'>";
					   if(USER_SESSION != "" && USER_SESSION != undefined) {
								var userPermissions = USER_SESSION.permissions;
								if(userPermissions.includes("fw_flow_update")){
									if(typeof(hasStoreSetting)!='undefined' && typeof(hasStoreSetting)!=null && hasStoreSetting == "true"){
										if(response[i]['discrepancy'] && !response[i]['discrepancy']['controller-discrepancy']){
											tableRow+="<i class='icon icon-edit' title='edit flow' onClick=goToflowDetail('"+response[i].flowid+"',true); style='margin-right:10px;'></i>";
									 	}else{
									 		tableRow+="";
										}
										
									}else{
										tableRow+="<i class='icon icon-edit' title='edit flow' onClick=goToflowDetail('"+response[i].flowid+"',true); style='margin-right:10px;'></i>";
						 			}
									
								}
						}
					   
		 			   $("#flowTable").append(tableRow);
		 			   if(response[i].status == "UP" || response[i].status == "ALLOCATED" || response[i].status == "CACHED") {
		 				   	$("#div_"+(i+1)).addClass('up-state');
		 		        } else {
		 		        	$("#div_"+(i+1)).addClass('down-state');
		 		        }
	 }
	 
	 common.customDataTableSorting();
	 
	 var tableVar  =  $('#flowTable').DataTable({
		 "iDisplayLength": 10,
		 "aLengthMenu": [[10, 20, 35, 50, -1], [10, 20, 35, 50, "All"]],
		  "responsive": true,
		  "bSortCellsTop": true,
		  "autoWidth": false,
		  language: {searchPlaceholder: "Search"},
		  "aaSorting": [[1, "asc"]],
		  "aoColumns": [
		                { sWidth: '15%' },
		                { sWidth:  '13%',"sType": "name","bSortable": true },
		                { sWidth: '8%' },
		                { sWidth: '9%' },
		                { sWidth: '13%',"sType": "name","bSortable": true },
		                { sWidth: '8%' },
		                { sWidth: '9%' },
		                { sWidth: '10%' },
		                { sWidth: '8%' },{ sWidth: '8%' },
		                { sWidth: '10%' },{ sWidth: '10%' } ]
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
		 input.val(decodeURIComponent(switchInfo[0])).trigger($.Event("keyup", { keyCode: 13 }));
		 
		 $('#targetIcon').trigger('click');
		 var input = $("#target-switch");
		 input.val(decodeURIComponent(switchInfo[1])).trigger($.Event("keyup", { keyCode: 13 }));
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