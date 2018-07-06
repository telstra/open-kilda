class Flow {
	submitFlow(){
		var data = $('#flowForm').serializeArray();
		var formData =[];
		if(data && data.length){
			$.each(data,function(){
				formData[this.name] = this.value;
			})
		}else{
			common.infoMessage("Please fill all the fields",'error');
			return false;
		}
		if(common.validateFormData(data)){
			var flowData ={
					"source":{
						"switch-id":formData['source_switch'],
						"port-id":formData['source_port'],
						"vlan-id":formData['source_vlan']
							
					},
					"destination":{
						"switch-id":formData['target_switch'],
						"port-id":formData['target_port'],
						"vlan-id":formData['target_vlan']
					},
					"flowid":formData['flowname'],
					"maximum-bandwidth":formData['max_bandwidth'],
					"description":formData['flow_description'],
			}
			common.saveData('/flows','PUT',flowData).then(function(data){
				common.infoMessage("Flow created successfully",'success');
				storage.remove('FLOWS_LIST');
				setTimeout(function(){window.location =APP_CONTEXT+"/flows/details#" + data.flowid;},700);
			}).fail(function(error){
				common.infoMessage(error.responseJSON['error-auxiliary-message'],'error');
			})
		 
		}
		return false;
	}
	
	 checkValidate(id){
		var val =$("#"+id).val();
		 var ifRequired=$('#'+id).attr('required');
		if(typeof(ifRequired)!=='undefined' && val!='' && typeof(val)!='undefined'){
			$('#'+id+"Error").hide();
			 $('#'+id).removeClass("errorInput");
		}else{
			$('#'+id+"Error").show();
			$('#'+id).addClass("errorInput");
		}
	}
	isNumberOnly(evt) {
	    evt = (evt) ? evt : window.event;
	    var charCode = (evt.which) ? evt.which : evt.keyCode;
	    if (charCode > 31 && (charCode < 48 || charCode > 57)) {
	        return false;
	    }
	    return true;
	}
		
}

var flowObj = new Flow();
