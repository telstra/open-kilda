$(document).ready(function(){
	focusNextInput();
	var key= $('#secretKey').text();
	var username= $('#uname').text();
	$('#qr_code_text').text(key);
    $('#qrCode').attr('src', 'https://chart.googleapis.com/chart?chs=200x200&cht=qr&chl=200x200&chld=M|0&cht=qr&' 
    		+ 'chl=' + encodeURIComponent('otpauth://totp/' + encodeURIComponent('Open Kilda') + ":" + username + '?secret=' + key + '&issuer=' + encodeURIComponent('Open Kilda')));
});

function focusNextInput(){
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
