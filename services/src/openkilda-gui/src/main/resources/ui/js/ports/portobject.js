class Port {
	constructor() {
	}
	
	getPortDetailObj() {
		var switchname = window.location.href.split("#")[1]	;
		var port_number = window.location.href.split("#")[2];
		var portData = localStorage.getItem('port_'+common.toggleSwitchID(switchname)+"_"+port_number);
		return JSON.parse(portData);
	}
	
	
	setPortDetails(portData) {
		var switchname = window.location.href.split("#")[1]	;
		var port_number = window.location.href.split("#")[2];
		localStorage.setItem('port_'+common.toggleSwitchID(switchname)+"_"+port_number,JSON.stringify(portData));
	 }
	
	configurePort() {
		var portData = this.getPortDetailObj();
		var switchDetail = JSON.parse(localStorage.getItem('switchDetailsJSON'));
		var switch_id = switchDetail.switch_id;
		var newStatus = $('#edit_port_status option:selected').val();
		$('#final_configure_confirm_modal').modal('hide');
		var url = '/switch/'+switch_id+"/"+portData.port_number+"/config";
		$('#port_detail_loading').show();
		
		common.saveData(url, 'PUT', {status:newStatus}).then( function(data) {
			common.infoMessage("Port configuration updated requested successfully", 'success');
			portData.status = newStatus;
			portObj.setPortDetails(portData);
			$('#port_detail_loading').hide();
			portObj.closeConfigurationPort(newStatus);
		}).fail(function(error) {
			$('#port_detail_loading').hide();
			common.infoMessage(error.responseJSON['error-auxiliary-message'],'error');
		})
	}
	
	cancelConfigurePort() {
		portObj.closeConfigurationPort();
	}
	
	closeConfigurationPort(newStatus) {
		if(typeof(newStatus)!=='undefined') {
			$('.port_details_div_status').text(newStatus).show();
		} else {
			$('.port_details_div_status').show();
		}
		$('#edit_port_status').hide();
		$('#save_configure_port').hide();
		$('#cancel_configure_port').hide();
		$('#configure_port').removeClass('hidePermission');
		$('#configure_port').addClass('showPermission');
	}
	configureConfirmation() {
		$('#configure_confirm_modal').modal('show');
	}
	
	cancelConfigure() {
		$('#configure_confirm_modal').modal('hide');
	}
	
	confirmConfigure() {
			var portData = this.getPortDetailObj();
			var port_status = (portData && portData.status) ? portData.status: '';
			$('#edit_port_status').val(port_status);
			$('.port_details_div_status').hide();
			$('#edit_port_status').show();
			$('#configure_confirm_modal').modal('hide');
			$('#cancel_configure_port').show();
			$('#save_configure_port').show();
			$('#configure_port').removeClass('showPermission');
			$('#configure_port').addClass('hidePermission');
	}
	
	confirmConfigurePort() {
		var oldData = this.getPortDetailObj();
		var newStatus = $('#edit_port_status option:selected').val();
		if(newStatus == oldData.status) {
			common.infoMessage('Nothing is changed','info');
		} else if(newStatus == '' || newStatus == null || typeof(newStatus) == 'undefined') {
			common.infoMessage('Status can not be empty','error');
		} else {
			$('#final_configure_confirm_modal').modal('show');
			$('#old_status_val').text(oldData.status);
			$("#new_status_val").text(newStatus);
		}
	}
	
	cancelConfirmConfigurePort() {
		$('#final_configure_confirm_modal').modal('hide');
		 portObj.closeConfigurationPort();
	}
}

var portObj = new Port();