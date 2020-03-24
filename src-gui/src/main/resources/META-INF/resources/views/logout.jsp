<html>

<head>
<link href="<%=request.getContextPath()%>/lib/css/bootstrap.min.css" rel="stylesheet" type="text/css" />
<meta http-equiv="Content-Type" content="text/html; charset=US-ASCII"/>
<title text="#{label.pages.home.title}">OPEN KILDA</title>
<script src="<%=request.getContextPath()%>/lib/javascript/jquery-3.2.1.min.js"></script>
<link href="<%=request.getContextPath()%>/ui/images/kilda.png" rel="shortcut icon" type="image/png"></link>
	<script>
		
		$(document).ready(function() { 
			 var error = getErrorFromUrl();
			 if(typeof(error)!=='undefined'){
				 $('.login_error').html(error);	 
			 }
			 
			
			 
		});		
	</script>	
</head>

<body>
    <h1 style="color:blue"> Logout!!!!!!!!!!!!!!!</h1>
        <form action="login" method="post">
            <input type="submit" value="Login Again"/>
        </form>
</body>

</html>