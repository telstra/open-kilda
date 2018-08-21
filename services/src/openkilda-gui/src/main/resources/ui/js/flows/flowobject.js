class Flow {
	constructor () {
		this.isEdit = false;
	}
	
	getFlow () {
		return this.flow;
	}
	
	
	setFlow (flow) {
		this.flow = flow;
	}
	
	getPorts (id) {
		 var switch_id = common.toggleSwitchID($("#" + id).val());
		 var endDate = moment().utc().format("YYYY-MM-DD-HH:mm:ss");
		 var startDate = moment().utc().subtract(30,'minutes').format("YYYY-MM-DD-HH:mm:ss");
		 var downSample = "30s";
		 return common.getData('/stats/switchports/' + switch_id + '/'+ startDate +'/' + endDate + '/'+ downSample);
	}
	
	validateFlow () {
		var data = $('#flowForm').serializeArray();
		var formData =[];
		
		if(data && data.length) {
			$.each(data,function() {
				formData[this.name] = this.value;
			})
		} else {
			common.infoMessage("Please fill all the fields",'error');
			return false;
		}
		
		if(common.validateFormData(data)) {
			if(this.isEdit){
				this.editFlowConfirm();
			}else{
				this.createFlowConfirm();
			}
			
		}
		return false;
	}
	
	
	createFlow () {
		$("#createflowconfirmModal").modal('hide');
		var data = $('#flowForm').serializeArray();
		var formData =[];
		$.each(data,function() {
			formData[this.name] = this.value;
		})
		var flowData = {
				"source": {
					"switch-id":formData['source_switch'],
					"port-id":formData['source_port'],
					"vlan-id":formData['source_vlan']
						
				},
				"destination": {
					"switch-id":formData['target_switch'],
					"port-id":formData['target_port'],
					"vlan-id":formData['target_vlan']
				},
				"flowid":formData['flowname'],
				"maximum-bandwidth":formData['max_bandwidth'],
				"description":formData['flow_description']
			}
			
			$('#saveflowloader').show();
			common.saveData('/flows', 'PUT', flowData).then( function(data) {
				common.infoMessage("Flow created successfully", 'success');
				storage.remove('FLOWS_LIST');
				setTimeout(function(){ 
					$('#saveflowloader').hide();
					window.location = APP_CONTEXT+"/flows/details#" + data.flowid;
				}, 500);
			}).fail(function(error) {
				$('#saveflowloader').hide();
				common.infoMessage(error.responseJSON['error-auxiliary-message'],'error');
			})
	}
	
	
	createNewFlow () {
		
		
		$('#addflowloader').show();
		common.getData('/switch/list').then(function(switches){
			if(switches && switches.length){
				var options =[];
				for(var i=0; i<switches.length; i++){
					var switch_state = switches[i].state;
						options.push({id:switches[i].switch_id,text:switches[i].name+"("+switch_state.toLowerCase()+")"})
				}
				var vlanOptions ="<option value='0'>0</option>";
				for(var i=1; i<=4094; i++){
					vlanOptions+="<option value='"+i+"'>"+i+"</option>";
				}
				$('#addflowloader').hide();
				$('#switchdetails_div').hide();
				$('#breadcrum_flow').append("<li id='new_flow_crum'><i class='fa icon-double-angle-right'></i>New Flow</li>")
				$("#add_flow_div").show().load('ui/templates/flows/addflow.html',function(){
					$("#add_flow_div").find("#source_vlan").html(vlanOptions);
					$("#add_flow_div").find("#target_vlan").html(vlanOptions);
					$("#source_switch").select2({
						width: "100%",
		                data:options,
		                placeholder: "Please select a switch",
		                matcher: common.matchCustomFlow
		            }).on("select2:close", function (e) { flowObj.checkValidate('source_switch') });
				$("#target_switch").select2({
					width:"100%",
					data:options,
					placeholder:"Please select a switch",
					matcher: common.matchCustomFlow
				}).on("select2:close", function (e) { flowObj.checkValidate('target_switch')});
				
			    $(document).on("change","#source_switch",function(e){
			        var portoptions = [];
			        flowObj.getPorts("source_switch").then(function(ports){
			        	ports = ports.sort(function(a,b){ return a.port_number - b.port_number});
			  		  for(var i=0; i<ports.length; i++){
			  			   var port = ports[i];
			  			   	portoptions.push({id:port.port_number,text:port.port_number});
			  			  }
				  		 $("#source_port").select2({
					         width:"100%",
					         data:portoptions,
					         placeholder:"Please select a port",
					         matcher: common.matchCustomFlow
					        }).on("select2:close", function (e) { flowObj.checkValidate('source_port')});
			  		 });
			       
			       });
			       
			       $(document).on("change","#target_switch",function(e){
			        var portoptions = [];
			        flowObj.getPorts("target_switch").then(function(ports){
			        	ports = ports.sort(function(a,b){ return a.port_number - b.port_number});
				  		  for(var i=0; i<ports.length; i++){
				  			   var port = ports[i];
				  			   	portoptions.push({id:port.port_number,text:port.port_number});
				  			  }
						  		$("#target_port").select2({
							         width:"100%",
							         data:portoptions,
							         placeholder:"Please select a port",
							         matcher: common.matchCustomFlow
							        }).on("select2:close", function (e) { flowObj.checkValidate('target_port')});
				  		 });
			        
			       });
			       
				})
				}else{
				$('#addflowloader').hide();
				common.infoMessage('No Switch Available','info');
			}
			
		}).fail(function(error){
			console.log("Error in fetching Switches:"+JSON.stringify(error))
			common.infoMessage('Error Fetching Switches','error');
			$('#addflowloader').hide();
		})
	}
	
	
	cancelCreateFlow (){
		
		$("#add_flow_div").empty().hide();
		$('#switchdetails_div').show();
		$('#breadcrum_flow').find('#new_flow_crum').remove();
		$(document).find("#flow-list").trigger('click');
		
	}
	
	createFlowConfirm () {
		$("#createflowconfirmModal").modal('show');
	}
	
	editFlowConfirm(){
		$("#editflowconfirmModal").modal('show');
		
	}
	cancelEditFlow () {
		$("#edit_flow_div").empty().hide();
		$('#flow_detail_div').show();
	}
	
	editFlow () {
		var flowData = this.getFlow();
		this.isEdit = true;
		$("#editflowconfirmModal").modal('hide');
		$('#editflowloader').show();
			common.getData('/switch/list').then(function(switches) {
				if(switches && switches.length) {
					var options = [];
					var selectedSourceSwitch = null;
					var selectedTargetSwitch = null;
					
					for(var i=0; i<switches.length; i++){
						var switch_state = switches[i].state;
						if(flowData.source['switch-id'] == switches[i].switch_id ) {
							selectedSourceSwitch = {
								id : switches[i].switch_id,
								text : switches[i].name + "(" + switch_state.toLowerCase() + ")"
							};
						}
						if(flowData.destination['switch-id'] == switches[i].switch_id ){
							selectedTargetSwitch = {
								id : switches[i].switch_id,
								text : switches[i].name + "(" + switch_state.toLowerCase() + ")"
							};
						}
						
						options.push({
							id : switches[i].switch_id,
							text : switches[i].name + "(" + switch_state.toLowerCase() + ")"
						});
					}
					var vlanOptions ="<option value='0'>0</option>";
					
					for( var i = 1; i <= 4094; i++) {
						vlanOptions += "<option value='" + i + "'>" + i + "</option>";
					}

					$('#editflowloader').hide();
					$('#flow_detail_div').hide();
					$("#edit_flow_div").show().load('../ui/templates/flows/editflow.html',function(){
						$("#edit_flow_div").find("#source_vlan").html(vlanOptions).val(flowData.source['vlan-id']);
						$("#edit_flow_div").find("#target_vlan").html(vlanOptions).val(flowData.destination['vlan-id']);
						$("#edit_flow_div").find("#flowname").val(flowData.flowid);
						$("#edit_flow_div").find("#flowname_read").val(flowData.flowid);
						$("#edit_flow_div").find("#flow_description").val(flowData.description);
						$("#edit_flow_div").find("#max_bandwidth").val(flowData['maximum-bandwidth']);
						
						$("#source_switch").select2({
							width: "100%",
							data:options,
							placeholder: "Please select a switch",
							matcher: common.matchCustomFlow
						}).on("select2:close", function (e) { flowObj.checkValidate('source_switch') });
						
						$("#target_switch").select2({
							width:"100%",
							data:options,
							placeholder:"Please select a switch",
							matcher: common.matchCustomFlow
						}).on("select2:close", function (e) { flowObj.checkValidate('target_switch')});
						
					    $(document).on("change","#source_switch",function(e){
					        var portoptions = []; 
					        	flowObj.getPorts("source_switch").then(function(ports){
					        		ports = ports.sort(function(a,b){ return a.port_number - b.port_number});
							  		  for(var i=0; i<ports.length; i++){
							  			   var port = ports[i];
							  			   	portoptions.push({id:port.port_number,text:port.port_number});
							  			  }
							  		 $("#source_port").select2({
								         width:"100%",
								         data:portoptions,
								         placeholder:"Please select a port",
								         matcher: common.matchCustomFlow
								        }).on("select2:close", function (e) { flowObj.checkValidate('source_port')});
							  		 $("#source_port").val(flowData.source['port-id']).trigger('change');
						  		 });
					       });
					       
					       $(document).on("change","#target_switch",function(e){
					        var portoptions =[];
					        flowObj.getPorts("target_switch").then(function(ports){
					        	ports = ports.sort(function(a,b){ return a.port_number - b.port_number});
						  		  for(var i=0; i<ports.length; i++){
						  			   var port = ports[i];
						  			   	portoptions.push({id:port.port_number,text:port.port_number});
						  			  }
						  		 $("#target_port").select2({
							         width:"100%",
							         data:portoptions,
							         placeholder:"Please select a port",
							         matcher: common.matchCustomFlow
							        }).on("select2:close", function (e) { flowObj.checkValidate('target_port')});
								$("#target_port").val(flowData.destination['port-id']).trigger('change');
					  		 });
					       
					       });

						$('#source_switch').val(selectedSourceSwitch.id).trigger('change');
						$("#target_switch").val(selectedTargetSwitch.id).trigger('change');
						
					})
				} else {
					$('#editflowloader').hide();
					common.infoMessage('No Switch Available','info');
				}
			}).fail(function(error){
				console.log("Error in fetching Switches:"+JSON.stringify(error))
				common.infoMessage('Error Fetching Switches','error');
				$('#editflowloader').hide();
			});
	}
	
	updateFlow () {
		$("#editflowconfirmModal").modal('hide');
		var data = $('#flowForm').serializeArray();
		var formData =[];
		$.each(data,function(){
				formData[this.name] = this.value;
			})
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
			$('#updateflowloader').show();
			common.updateData('/flows/'+flowData.flowid,'PUT',flowData).then(function(success){
				common.infoMessage("Flow updated successfully",'success');
				storage.remove('FLOWS_LIST');
				setTimeout(function(){
					$('#updateflowloader').hide();
					location.reload();
					//window.location.href = APP_CONTEXT+"/flows/details#" + flowData.flowid;
					},500)
			}).fail(function(error){
				$('#updateflowloader').hide();
				common.infoMessage(error.responseJSON['error-message'],'error');
			})
	}
	
	deleteFlowAlert () {
		$('#deleteflowconfirmModal').modal('show');
	}
	
	confirmFlowDelete () {
		$('#deleteflowconfirmModal').modal('hide');
		if(USER_SESSION.is2FaEnabled){
			this.focusNextInput();
			$("#twoFaOtpModal").modal();
		}else{
			$('#twofa_warning').modal('show');
		}
	}
	
	deleteFlow () {
		var otp = $("#twofacode").val();
		$('.error').hide();
		$('.otpdigit').removeClass("errorInput");
		if(otp=="" || otp == null){ 
			$('#codeOtpError').show();
			$('.otpdigit').val('').addClass("errorInput");
			return false;
		}
		var otpData ={"code":otp};
		var flowid = this.getFlow().flowid;
		$('#deleteFlowLoader').show();
		common.deleteData('/flows',flowid,otpData).then(function(success){
				$("#twoFaOtpModal").modal('hide');
				common.infoMessage('Flow deleted successfully','success');
				storage.remove('FLOWS_LIST');
				setTimeout(function(){
					$('#deleteFlowLoader').hide();
					var url = APP_CONTEXT+"/flows";
					window.location = url; 	
				},500)
				
			}).fail(function(error){
				$('#deleteFlowLoader').hide();
				$('.otpdigit').val('').addClass("errorInput");
				common.infoMessage(error.responseJSON['error-message'],'error');
		})
	}
	
	focusNextInput () {
		var container = document.getElementsByClassName("otp-container")[0];
		container.onkeyup = function(e) {
			var target = e.srcElement || e.target;
			var maxLength = parseInt(target.attributes["maxlength"].value, 10);
			var myLength = target.value.length;
			if (myLength >= maxLength) {
				var next = target;
				while (next = next.nextElementSibling) {
					if (next == null)
						break;
					if (next.tagName.toLowerCase() === "input") {
						next.focus();
						break;
					}
				}
			}
			// Move to previous field if empty (user pressed backspace)
			else if (myLength === 0) {
				var previous = target;
				while (previous = previous.previousElementSibling) {
					if (previous == null)
						break;
					if (previous.tagName.toLowerCase() === "input") {
						previous.focus();
						break;
					}
				}
			}
		}
	}
	
	
	validateOtpFragment (evt) {
	  	  var theEvent = evt || window.event;
	  	  var key = theEvent.keyCode || theEvent.which;
	  	  key = String.fromCharCode( key );
	  	  var regex = /[0-9]|\./;
	  	  if( !regex.test(key) ) {
	  		theEvent.returnValue = false;
	  		if(theEvent.preventDefault) theEvent.preventDefault();
	  	  }
  	}
	
	removeErrorOtp () {
		var otp = $('#twofacode').val();
		if(otp.trim()!=''){
			   $("#codeOtpError").hide();
				$('.otpdigit').removeClass("errorInput"); // Remove Error border
		}else{
			$('.error').hide();
			$("#codeOtpError").css('display','block');
			$('.otpdigit').addClass("errorInput"); // Add Error border
		}
	}
	
	assembleOtp () {
		var otpArr = [];
		var inputs = $(".otpdigit");
		for(var j=0;j<inputs.length; j++){
			if(inputs[j].value){
				otpArr.push(inputs[j].value)
			}
			
		}
		var otp = otpArr.join("");
		$('#twofacode').val(otp)
		
	}
	
	checkValidate (id) {
		var val = $("#" + id).val();
		var ifRequired = $('#' + id).attr('required');
		if( typeof(ifRequired) !== 'undefined' && val != '' && typeof(val) != 'undefined') {
			$('#' + id + "Error").hide();
			$('#' + id).removeClass("errorInput");
		} else {
			$('#' + id + "Error").show();
			$('#' + id).addClass("errorInput");
		}
	}
	
	IsAlphaNumeric (e) {
		e = (e) ? e : window.event;
		var specialKeys = new Array(8,9,46,36,35,37,39);
        var keyCode = e.keyCode == 0 ? e.charCode : e.keyCode;
        var ret = ((keyCode >= 48 && keyCode <= 57)  || (keyCode >= 65 && keyCode <= 90) || (keyCode >= 97 && keyCode <= 122) || (specialKeys.indexOf(e.keyCode) != -1 && e.charCode != e.keyCode));
        if(!ret){
        	$('#flownamepatternError').show();
			$('#flowname').addClass("errorInput");
        }else{
        	$('#flownamepatternError').hide();
			$('#flowname').removeClass("errorInput");
        }
        return ret;
    }
	
	
	isNumberOnly (evt) {
		evt = (evt) ? evt : window.event;
		var charCode = (evt.which) ? evt.which : evt.keyCode;
		if (charCode > 31 && (charCode < 48 || charCode > 57)) {
			return false;
		}
		return true;
	}
}

var flowObj = new Flow();