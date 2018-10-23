class StoreSetting {
	constructor () {
		this.identityDetailObj = {
			    "username": "",
			    "password": "",
			    "oauth-generate-token-url": {
			        "name": "oauth-generate-token",
			        "method-type": "POST",
			        "url": "",
			        "header": "",
			        "body": ""
			    },
			    "oauth-refresh-token-url": {
			        "name": "oauth-refresh-token",
			        "method-type": "POST",
			        "url": "",
			        "header": "",
			        "body": ""
			    }
			}
	    this.linkStoreObj  = {
	    	    "urls": {
	    	    	"get-all-link": {
	    	            "name": "get-all-link",
	    	            "method-type": "GET",
	    	            "url": "",
	    	            "header": "Content-Type:application/json",
	    	            "body": "{}"
	    	        },
	    	        "get-link": {
	    	            "name": "get-link",
	    	            "method-type": "GET",
	    	            "url": "",
	    	            "header": "Content-Type:application/json",
	    	            "body": "{}"
	    	        },
	    	        "get-contract": {
	    	            "name": "get-contract",
	    	            "method-type": "GET",
	    	            "url": "",
	    	            "header": "Content-Type:application/json",
	    	            "body": "{}"
	    	        },
	    	        "get-link-with-param": {
	    	            "name": "get-link-with-param",
	    	            "method-type": "GET",
	    	            "url": "",
	    	            "header": "Content-Type:application/json",
	    	            "body": "{[]}"
	    	        },
	    	        "delete-contract": {
	    	            "name": "delete-contract",
	    	            "method-type": "DELETE",
	    	            "url": "",
	    	            "header": "Content-Type:application/json",
	    	            "body": "{}"
	    	        }
	    	    }
	    	}
		this.getIdentityServerConfigurations();
		this.getLinkStoreUrl();
	}

	getLinkStoreUrl() {
		common.getData('/url/store/LINK_STORE').then(function(response){ 
			if(response && response.length){
				for(var i=0; i < response.length; i++) { 
					 switch(response[i]){ 
							case 'get-link': 
								 common.getData('/url/params/'+response[i]).then(function(data) {
									var paramList = data.map(function(d){ return d['param-name']});
									$('#requiredParam_getlinkurl').html("<plaintext>"+paramList);
									$('#exampleGetLinkParam').html("<span>Required Query Params:</span><plaintext style='float: left;width: 100%;margin-top: 2px;margin-bottom: 10px;'>"+paramList)
								})	
								   break;
							 case 'get-link-with-param':
								 common.getData('/url/params/'+response[i]).then(function(data) {
									 var paramList = data.map(function(d){ return d['param-name']});
									 $('#requiredParam_getlinkwithparam').html("<plaintext>"+paramList);
									  $('#exampleGetLinkWithParam').html("<span>Required Query Params:</span><plaintext  style='float: left;width: 100%;margin-top: 2px;margin-bottom: 10px;'>"+paramList)
									})	
								   break;
							 case 'get-contract' : 
								 common.getData('/url/params/'+response[i]).then(function(data) {
									 	var paramList = data.map(function(d){ return d['param-name']})
									 	 $('#requiredParam_getcontracturl').html("<plaintext>"+paramList);
										$('#exampleGetContractParam').html("<span>Required Query Params:</span><plaintext style='float: left;width: 100%;margin-top: 2px;margin-bottom: 10px;'>"+paramList)
									})	
								 break;
							 case 'delete-contract' : 
								 common.getData('/url/params/'+response[i]).then(function(data) {
									 var paramList = data.map(function(d){ return d['param-name']})
									  $('#requiredParam_deletecontracturl').html("<plaintext>"+paramList);
									 $('#exampleDeleteContractParam').html("<span>Required Query Params:</span><plaintext style='float: left;width: 100%;margin-top: 2px;margin-bottom: 10px;'>"+paramList)
									})	
								 break;
					 }
				}
			}
		})
	}
	setIdentityServerConfigFlag(flag) {
		localStorage.setItem('identityServerConfigFlag',flag);
	}
	
	getIdentityServerConfigFlag(flag) {
		return localStorage.getItem('identityServerConfigFlag') || false;
	}
	getLinkStoreDetails() {
		$('#loading_config').show();
		common.getData('/store/link-store-config',"GET").then(function(data){
			$('#loading_config').hide();
			var JSONResponse = JSON.parse(JSON.stringify(data));
			if(JSONResponse && JSONResponse['urls'] && typeof(JSONResponse['urls']['get-link']) !='undefined' &&  typeof(JSONResponse['urls']['get-link']['url'])!='undefined'){
				storeSettingObj.linkStoreObj = JSONResponse;
				storeSettingObj.setLinkForm(JSONResponse);
				storeSettingObj.disableLinkStoreForm();
			}
		},function(error){
			$('#loading_config').hide();
		}).fail(function(error){
			$('#loading_config').hide();
		});
	}
	
	generateorRefreshToken(tokenUrl,postData){
		return $.ajax({url : tokenUrl,contentType:'application/x-www-form-urlencoded',type : 'POST',data:postData});
	}
	setIdentityForm(data) {
		$('#username').val((typeof(data['username']) !=='undefined') ? data['username'] : '');
		$('#password').val((typeof(data['password']) !=='undefined') ? data['password'] : '');
		$('#tokenurl').val((typeof(data['oauth-generate-token-url']['url']) !=='undefined') ? data['oauth-generate-token-url']['url'] : '');
		$('#refreshtokenurl').val((typeof(data['oauth-refresh-token-url']['url'])!=='undefined') ? data['oauth-refresh-token-url']['url'] : '');
	}
	setLinkForm(data) {
		$('#getalllinkurl').val(data['urls'] && data['urls']['get-all-link'] && (typeof(data['urls']['get-all-link']['url'])!='undefined') ? data['urls']['get-all-link']['url'] : '');
		$('#getlinkurl').val(data['urls']&& data['urls']['get-link'] && (typeof(data['urls']['get-link']['url'])!='undefined') ? data['urls']['get-link']['url'] : '');
		$('#getlinkwithparam').val( data['urls'] && data['urls']['get-link-with-param'] && (typeof(data['urls']['get-link-with-param']['url'])) ? data['urls']['get-link-with-param']['url'] : '');
		$('#getcontracturl').val(data['urls'] && data['urls']['get-contract'] && (typeof(data['urls']['get-contract']['url'])) ? data['urls']['get-contract']['url'] : '');
		$('#deletecontracturl').val(data['urls']  &&  data['urls']['delete-contract'] && (typeof(data['urls']['delete-contract']['url'])) ? data['urls']['delete-contract']['url'] : '');
	}
	getIdentityServerConfigurations(){
		$('#loading_config').show();
		common.getData('/auth/oauth-two-config',"GET").then(function(data){
			var jsonResponse =JSON.parse(JSON.stringify(data));
			if(jsonResponse && jsonResponse['oauth-generate-token-url'] && typeof(jsonResponse['oauth-generate-token-url']['url']) !== 'undefined' ){
				storeSettingObj.identityDetailObj = jsonResponse ;
				storeSettingObj.setIdentityForm(jsonResponse);
				$('#linkStoreTabHeader').show();
				storeSettingObj.setIdentityServerConfigFlag(true);
				storeSettingObj.disableIdentityForm();
			}
			$('#loading_config').hide();
		},function(error){
			storeSettingObj.setIdentityServerConfigFlag(false);
			$('#linkStoreTabHeader').hide();
			$('#loading_config').hide();
		}).fail(function(error){
			storeSettingObj.setIdentityServerConfigFlag(false);
			$('#linkStoreTabHeader').hide();
			$('#loading_config').hide();
		})
	}
	cancelIdentityForm() {
		storeSettingObj.setIdentityForm(this.identityDetailObj);
		storeSettingObj.disableIdentityForm();
	}
	cancelLinkStoreForm() {
		storeSettingObj.setLinkForm(this.linkStoreObj);
		storeSettingObj.disableLinkStoreForm();
	}
	disableIdentityForm(){
		$('#editidentityBtn').show();
		$('#cancelidentityBtn').hide();
		$('#submitidentityBtn').hide();
		$('#username').attr('disabled','disabled');
		$('#password').attr('disabled','disabled');
		$('#tokenurl').attr('disabled','disabled');
		$('#refreshtokenurl').attr('disabled','disabled');
	}
	enableIdentityForm(){
		$('#editidentityBtn').hide();
		$('#cancelidentityBtn').show();
		$('#submitidentityBtn').show();
		$('#username').removeAttr('disabled');
		$('#password').removeAttr('disabled');
		$('#tokenurl').removeAttr('disabled');
		$('#refreshtokenurl').removeAttr('disabled');
		
	}
	disableLinkStoreForm(){
		$('#editlinkstoreBtn').show();
		$('#deletelinkstoreBtn').show();
		$('#cancellinkstoreBtn').hide();
		$('#submitlinkstoreBtn').hide();
		$('#getalllinkurl').attr('disabled','disabled');
		$('#getlinkurl').attr('disabled','disabled');
		$('#getlinkwithparam').attr('disabled','disabled');
		$('#getcontracturl').attr('disabled','disabled');
		$('#deletecontracturl').attr('disabled','disabled');
	}
	enableLinkStoreForm(){
		$('#editlinkstoreBtn').hide();
		$('#deletelinkstoreBtn').hide();
		$('#cancellinkstoreBtn').show();
		$('#submitlinkstoreBtn').show();
		$('#getalllinkurl').removeAttr('disabled');
		$('#getlinkurl').removeAttr('disabled');
		$('#getlinkwithparam').removeAttr('disabled');
		$('#getcontracturl').removeAttr('disabled');
		$('#deletecontracturl').removeAttr('disabled');
		
	}
	deleteLinkStoreConfirm(){
		$('#deletelinkStoreconfirmModal').modal('show');
	}
	deleteLinkStore(){
		$('#deletelinkStoreconfirmModal').modal('hide');
		$('#loading_delete_link_store').show();
		common.deleteData('/store/link-store-config/delete').then(function(response){
			$('#loading_delete_link_store').hide();
			common.infoMessage("Link store setting deleted successfully",'success');
			setTimeout(function(){
				location.reload();
			},500);
			
		},function(error){
			$('#loading_delete_link_store').hide();
			common.infoMessage(error.responseJSON['error-message'],'error');
		}).fail(function(error){
			$('#loading_delete_link_store').hide();
			common.infoMessage(error.responseJSON['error-message'],'error');
		})
	}
	generateFormFieldObject(formArr) {
		var formObj ={};
		if(formArr && formArr.length){ 
			for(var i=0; i < formArr.length; i++){ 
				formObj[formArr[i].name] = formArr[i].value;
			}
		}
		return formObj;
	}
	validateIdentityData(event) {
		 var identityForm = $("#identityServerForm");
		var valid = storeSettingObj.validateForm(identityForm);
		if(valid){
			 $('#loading_identity_details').show();
			var username = $('#username').val();
			var password = $('#password').val();
			var tokenUrl = $('#tokenurl').val();
			var refreshTokenUrl = $('#refreshtokenurl').val();
			var postData = {
					grapt_type:"password",
					username:username,
					password:password
			};
			var postData = decodeURIComponent("grant_type=password&username="+username+"&password="+password);
			storeSettingObj.generateorRefreshToken(tokenUrl,postData).then(function(response){
				var jsonResponse = JSON.parse(response);
				 if(jsonResponse && jsonResponse.access_token){
						var token = jsonResponse.access_token;
						var refresh_token = jsonResponse.refresh_token;
						var postDataForRefresh = decodeURIComponent("grant_type=refresh_token&refresh_token="+refresh_token);
						storeSettingObj.generateorRefreshToken(refreshTokenUrl,postDataForRefresh).then(function(response){
							storeSettingObj.submitIdentity(event);
							 $('#loading_identity_details').hide();
						},function(error){
							var err = JSON.parse(error.responseText);
							 $('#loading_identity_details').hide();
							 common.infoMessage(err['error_description'] || err['error-message'],'error');
						}).fail(function(error){
							var err = JSON.parse(error.responseText);
							 $('#loading_identity_details').hide();
							 common.infoMessage(err['error_description'] || err['error-message'],'error');
						});
				 }	
				},function(error){
					var err = JSON.parse(error.responseText);
					$('#loading_identity_details').hide();
					common.infoMessage(err['error_description'] || err['error-message'],'error');
			}).fail(function(error){
				 var err = JSON.parse(error.responseText);
				 $('#loading_identity_details').hide();
				 common.infoMessage(err['error_description'] || err['error-message'],'error');
			})
		}
		return false;
	}
	submitIdentity(e) {
			var identityForm = $("#identityServerForm");
			 if(storeSettingObj.validateForm(identityForm)){
				 $('#loading_identity_details').show();
				 localStorage.removeItem('IdentitySubmitEnabled');
				 var formData = JSON.stringify(identityForm.serializeArray());
				 var formObj = storeSettingObj.generateFormFieldObject(JSON.parse(formData));
				 var obj = storeSettingObj.identityDetailObj;
				 obj['username'] = formObj.username;
				 obj['password'] = formObj.password;
				 obj['oauth-generate-token-url']['url'] = formObj.tokenurl;
				 obj['oauth-refresh-token-url']['url'] = formObj.refreshtokenurl;
				 common.saveData('/auth/oauth-two-config/save', 'POST', obj).then( function(data) {
					 	var jsonResponse = JSON.parse(JSON.stringify(data));
					 	storeSettingObj.identityDetailObj = jsonResponse ;
						storeSettingObj.setIdentityForm(jsonResponse);
						common.infoMessage("Identity Server Details saved successfully", 'success');
						 $('#loading_identity_details').hide();
						$('#linkStoreTabHeader').show();
						storeSettingObj.setIdentityServerConfigFlag(true);
						 storeSettingObj.disableIdentityForm();
				},function(error){
					var err = error.responseJSON;
					common.infoMessage(err['error-auxiliary-message'],'error');
				}).fail(function(error) {
					var err = error.responseJSON;
					 $('#loading_identity_details').hide();
					common.infoMessage(err['error-auxiliary-message'],'error');
				})
			 }
		return false;
	}
	
	submitLinkData(e) {
		 var linkDataForm = $("#linkStoreForm");
		 if(storeSettingObj.validateForm(linkDataForm)){
			 $('#loading_link_store_details').show();
			 var formData = JSON.stringify(linkDataForm.serializeArray());
			 var formObj = storeSettingObj.generateFormFieldObject(JSON.parse(formData));
			 var Obj = this.linkStoreObj;
			 	Obj['urls']['get-all-link']['url'] = formObj['getalllinkurl'];
			 	Obj['urls']['get-link']['url'] = formObj['getlinkurl'];
			 	Obj['urls']['get-link-with-param']['url'] = formObj['getlinkwithparam'];
			 	Obj['urls']['get-contract']['url'] = formObj['getcontracturl'];
			 	Obj['urls']['delete-contract']['url'] = formObj['deletecontracturl'];
			 	common.saveData('/store/link-store-config/save', 'POST', Obj).then( function(data) {
			 		var JSONResponse = JSON.parse(JSON.stringify(data));
			 		storeSettingObj.linkStoreObj = JSONResponse;
					storeSettingObj.setLinkForm(JSONResponse);
		 		 common.infoMessage("Link store details saved successfully", 'success');
					 $('#loading_link_store_details').hide();
					 storeSettingObj.setIdentityServerConfigFlag(true);
					 storeSettingObj.disableLinkStoreForm();
			 	},function(error){
			 		var err = error.responseJSON;
			 		common.infoMessage(err['error-auxiliary-message'],'error');
			 	}).fail(function(error) {
			 		var err = error.responseJSON;
			 		$('#loading_link_store_details').hide();
			 		common.infoMessage(err['error-auxiliary-message'],'error');
			 	})
		 }
		return false;
	}
	validateForm(form) {
		var formValues = form.serializeArray();
		var validatedForm = true;
		for(var i = 0; i < formValues.length; i++){ 
			if(!storeSettingObj.validateField(formValues[i].name)){
				validatedForm = false;
			}
		}
		return validatedForm;
	}
	validateUrl(url) {
		return true;
		var res = url.match(/(http(s)?:\/\/.)?(www\.)?[-a-zA-Z0-9@:%._\+~#=]{2,256}\.[a-z]{2,6}\b([-a-zA-Z0-9@:%_\+.~#?&//=]*)/g);
	    if(res == null)
	        return false;
	    else
	        return true;
	}
	validateUrlParams(url,requiredParams) {
		var return_flag = true;
		var params = requiredParams.split(",");
		for(var i=0; i < params.length; i++){
			if(url.includes(params[i])){
				return_flag = true;
			}else{
				return_flag = false;
				break;
			}
		}
			
		return return_flag;
	}
	
	validateField(id) {
		var elmVal = $('#'+id).val();
		var ifUrlField = $('#'+id).attr('rel') == 'urltext';
		var requiredParams = $('#requiredParam_'+id).find("plaintext").html();
		if(elmVal != '' && typeof(elmVal) != 'undefined'){
			 	$('#' + id + "Error").hide();
				$('#' + id).removeClass("errorInput");
				if(ifUrlField && !storeSettingObj.validateUrl(elmVal)){
					$('#' + id + "ErrorUrl").show();
					$('#' + id).addClass("errorInput");
					return false;
				}else{
					if(typeof(requiredParams) !='undefined'){
						if(!storeSettingObj.validateUrlParams(elmVal,requiredParams)){
							$('#' + id + "requiredError").show();
							$('#' + id).addClass("errorInput");
							return false;
						}else{
							$('#' + id + "requiredError").hide();
							$('#' + id).removeClass("errorInput");
							return true;
						}
					}else{
						$('#' + id + "ErrorUrl").hide();
						$('#' + id).removeClass("errorInput");
						return true;
					}
					
				}
		}else{
			$('#' + id + "Error").show();
			$('#' + id).addClass("errorInput");
			return false;
		}
	}
	
}

var storeSettingObj = new StoreSetting();