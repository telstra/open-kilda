<!doctype html>
<html xmlns="http://www.w3.org/1999/xhtml"
   xmlns:sec="http://www.thymeleaf.org/thymeleaf-extras-springsecurity3">
   <head>
      <meta charset="utf-8">
      </meta>
      <title>OPEN KILDA</title>
      <!-- CSS Style Sheets -->
<script src="<%=request.getContextPath()%>/lib/javascript/jquery-3.2.1.min.js"></script>
<script src="<%=request.getContextPath()%>/lib/javascript/bootstrap.min.js"></script>
<link href="<%=request.getContextPath()%>/lib/css/bootstrap.min.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/css/custom.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/images/kilda.png" rel="shortcut icon" type="image/png"></link>
   </head>
   <body>
      <div class="login-wrapper">
			
		<div class="login">
			<div class="col-lg-12">
				<form name="otpForm" method="POST" action="authenticate" onSubmit="return validateTwoFaOtp()">
					<h1 class="text-center">Open Kilda</h1>
					<h2 class="text-center">Two-factor authentication</h2>
					<input type="hidden" name="username" id="username" value="${username}" style="visibility:hidden;"/>
					<input type="hidden" name="password" id="password" value="${password}" style="visibility:hidden;"/>
								
					<div class="form-group">
						<label for="otp">Enter the authentication code <span class="mandatory-text text-danger">*</span></label>
						 <!-- <div class="">
							<input name="code" id="code" type="password" maxlength="6" class="form-control partitioned" placeholder="OTP" onkeyup="removeError(this);" 
							autocomplete="off" autofocus="true"/>
							
						</div>--> 
						 <div class="barcode-bg">					
						<div class="otp otp-container">
						<input name="code" id="code" type="hidden" maxlength="6"  placeholder="OTP"  />
						<input  autofocus="true" class="form-control otpdigit" placeholder="*" maxlength="1" onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();" />
						<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
						<input class="form-control otpdigit" placeholder="*" maxlength="1" onkeypress="validateOtpFragment()"  onkeyup="assembleOtp();removeErrorOtp();"/>
						<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
						<input class="form-control otpdigit" placeholder="*" maxlength="1"  onkeypress="validateOtpFragment()" onkeyup="assembleOtp();removeErrorOtp();"/>
						<input class="form-control otpdigit" placeholder="*" maxlength="1" onkeypress="validateOtpFragment()"  onkeyup="assembleOtp();removeErrorOtp();"/> 
						</div>    
					</div> 
						<span id="codeError" style="display:none; color:red;">Authentication code is required.</span>
						<span id="otpError" class="error" style="color:red;">${error}</span>
					</div>
					<div class="form-group">
						<button type="submit" class="btn btn-md btn-primary btn-submit">Verify</button>
					</div>		
					<p class="icons-phone">Open the two-factor authentication app on your device to view your authentication code and verify your identity.</p>	
				</form>
			</div>
		</div>
	</div>
      <script type="text/javascript" src="<%=request.getContextPath()%>/ui/js/common.js"></script>
      		<script>
		$(document).ready(function(){
			focusNextInput();
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

			  if(theEvent.keyCode == 13){
				validateTwoFaOtp();
				return; 
			  }

			  key = String.fromCharCode( key );
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