<%@ page language="java" contentType="text/html; charset=ISO-8859-1"
    pageEncoding="ISO-8859-1"%>
<!DOCTYPE html PUBLIC "-//W3C//DTD HTML 4.01 Transitional//EN" "http://www.w3.org/TR/html4/loose.dtd">
<html>
<head>
<meta http-equiv="Content-Type" content="text/html; charset=ISO-8859-1">
<title>OPEN KILDA</title>
<script src="<%=request.getContextPath()%>/lib/javascript/jquery-3.2.1.min.js"></script>
<script src="<%=request.getContextPath()%>/lib/javascript/bootstrap.min.js"></script>
<link href="<%=request.getContextPath()%>/lib/css/bootstrap.min.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/css/custom.css" rel="stylesheet"></link>
<link href="<%=request.getContextPath()%>/ui/images/kilda.png" rel="shortcut icon" type="image/png"></link>
<style>
html, body {
	height: 100%;
	width: 100%;
}
</style>
</head>
<body>
<div class="login-wrapper">
  <div class="login">
  	<div class="col-lg-12">
      <form role="form" method="POST" action="authenticate">
        <h1 class="text-center">OPEN KILDA</h1>
        <fieldset>
            <div class="form-group">
                <input type="name" name="username" id="username" class="form-control input-lg" placeholder="Email" autofocus="true" required="true"></input>
            </div>
            <div class="form-group">
                <input type="password" name="password" id="password" class="form-control input-lg" placeholder="Password" required="true"></input>
            </div>
            <span class="button-checkbox">
                <input type="submit" class="btn btn-md btn-primary btn-submit" value="Login"></input>
            </span>
            <span class="login_error" th:text="${error}"></span>
        </fieldset>
    </form>
    </div>
  </div>
</div>
<script>
function getErrorFromUrl(){
	var error = '';
	var url = window.location.href;
	var urlQuerystring = url.split("?");
	 if(typeof(urlQuerystring[1])!=='undefined'){
		 var errorString =urlQuerystring[1].split("=");	
		 if(typeof(errorString[1])!='undefined'){
			 error = decodeURIComponent(errorString[1]).replace(/\+/g,' ');
		 }
	 }
	 return error;
}
		$(document).ready(function() { 
			$('body').css('pointer-events','all');
			 var error = getErrorFromUrl();
			 if(typeof(error)!=='undefined'){
				 $('.login_error').html(error);	 
			 }
			 
			
			 
		});		
	</script>
</body>
</html>