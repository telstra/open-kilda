<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>OPEN KILDA </title>
<script src="<%=request.getContextPath()%>/lib/javascript/jquery-3.2.1.min.js"></script>
<script src="<%=request.getContextPath()%>/lib/javascript/bootstrap.min.js"></script>
<link href="<%=request.getContextPath()%>/lib/css/bootstrap.min.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/css/custom.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/images/kilda.png" rel="shortcut icon" type="image/png"></link>
</head>
   <body class="twofa_body">
         <div class=" twofa-wrapper">
         	<div class="row">
         	<div class="col-lg-12">
         		<form name="twoFaForm"  method="POST" action="authenticate" onSubmit="return validateTwoFaOtp();">
         			<div class="row">         			
		                <div class="col-md-6 two-fa-link">
		                	<h2>Setting up Two-Factor Authentication (2FA) <i class="icon-login-info"></i></h2>
		                    <p>Follow the steps below to set up 2FA on Kilda.</p>
		                    <ul>
		                        <li>Download and install an authenticator app on your mobile device. There are many to choose from - including apps from <a href="https://play.google.com/store/apps/details?id=com.google.android.apps.authenticator2" target="_blank">Google</a> and <a href=" https://www.microsoft.com/en-au/store/p/microsoft-authenticator/9nblgggzmcj6" target="_blank">Microsoft</a>.</li>
		                        <li>Open your authentication app and add a new account.</li>
		                        <li>Link your mobile device to your account by scanning the QR code on your login screen.</li>
		                        <li>Select Next on login screen. You'll see a field requesting an authentication code.</li>
		                        <li>Enter authentication code from your mobile app and select Verify.</li>
		                    </ul>
		           </div>
		                <div class="col-md-6">
		                	<div class="QRcode-container change-password">
		                        <h2 class="center">Two-Factor Authentication</h2>
								<span id="secretKey" style="visibility:hidden;">${key}</span>  
								<span id="appName" style="visibility:hidden;">${applicationName}</span> 
		                        <span id="uname" style="visibility:hidden;">${username}</span>      
		                        <p>Scan below code with the authenticator app on your mobile device and follow instructions to verify your identity.</p>
		                      	 <div class="qr_scan_img">             
		                            <div class="form-group text-center">                            
		                             	<img alt="QrCode" id="qrCode" src="" />
		                            </div>      
		                            <div class="form-group">                            
		                             	<label for="qr_code">(Code : <span id="qr_code_text"> </span> )</label>		                             	
		                            </div>                       
									<input type="hidden" name="username" id="username" value="${username}" style="visibility:hidden;"/>
									<input type="hidden" name="password" id="password" value="${password}" style="visibility:hidden;"/>
									<input type="hidden" name="configure2Fa" id="configure2Fa" value="true" style="visibility:hidden;"/>
									<div class="form-group">
         								<label for="otp">Enter the authentication code <span class="mandatory-text text-danger">*</span></label>
       									<div class="barcode-bg">					
											<div class="otp otp-container">
												<input   name="code" id="code" type="hidden" maxlength="6"  placeholder="OTP"  />
												<input autofocus="true" class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();" />
												<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
												<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
												<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
												<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
												<input class="form-control otpdigit" placeholder="*" maxlength="1" onkeypress="validateOtpFragment()"  onkeyup="assembleOtp();removeErrorOtp();"/> 
											</div>    
										</div> 
										<input type="submit" class="btn btn-md kilda_btn btn-submit" value="Next" style="padding: 12px 32px;margin: 10px 0 0 15px;"/>
										<span id="codeError" class="error clearfix" style="display:none; color:red;">Authentication code is required.</span>
         								<span id="otpError" class="error clearfix" style="display:none; color:red;">Authentication code is invalid.</span>
	         							
	      							</div>
		                        </div>
		                    </div>
		                </div>
                	</div>
         		</form>
         	</div>
            </div>
         </div>
		<script type="text/javascript" src="ui/js/usermanagement/twofa.js"></script>
		<script>
		$(document).ready(function(){
			focusNextInput();
			var key= $('#secretKey').text();
			var username= $('#uname').text();
			var appName = $('#appName').text();
			$('#qr_code_text').text(key);
		    $('#qrCode').attr('src', 'https://chart.googleapis.com/chart?chs=200x200&cht=qr&chl=200x200&chld=M|0&cht=qr&' 
		    		+ 'chl=' + encodeURIComponent('otpauth://totp/' + encodeURIComponent(appName) + ":" + username + '?secret=' + key + '&issuer=' + encodeURIComponent(appName)));
		});

		function focusNextInput(){
			var container = document.getElementsByClassName("otp-container")[0];
			var input = container.getElementsByTagName('input')[0];
			input.focus();
			
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
		function validateTwoFaOtp(){
			var otp = document.twoFaForm.code.value;
			if(otp=="" || otp == null){
				$('#codeError').css('display','block');
				return false;
			}
			return true;
		}
		function validateOTP($event){
			$event.preventDefault(); 
			var otp = document.otpForm.code.value || document.twoFaForm.code.value;
			if(otp=="" || otp == null){
				$('#codeError').css('display','block');
				$('input[name="otp"').addClass("errorInput");
				return false;
			}
			
			var userData = {username,password,otp}
			$.ajax({url : './login',contentType:'application/json',dataType : "json",type : 'POST',data: JSON.stringify(userData)}).then(function(response){
		        
		        common.infoMessage('2FA Authenticated.','success');
			}, function(error){
				common.infoMessage(error.responseJSON['error-message'],'error');
			});			
			
		}
		//this to validation error on form
		function removeError(elem) {
		    var id = elem.name;
		    if ((elem.value).trim() != '') {
		        $("#" + id + "Error").hide();
		        $('input[name="'+id+'"').removeClass("errorInput"); // Remove Error border
		        $('textarea[name="'+id+'"').removeClass("errorInput"); // Remove Error border
		    } else {
		    	$('.error').hide();
		        $("#" + id + "Error").css('display','block');
		        $('input[name="'+id+'"').addClass("errorInput"); // Add Error border
		        $('textarea[name="'+id+'"').addClass("errorInput"); // Remove Error border
		    }
		}
		function validateOtpFragment(evt) {
			  var theEvent = evt || window.event;
			  var key = theEvent.keyCode || theEvent.which;
			  key = String.fromCharCode( key );
			  
			  if(theEvent.keyCode == 13){
					validateTwoFaOtp();
					return; 
			  }
			  
			  var regex = /[0-9]|\./;
			  if( !regex.test(key) ) {
			    theEvent.returnValue = false;
			    if(theEvent.preventDefault) theEvent.preventDefault();
			  }
			}
		function removeErrorOtp(){
			var otp = $('#code').val();
			if(otp.trim()!=''){
				   $("#codeError").hide();
			        $('input[name="code"').removeClass("errorInput"); // Remove Error border
			        $('textarea[name="code"').removeClass("errorInput"); // Remove Error border
			}else{
				$('.error').hide();
		        $("#codeError").css('display','block');
		        $('input[name="code"').addClass("errorInput"); // Add Error border
		        $('textarea[name="code"').addClass("errorInput"); // Remove Error border
			}
		}
		function assembleOtp(){
			var otpArr = [];
			var inputs = $(".otpdigit");
			for(var j=0;j<inputs.length; j++){
				if(inputs[j].value){
					otpArr.push(inputs[j].value)
				}
				
			}
			var otp = otpArr.join("");
			$('#code').val(otp)
			
		}

</script>
   </body>
</html>