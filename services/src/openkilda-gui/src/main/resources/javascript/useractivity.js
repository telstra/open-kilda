/*<![CDATA[*/


	$(document).ready(function(){
		$('#fromFilterField').datetimepicker({
			  format:'Y/m/d H:i:s',
			  onSelect: function(dateText, inst){
			      $("#toFilterField").datetimepicker('option', 'minDate', dateText);
			   }
		});
		$('#toFilterField').datetimepicker({
			  format:'Y/m/d H:i:s',
			  onSelect: function(dateText, inst){
			      $("#fromFilterField").datetimepicker('option', 'maxDate', dateText);
			   }
		});
		initUserActivity();
		
	
	})
function atLeastOneFilterSelected(){
		var type = $("#typeFilterField").val();
		var username= $("#usernameFilterField").val();
		var from = $("#fromFilterField").val();
		var to = $("#toFilterField").val();
		var flag = false;
		if(type && type.length){
			flag = true;
		}
		if(username && username.length){
			flag = true;	
				}
		if(from!=''){
			flag = true;
		}
		if(to!=''){
			flag = true;
		}
	return flag;	
}
function setCurrentDate(id){
	$("#"+id).val(moment().format('YYYY/MM/DD HH:mm:ss')).trigger('change');
}
function updateFilter(filter,isMultiselect){
		
		if($("#"+filter+"FilterField").val()!=''){
			$("#"+filter+"FilterSelected").show();
		}else{
			$("#"+filter+"FilterSelected").hide();
		}
	  	if(atLeastOneFilterSelected()){
	  		if(isMultiselect){
	  			var fieldValue=$("#"+filter+"FilterField").select2('data');
	  			var selectedText = [];
	  			if(fieldValue.length){
	  				for(var i=0; i< fieldValue.length; i++){
	  					selectedText.push(fieldValue[i].text)
	  				}
	  			}
	  			$("#"+filter+"FilterSelected").find('.selectedValues').html(": "+selectedText.join(","))
	  		}else{
	  			var fieldValue=$("#"+filter+"FilterField").val();
	  			$("#"+filter+"FilterSelected").find('.selectedValues').html(": "+moment(new Date(fieldValue)).format("MMMM Do YYYY, h:mm:ss:ms"))
	  		}
			$("#selectedFilter").show();
		}else{
			$("#selectedFilter").hide();
		}
		
}
function removeFilter(filter,isMultiselect){
		$("#"+filter+"FilterSelected").hide();
		if(isMultiselect){
			$("#"+filter+"FilterField").val([]).trigger('change');
		}else{
			$("#"+filter+"FilterField").val("");
		}
		if(atLeastOneFilterSelected()){
			$("#selectedFilter").show();
		}else{
			$("#selectedFilter").hide();
		}
}
function submitFilter(){
	$('#useractivityTable tbody').empty();
	var type = $("#typeFilterField").val();
	var username= $("#usernameFilterField").val();
	var from = $("#fromFilterField").val();
	var to = $("#toFilterField").val();
	var url = '/useractivity/log';
	var replacement ="";
	 if(type.length || username.length || from !='' || to!=''){
		 url+="?";
		 var hasOtherField = false;
		 if(type.length){
			 url+='activity='+type[0]+"&";
			 hasOtherField = true;
		 }
		 if(username.length){
			 url+='userId='+username[0]+"&";
		 }
		 if(from!=''){
			 url+='startTime='+new Date(from).getTime()+"&";
		 }
		 if(to!=''){
			 url+='endTime='+new Date(to).getTime();
		 }
	 }
	 url = url.replace(/&$/,replacement);
	 $("#loading").css("display", "block");
	 common.getData(url,"GET").then(function(response) {
			$("#loading").css("display", "none");
			$('body').css('pointer-events','all'); 
			showUserActivities(response)
		},function(error){
			response=[]
			var errorData = error.responseJSON;
			common.infoMessage(errorData['error-auxiliary-message'],'error');
			$("#loading").css("display", "none");
			$('body').css('pointer-events','all'); 
			showUserActivities(response)
		})
}
function initUserActivity(){
		$("#loading").css("display", "block");
		getTypes().then(function(types){
			var typesOptions = '<option value="">Select Type</option>';
			if(types && types.length > 0){
				for(var i=0; i < types.length; i++){
					typesOptions +='<option value="'+types[i].id+'">'+types[i].name+'</option>';
				}
			}
			$('#typeFilterField').append(typesOptions);
			$('#typeFilterField').select2({
				 width: "100%",
                 placeholder: "Select type"
			})
			getUsers().then(function(users){
				var userOptions = '<option value="">Select Username</option>';
				if(users && users.length > 0){
					for(var i=0; i < users.length; i++){
						userOptions +='<option value="'+users[i].user_id+'">'+users[i].user_name+'</option>';
					}
				}
				$('#usernameFilterField').append(userOptions);
				$('#usernameFilterField').select2({
					 width: "100%",
	                 placeholder: "Select Username"
				})
				useractivities();
			});
			
		})
	
}
function getUsers(){
		return common.getData("/user","GET");
}
function getTypes(){
	return  common.getData("/useractivity/types","GET");
}
function useractivities(){
$('#useractivityTable tbody').empty();
	common.getData("/useractivity/log","GET").then(function(response) {
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all'); 
		showUserActivities(response)
	},function(error){
		response=[]
		$("#loading").css("display", "none");
		$('body').css('pointer-events','all'); 
		var errorData = error.responseJSON;
		common.infoMessage(errorData['error-auxiliary-message'],'error');
		showUserActivities(response)
	})
}
function showUserActivities(data){
	if(!data || data.length==0) {
		data=[]
		$('#useractivityTable tbody').empty();
		common.infoMessage('No User Activity Available','info');
	}
	data = data.sort(function(a,b){
		return b.activityTime - a.activityTime
	})
	 for(var i = 0; i < data.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"'>"
		 				+"<td  title ='"+((data[i].activityTime === "" || data[i].activityTime == undefined)?"-":moment(new Date(data[i].activityTime)).format("MMMM Do YYYY, h:mm:ss:ms"))+"'>"+((data[i].activityTime === "" || data[i].activityTime == undefined)?"-":moment(new Date(data[i].activityTime)).format("MMMM Do YYYY, h:mm:ss:ms"))+"</td>"
		 				+"<td title ='"+((data[i].clientIpAddress === "" || data[i].clientIpAddress == undefined)?"-":data[i].clientIpAddress)+"'>"+((data[i].clientIpAddress === "" || data[i].clientIpAddress == undefined)?"-":data[i].clientIpAddress)+"</td>"
		 			    +"<td  title ='"+((data[i].userId === "" || data[i].userId == undefined)?"-":data[i].userId)+"'>"+((data[i].userId === "" || data[i].userId == undefined)?"-":data[i].userId)+"</td>"
		 			    +"<td  title ='"+((data[i].activityType === "" || data[i].activityType == undefined)?"-":data[i].activityType)+"'>"+((data[i].activityType === "" || data[i].activityType == undefined)?"-":data[i].activityType)+"</td>"
		 			    +"<td  title ='"+((data[i].objectId ==="" || data[i].objectId == undefined)?"-":data[i].objectId)+"'>"+((data[i].objectId === "" || data[i].objectId == undefined)?"-":data[i].objectId)+"</td>"
		 			    +"</tr>";
		$("#useractivity-details").append(tableRow);
		
		
	 }
 
	 $('#useractivityTable').show();
}

/* ]]> */