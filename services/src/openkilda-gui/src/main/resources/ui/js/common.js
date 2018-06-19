/*<![CDATA[*/


/*Global Variable Constant*/
var metricVarList = ["bits:Bits/sec","bytes:Bytes/sec","packets:Packets/sec","drops:Drops/sec","errors:Errors/sec", "collisions:Collisions","frameerror:Frame Errors","overerror:Overruns","crcerror:CRC Errors"];

var href = window.location.href;
var lastslashindex = href.lastIndexOf('/');
var page = $.trim(href.substring(lastslashindex  + 1)).split("#")[0];

if(page == "isl") {
	var linkData = localStorage.getItem("linkData");
	var obj = JSON.parse(linkData);
	var srcSwitch = obj.source_switch;
	var srcPort = obj.src_port;
	var targetSwitch = obj.target_switch;
	var targetPort = obj.dst_port;
}

var graphIntervalISL;
var graphIntervalISLName;
var TopologyIntervalID;

var LocalStorageHandler = function() {

    /**
     * @property _ls
     * @private
     * @type Object
     */
    var _ls = window.localStorage;

    /**
     * @property length
     * @type Number
     */
    this.length = _ls.length;

    /**
     * @method get
     * @param key {String} Item key
     * @return {String|Object|Null}
     */
    this.get = function(key) {
        try {
            return JSON.parse(_ls.getItem(key));
        } catch(e) {
            return _ls.getItem(key);
        }
    };

    /**
     * @method set
     * @param key {String} Item key
     * @param val {String|Object} Item value
     * @return {String|Object} The value of the item just set
     */
    this.set = function(key, val) {
        _ls.setItem(key,JSON.stringify(val));
        return this.get(key);
    };

    /**
     * @method key
     * @param index {Number} Item index
     * @return {String|Null} The item key if found, null if not
     */
    this.key = function(index) {
        if (typeof index === 'number') {
            return _ls.key(index);
        }
    };

    /**
     * @method data
     * @return {Array|Null} An array containing all items in localStorage through key{string}-value{String|Object} pairs
     */
    this.data = function() {
        var i       = 0;
        var data    = [];

        while (_ls.key(i)) {
            data[i] = [_ls.key(i), this.get(_ls.key(i))];
            i++;
        }

        return data.length ? data : null;
    };

    /**
     * @method remove
     * @param keyOrIndex {String|Number} Item key or index (which will be converted to key anyway)
     * @return {Boolean} True if the key was found before deletion, false if not
     */
    this.remove = function(keyOrIndex) {
        var result = false;
        var key = (typeof keyOrIndex === 'number') ? this.key(keyOrIndex) : keyOrIndex;

        if (key in _ls) {
            result = true;
            _ls.removeItem(key);
        }

        return result;
    };

    /**
     * @method clear
     * @return {Number} The total of items removed
     */
    this.clear = function() {
        var len = _ls.length;
        _ls.clear();
        return len;
    };
}

var common = {	
		getData:function(apiUrl,requestType){	
			var hasQueryParams = apiUrl.split("?");
			if(hasQueryParams.length > 1){
				return $.ajax({url : APP_CONTEXT+apiUrl+"&_=" + new Date().getTime(),type : requestType,dataType : "json"});							
				
			}else{
				return $.ajax({url : APP_CONTEXT+apiUrl+"?_=" + new Date().getTime(),type : requestType,dataType : "json"});							
				
			}							
		},
		
		updateData:function(apiUrl,requestType,data){
			return $.ajax({url : APP_CONTEXT+apiUrl,contentType:'application/json',dataType : "json",type : requestType,data:JSON.stringify(data)});	
		},
		customDataTableSorting:function(){
			jQuery.fn.dataTableExt.oSort["name-desc"] = function (x, y) {
				 var xArr = x.split(".");
				 var yArr = y.split(".");
				 
				 var xa = xArr[xArr.length - 1];
				  var ya = yArr[yArr.length - 1];
				 
				
				   return ((xa < ya) ?  1 : ((xa > ya) ? -1 : 0));
			        
			};
			jQuery.fn.dataTableExt.oSort["name-asc"] = function (x, y) {
				var xArr = x.split(".");
				 var yArr = y.split(".");
				 var xa = xArr[xArr.length - 1];
				  var ya = yArr[yArr.length - 1];
				
		     return ((xa < ya) ? -1 : ((xa > ya) ?  1 : 0));
			}
		},
		infoMessage:function(msz,type){
			$.toast({heading:(type =='info'?'Information':type), text: msz, showHideTransition: 'fade',position: 'top-right', icon: type, hideAfter : 6000})
		},
		toggleSwitchID:function(switchid){
			var prefix = "SW";
			var switchPrefix = switchid.substring(0,2);
			if (prefix == switchPrefix){
				return this.addCharacter(switchid,2).join(':').substring(3).toLowerCase();
			}else{
				return prefix+switchid.replace(/:/g , "").toUpperCase();
			}
			
		},
		
		addCharacter: function(str, n) {
		    var ret = [];
		    
		    for(var i = 0, len = str.length; i < len; i += n) {
		       ret.push(str.substr(i, n))
		    }
		    return ret;
		},
		isNumeric: function(value) {
			return !isNaN(value) && (function(x) { return (x | 0) === x; })(parseFloat(value));
		},		
		validateNumber: function(event) {
		    var key = window.event ? event.keyCode : event.which;
		    if (event.keyCode === 8 || event.keyCode === 46) {
		        return true;
		    } else if ( key < 48 || key > 57 ) {
		        return false;
		    } else {
		    	return true;
		    }
		},
		groupBy: function(array , f)
		{
		  var groups = {};
		  array.forEach( function( o )
		  {
		    var group = JSON.stringify( f(o) );
		    groups[group] = groups[group] || [];
		    groups[group].push( o );  
		  });
		  return Object.keys(groups).map( function( group )
		  {
		    return groups[group]; 
		  })
		}
}
var storage = new LocalStorageHandler();

/** sub menu related code start **/
var urlString = window.location.href;
$("#menubar-tbn li").each(function(){
	$(this).removeClass();
})
function updateTopologyCoordinates(){
	var isDirtyCordinates =(typeof(storage.get('isDirtyCordinates'))=='undefined') ?  false : storage.get('isDirtyCordinates');
	if(isDirtyCordinates){
		var coordinatesJson = storage.get('NODES_COORDINATES');
    	common.updateData("/user/settings","PATCH",coordinatesJson).then(function(res){
    		console.log('topology position updated')
    		storage.set('isDirtyCordinates',false);
    	}).fail(function(error){
    		console.log('topology position failed to update')
       })
    } 
}
if(typeof(TopologyIntervalID)!='undefined' && page !=='topology'){
	clearTimeout(TopologyIntervalID);
	console.log('TopologyIntervalID',TopologyIntervalID)
	TopologyIntervalID = undefined;
}
if(page == "home"){
	$("#home-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
	storage.remove("SWITCHES_LIST");
}else if(page == "topology"){
	$("#topology-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
	storage.remove("SWITCHES_LIST");
	TopologyIntervalID = setInterval(updateTopologyCoordinates,5000);
}else if(page == "flows" || page == "flowdetails" || (page.indexOf('details')!==-1 && href.indexOf('flows')!==-1)){
	$("#flows-menu-id").addClass("active");
	storage.remove("SWITCHES_LIST");
}else if(page == "isllist" || page == "isl"){
	$("#isl-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
	storage.remove("SWITCHES_LIST");
}else if(page == "switch" || (page.indexOf('details')!==-1 && href.indexOf('switch')!==-1)){ 
	$("#switch-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
}
else if(page == "usermanagement" || (page.indexOf('details')!==-1 && href.indexOf('usermanagement')!==-1)){ 
	$("#usermanagement-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
}else if(page =='useractivity'){
	$("#useractivity-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
}else if(page =='discrepancy'){
	$("#discrepancy-menu-id").addClass("active");
	storage.remove("FLOWS_LIST");
}
/** sub menu related code End **/

	
$('.t-logy-icon').click(function(e){
	 e.stopPropagation();
	 $("#topology-txt").slideToggle();
});

$('#topology-txt').click(function(e){
    e.stopPropagation();
});

$(document).click(function(){
    $('#topology-txt').slideUp();
});

$("#logout").click(function(){
	storage.remove("FLOWS_LIST");
	storage.remove("SWITCHES_LIST");
	storage.remove("switchDetailsJSON");
});


var requests = null;
var loadGraph = {	
		loadGraphData:function(apiUrl,requestType,selMetric,domId){	
			requests =  $.ajax({url : APP_CONTEXT+apiUrl,type : requestType,
					dataType : "json",
					error : function(errResponse) {	
						if(domId == 'source'){
							$("#waitisl1").css("display", "none");	
						}
						if(domId == 'target'){
							$("#waitisl2").css("display", "none");	
						}
						if($('#selectedGraph').val() == 'isl'){
							$("#wait1").css("display", "none");
						}
						if(domId == undefined && domId == undefined){
							$("#waitisl1").css("display", "none");
							$("#waitisl2").css("display", "none");	
						}
						if($('#selectedGraph').val() != 'isl' ) {
							showIslSwitchStats.showIslSwitchStatsData(errResponse,selMetric,null,domId);
						} else{
							showStatsGraph.showStatsData(errResponse,selMetric,null,domId);
						}
					}
				});			
			
		return requests;				
	}
}

var graphAutoReload = {	
		autoreload:function(){
			
			var key = window.location.href;
			
			if(key.includes("isl")) {				
				$("#autoreloadISL").toggle();
				var savedEndDate =  new Date($('#datetimepicker8ISL').val());
				$('#savedEnddate').val(savedEndDate);
				$("#toId").toggle();
				var checkbox =  $("#check").prop("checked");
				if(checkbox == false){
										
					$("#autoreloadISL").val('');
					if($('#selectedGraph').val() == 'source' || $('#selectedGraph').val() == 'target') {
						clearInterval(graphInterval);
						clearInterval(graphIntervalISLName);		
						
						clearInterval(graphIntervalISL);
					} else {
						clearInterval(callIntervalData);
						clearInterval(graphInterval);
					}
					$("#autoreloadId").removeClass("has-error")	
				    $(".error-message").html("");
					$('#wait1').hide();	
				}
			} else {	
				$("#autoreload").toggle();
				var savedEndDate =  new Date($('#datetimepicker8').val());
				$('#savedEnddate').val(savedEndDate);
				$("#toId").toggle();
				var checkbox =  $("#check").prop("checked");
				if(checkbox == false){
					$('#savedEnddate').val('');
					$("#autoreload").val('');
					clearInterval(callIntervalData);
					clearInterval(graphInterval);
					$("#autoreloadId").removeClass("has-error")	
				    $(".error-message").html("");
					$('#wait1').hide();	
				}
			}
	}
}

function ZoomCallBack(minX, maxX, yRanges) {
    var startDate = moment(new Date(minX)).format("YYYY/MM/DD HH:mm:ss");
    var endDate = moment( new Date(maxX)).format("YYYY/MM/DD HH:mm:ss");
    if($('#datetimepicker7').length){
   	 $('#datetimepicker7').val(startDate);
   	 $('#datetimepicker8').val(endDate);
    }
    if($('#datetimepicker7ISL').length){
   	 $('#datetimepicker7ISL').val(startDate);
   	 $("#datetimepicker8ISL").val(endDate)
    }
  }

 var showStatsGraph = {	

	showStatsData:function(response,metricVal,graphCode,domId,startDate,endDate) {
		
		var metric1 = "";
		var metric2 = "";
		var direction1 = "";
		var direction2 = "";
		var data = response;
		var jsonResponse = response.responseJSON;
		
		var graphData = [];
		 if(data){
			 if(data.length == 0){
					if(graphCode == 0) {
						 var g = new Dygraph(document.getElementById("source-graph_div"), [],
							 	 {
							 		      drawPoints: false,
							 		      labels: "test",									 			 
							 		      labelsUTC:true, 		      
							 		      colors: ["#495cff","#aad200"],
							 	  });	
						 return;
					}
					if(graphCode == 1) {
						 var g = new Dygraph(document.getElementById("dest-graph_div"), [],
							 	 {
							 		      drawPoints: false,
							 		      labels: "test",									 			 
							 		      labelsUTC:true, 		      
							 		      colors: ["#495cff","#aad200"],
							 	  });	
						 return;
					}
				 
			 var g = new Dygraph(document.getElementById("graphdiv"), [],
				 	 {
				 		      drawPoints: false,
				 		      labels: "test",									 			 
				 		      labelsUTC:true,
				 		      colors: ["#495cff","#aad200"],
				 	  });	
			 return;
			 } 
		 }
		 
		if(!jsonResponse) { 
		    	var getValue = data[0].dps;	    	
		    	 metric1 = data[0].metric;	
		    	 
		    	if(data.length == 2) {
		    		var getVal = data[1].dps;
		    		 metric2 = data[1].metric;
		    		 
		    		 if(data[1].tags.direction){
		    			 metric2 = data[1].metric + "("+data[1].tags.direction+")"
		    		 }
		    		 if(data[0].tags.direction){
		    			 metric1 = data[0].metric + "("+data[0].tags.direction+")"
		    		 }
		    	}
		    	    
				    if(!getValue) {
				    	metric1 = "F";
				    	metric2 = "R";		    	
				    } else {
				    	if(typeof(startDate)!=='undefined'){
							var dat = new Date(startDate);
							var startTime = dat.getTime();
							var usedDate = new Date();
							startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
							var arr = [new Date(startTime),null,null];
							graphData.push(arr);
						}
				 
				    	 for(i in getValue) {

                              if(getValue[i]<0){
                              	getValue[i] = 0;
                              }    
                              if(getVal[i]<0){
                            	  getVal[i] = 0;
                              }
						      var temparr = [];
						      temparr[0] = new Date(Number(i*1000)); 
						       temparr[1] = getValue[i]
						      if(data.length == 2) {
								temparr[2] = getVal[i];
						  
						      }
						      graphData.push(temparr)
						 }
				    }
				    if(metric1 && metric2){
				    	var labels = ['Time', metric1,metric2];
				    }else if(metric1){
				    	var labels = ['Time', metric1];
				    }
				    else{
				    	var labels = ['Time', metric2];
				    }	
		}else{
			metric1 = "F";
	    	metric2 = "R";
			var labels = ['Time', metric1,metric2];
		}
		
		if(typeof(endDate)!=='undefined'){
			var dat = new Date(endDate);
			var lastTime = dat.getTime();
			var usedDate = new Date(graphData[graphData.length-1][0]);
			lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
			var arr = [new Date(lastTime),null,null];
			graphData.push(arr);
		}
		
		
		
		if(graphCode == undefined){
					
			if(domId == 'source') {
				var g = new Dygraph(document.getElementById("source-graph_div"), graphData,
						{
							 		      drawPoints: false,
							 		      labels: labels,									 			 
							 		      labelsUTC:true, 
							 		      includeZero : true,		      
							 		      colors: ["#495cff","#aad200"],
							 		     zoomCallback:function(minX, maxX, yRanges){
								 		    	ZoomCallBack(minX, maxX, yRanges)
								 		      },
						});
			}else if(domId == 'target') {
				var g = new Dygraph(document.getElementById("dest-graph_div"), graphData,
						{
							 		      drawPoints: false,
							 		      labels: labels,	 								 			 
							 		      labelsUTC:true,	
							 		      includeZero : true,	      
							 		      colors: ["#495cff","#aad200"],
							 		     zoomCallback:function(minX, maxX, yRanges){
								 		    	ZoomCallBack(minX, maxX, yRanges)
								 		      },
						});
			} else {

			if($('#selectedGraph').val() =='isl') {
				var g = new Dygraph(document.getElementById("graphdiv"), graphData,
						{    										
							 		      drawPoints: false,
							 		      labels: labels,									 			 
							 		      labelsUTC:true, 
							 		      includeZero : true,
							 		      colors: ["#495cff","#aad200"],
							 		      zoomCallback:function(minX, maxX, yRanges){
								 		    	ZoomCallBack(minX, maxX, yRanges)
								 		      },
						});
			} else {
				       if(domId == undefined) {
				    	   
					        var g1 = new Dygraph(document.getElementById("graphdiv"), graphData,
							{
										 		      drawPoints: false,
										 		      labels:labels,	 
										 		      labelsUTC:true,
										 		      includeZero : true,
										 		     drawAxesAtZero:true,
										 		      colors: ["#495cff","#aad200"],
										 		      zoomCallback:function(minX, maxX, yRanges){
										 		    	ZoomCallBack(minX, maxX, yRanges)
										 		      },
							});
				       } 
				       if(domId != undefined && graphCode == null && $('#selectedGraph').val() =='source' ) {
					       var g1 = new Dygraph(document.getElementById("source-graph_div"), graphData,
									  {
										 		      drawPoints: false,
										 		      labels: labels,								 			 
										 		      labelsUTC:true,
										 		      includeZero : true,
										 		      colors: ["#495cff","#aad200"],
										 		      zoomCallback:function(minX, maxX, yRanges){
										 		    	ZoomCallBack(minX, maxX, yRanges)
										 		      },
									  });
				       }
				       if(domId != undefined && graphCode == null && $('#selectedGraph').val() =='target') {
							 var g2 = new Dygraph(document.getElementById("dest-graph_div"), graphData,
									  {
										 		      drawPoints: false,
										 		      labels: labels,									 			 
										 		      labelsUTC:true, 	
										 		      includeZero : true,	      
										 		      colors: ["#495cff","#aad200"],
										 		      zoomCallback:function(minX, maxX, yRanges){
											 		    	ZoomCallBack(minX, maxX, yRanges)
											 		      },
									 });	
				       }
			     }
		}
	}if(graphCode == 0){
			$("#dest-graph_div").empty();
			   var g = new Dygraph(document.getElementById("source-graph_div"), graphData,
				{
					 		      drawPoints: false,
					 		      labels: labels,	 									 			 
					 		      labelsUTC:true,	
					 		      includeZero : true,      
					 		      colors: ["#495cff","#aad200"],
					 		     zoomCallback:function(minX, maxX, yRanges){
						 		    	ZoomCallBack(minX, maxX, yRanges)
						 		      },
				});	

		}
		if(graphCode == 1){
			$("#source-graph_div").empty();
			  var g = new Dygraph(document.getElementById("dest-graph_div"), graphData,
				{
					 		      drawPoints: false,
					 		      labels: labels,	 								 			 
					 		      labelsUTC:true,	
					 		      includeZero : true,	      
					 		      colors: ["#495cff","#aad200"],
					 		     zoomCallback:function(minX, maxX, yRanges){
						 		    	ZoomCallBack(minX, maxX, yRanges)
						 		      },
				});	

			
		}
			     
	}
}




var showIslSwitchStats = {	

		showIslSwitchStatsData:function(response,metricVal,graphCode,domId,startDate,endDate) {
			
			var metric1 = "";
			var metric2 = "";
			var direction1 = "";
			var direction2 = "";
			var data = response;
			var jsonResponse = response.responseJSON;
			var graphData = [];
			 
			 if(data){
				 if(data.length == 0){
						if(graphCode == 0) {
							 var g = new Dygraph(document.getElementById("source-graph_div"), [],
								 	 {
								 		      drawPoints: false,
								 		      labels: "test",	 		      
								 		      colors: ["#495cff","#aad200"],
								 	  });	
							 return;
						}
						if(graphCode == 1) {
							 var g = new Dygraph(document.getElementById("dest-graph_div"), [],
								 	 {
								 		      drawPoints: false,
								 		      labels: "test",	 		      
								 		      colors: ["#495cff","#aad200"],
								 	  });	
							 return;
						}
				   } 
			 }
			 
			if(!jsonResponse) {
				
			    	var getValue = data[0].dps;	    	
			    	 metric1 = data[0].metric;	
			    	 
			    	if(data.length == 2) {
			    		var getVal = data[1].dps;
			    		 metric2 = data[1].metric;
			    		 
			    		 if(data[1].tags.direction){
			    			 metric2 = data[1].metric + "("+data[1].tags.direction+")"
			    		 }
			    		 if(data[0].tags.direction){
			    			 metric1 = data[0].metric + "("+data[0].tags.direction+")"
			    		 }
			    	}
			    	    
					    if(!getValue) {
					    	metric1 = "F";
					    	metric2 = "R";		    	
					    } else {
					    	if(typeof(startDate)!=='undefined'){
								var dat = new Date(startDate);
								var startTime = dat.getTime();
								var usedDate = new Date();
								startTime = startTime - usedDate.getTimezoneOffset() * 60 * 1000;
								var arr = [new Date(startTime),null,null];
								graphData.push(arr);
							}
					    	 for(i in getValue) {
							    
					    		 if(getValue[i]<0){
	                              	getValue[i] = 0;
	                              }    
	                              if(getVal[i]<0){
	                            	  getVal[i] = 0;
	                              }
	                              
							      var temparr = [];
							      temparr[0] = new Date(Number(i*1000));
							      temparr[1] = getValue[i]
							      
							      if(data.length == 2) {
							   		temparr[2] = getVal[i];
							    	}
							      graphData.push(temparr)
							 }
					    }
					    if(metric1 && metric2){
					    	var labels = ['Time', metric1,metric2];
					    }else if(metric1){
					    	var labels = ['Time', metric1];
					    }
					    else{
					    	var labels = ['Time', metric2];
					    }	
			}else{
				metric1 = "F";
		    	metric2 = "R";
				var labels = ['Time', metric1,metric2];
			}
			
			if(typeof(endDate)!=='undefined'){
				var dat = new Date(endDate);
				var lastTime = dat.getTime();
				var usedDate = new Date(graphData[graphData.length-1][0]);
				lastTime = lastTime - usedDate.getTimezoneOffset() * 60 * 1000;
				var arr = [new Date(lastTime),null,null];
				graphData.push(arr);	
			}
			
			if(graphCode == undefined){
						
				if(domId=='source') {
					var g = new Dygraph(document.getElementById("source-graph_div"), graphData,
							{
								 		      drawPoints: false,
								 		      labels: labels,								 			 
								 		      labelsUTC:true,
								 		      includeZero : true,
								 		      colors: ["#495cff","#aad200"],
								 		     zoomCallback:function(minX, maxX, yRanges){
									 		    	ZoomCallBack(minX, maxX, yRanges)
									 		      },
							});
				}else if(domId == 'target') {
					var g = new Dygraph(document.getElementById("dest-graph_div"), graphData,
							{
								 		      drawPoints: false,
								 		      labels: labels,								 			 
								 		      labelsUTC:true,
								 		      includeZero : true,
								 		      colors: ["#495cff","#aad200"],
								 		     zoomCallback:function(minX, maxX, yRanges){
									 		    	ZoomCallBack(minX, maxX, yRanges)
									 		      },
							});
				} else {
					       if(domId != undefined && graphCode == null && $('#selectedGraph').val() == 'source') {
						       var g1 = new Dygraph(document.getElementById("source-graph_div"), graphData,
										  {
											 		      drawPoints: false,
											 		      labels: labels,									 			 
											 		      labelsUTC:true,
											 		      includeZero : true, 		      
											 		      colors: ["#495cff","#aad200"],
											 		     zoomCallback:function(minX, maxX, yRanges){
												 		    	ZoomCallBack(minX, maxX, yRanges)
												 		      },
										  });
					       }
					       if(domId != undefined && graphCode == null &&  $('#selectedGraph').val() == 'target' ) {
								 var g2 = new Dygraph(document.getElementById("dest-graph_div"), graphData,
										  {
											 		      drawPoints: false,
											 		      labels: labels,									 			 
											 		      labelsUTC:true, 
											 		      includeZero : true,		      
											 		      colors: ["#495cff","#aad200"],
											 		     zoomCallback:function(minX, maxX, yRanges){
												 		    	ZoomCallBack(minX, maxX, yRanges)
												 		      },
										 });	
					       }
				     
			}
		}if(graphCode == 0){
				$("#dest-graph_div").empty();
				   var g = new Dygraph(document.getElementById("source-graph_div"), graphData,
					{
						 		      drawPoints: false,
						 		      labels: labels,	 									 			 
						 		      labelsUTC:true,	 
						 		      includeZero : true,     
						 		      colors: ["#495cff","#aad200"],
						 		     zoomCallback:function(minX, maxX, yRanges){
							 		    	ZoomCallBack(minX, maxX, yRanges)
							 		      },
					});	

			}
			if(graphCode == 1){
				$("#source-graph_div").empty();
				  var g = new Dygraph(document.getElementById("dest-graph_div"), graphData,
					{
						 		      drawPoints: false,
						 		      labels: labels,	 								 			 
						 		      labelsUTC:true,	
						 		      includeZero : true,	      
						 		      colors: ["#495cff","#aad200"],
						 		     zoomCallback:function(minX, maxX, yRanges){
							 		    	ZoomCallBack(minX, maxX, yRanges)
							 		      },
					});	

				
			}
			    
	  }
}


var getMetricDetails = {	
		getFlowMetricData:function(response){
						
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if(metricArray[i].includes("bits") || metricArray[i].includes("packets") || metricArray[i].includes("bytes")) {
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}
			}
			$("select.selectbox_menulist").html("").html(optionHTML);
			$('#menulist').val('packets');
		},
		getPortMetricData:function(response){
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			for (var i = 0; i < metricArray.length ; i++) {
				
				if(metricArray[i].includes("bytes") || metricArray[i].includes("latency")) {
				} else{
					optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
				}			
			}
			$("select.selectbox_menulist").html("").html(optionHTML);
			$('#portMenulist').val('bits');
		}
}


var autoVal = {	
			
			reloadValidation:function(callback) {
				
				var key = window.location.href;			
				if(key.includes("isl")){
										
					var autoreload = $("#autoreloadISL").val();
					var numbers = /^[-+]?[0-9]+$/;  
					var checkNo = $("#autoreloadISL").val().match(numbers);
					var checkbox =  $("#check").prop("checked");
					
					if(checkbox) {
						
						if($("#autoreloadISL").val().length > 0) {	
							if(autoreload < 0) {
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Autoreload cannot be negative");
								valid=false;
								clearInterval(graphInterval);
								clearInterval(graphIntervalISL);
								clearInterval(graphIntervalISLName);
								callback(valid)
							} else if(autoreload == 0) {						
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Autoreload cannot be zero");
								valid=false;			
								clearInterval(graphInterval);
								clearInterval(graphIntervalISL);
								clearInterval(graphIntervalISLName);
								callback(valid)
							}else if(!checkNo) {		
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Please enter positive number only");			
								valid=false;
								clearInterval(graphInterval);
								clearInterval(graphIntervalISL);
								clearInterval(graphIntervalISLName);
								callback(valid)
							}
							else{
								valid = true;
								callback(valid)
							}
						}
				   }
				} else {
					
					var autoreload = $("#autoreload").val();
					var numbers = /^[-+]?[0-9]+$/;  
					var checkNo = $("#autoreload").val().match(numbers);
					var checkbox =  $("#check").prop("checked");
				
					if(checkbox) {
						
						if($("#autoreload").val().length > 0) {	
							if(autoreload < 0) {
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Autoreload cannot be negative");
								valid=false;
								clearInterval(graphInterval);
								callback(valid)
							} else if(autoreload == 0) {
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Autoreload cannot be zero");
								valid=false;							
								clearInterval(graphInterval);
								callback(valid)
							}else if(!checkNo) {								
								$("#autoreloadId").addClass("has-error")	
								$(".error-message").html("Please enter positive number only");		
								valid=false;
								clearInterval(graphInterval);
								callback(valid)
							}
							else{
								valid = true;
								callback(valid)
							}
						}
				     }
				}
		 }
	}


var islDetails = {
		
		checkDropdown:function(domObj){
			return $("select.selectbox_menulist option").length;
			
		},
		getIslMetricData:function(domObj) {
			var selectedgraph = $('#selectedGraph').val();
			var metricArray = [];			
			metricArray = metricVarList;
			var optionHTML = "";
			
			if(parseInt(this.checkDropdown()) < 1){

				for (var i = 0; i < metricArray.length ; i++) {
					
					if( metricArray[i].includes("bytes")) {
					} else {
						optionHTML += "<option value=" + metricArray[i].split(":")[0] + ">"+ metricArray[i].split(":")[1] + "</option>";
					}
					
				}	
				$("select.selectbox_menulist").html("").html(optionHTML);
				 if (obj.hasOwnProperty("flowid")) {				
						$('#menulist').val('pen.flow.packets');
					} else {
						$('#menulistISL').val('bits');				
					}	
			}
			if(selectedgraph == 'source' || selectedgraph == 'target'){
				$("#islMetric").show();
				//$("#graphrowDiv").show();
				$('#isl_latency_graph').hide();
				if(selectedgraph == 'source'){ 
					$('#sourceGraphDiv').show();
					$('#targetGraphDiv').hide();
			    	$("#source-graph_div").show();
			    	$("#dest-graph_div").hide();
			    	getISLGraphData(common.toggleSwitchID(srcSwitch),srcPort,selectedgraph);
				}else if(selectedgraph == 'target'){ 
					$('#sourceGraphDiv').hide();
					$('#targetGraphDiv').show();
					$("#source-graph_div").hide();
			    	$("#dest-graph_div").show();
			    	getISLGraphData(common.toggleSwitchID(targetSwitch),targetPort,selectedgraph);
				}
			}
			if(selectedgraph == 'isl'){
				$("#islMetric").hide();
				//$("#graphrowDiv").hide();
				$('#sourceGraphDiv').hide();
				$('#targetGraphDiv').hide();
				$('#isl_latency_graph').show();
			}
		}
}

function getISLGraphData(switchId,portId,domId) {

	var graphDivCode;
	var selectedGraph = $('#selectedGraph').val();
	if(selectedGraph == 'source' ){
		$('#sourceGraphDiv').show();
		$('#targetGraphDiv').hide();
		graphDivCode = 0;
		$("#waitisl2").hide();
		$("#waitisl1").show();
	}

	if(selectedGraph == 'target') {
		$('#sourceGraphDiv').hide();
		$('#targetGraphDiv').show();
		graphDivCode = 1;
		$("#waitisl1").hide();
		$("#waitisl2").show();		
	}
	var regex = new RegExp("^\\d+(s|h|m){1}$");
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7ISL").val());
	var endDate =  new Date($("#datetimepicker8ISL").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");	
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");
	var downsampling = $("#downsamplingISL").val();
	var downsamplingValidated = regex.test(downsampling);
	var selMetric=$("select.selectbox_menulist").val();
	var valid=true;
	
	if(downsamplingValidated == false) {
		
		$("#DownsampleID").addClass("has-error")	
		$(".downsample-error-message").html("Please enter valid input.");		
		valid=false;
		return
	}
	if(startDate.getTime() > currentDate.getTime()) {

		$("#fromId").addClass("has-error")	
		$(".from-error-message").html("From date should not be greater than currentDate.");				
		valid=false;
		return;
	} else if(endDate.getTime() < startDate.getTime()){
		$("#toId").addClass("has-error")	
		$(".to-error-message").html("To date should not be less than from fate.");		
		valid=false;
		return;
	}
	
	var autoreloadISL = $("#autoreloadISL").val();
	var numbers = /^[-+]?[0-9]+$/;  
	var checkNo = $("#autoreloadISL").val().match(numbers);
	var checkbox =  $("#check").prop("checked");
	var test = true;
    autoVal.reloadValidation(function(valid){
	  
	  if(!valid) {
		  test = false;		  
		  return false;
	  }
  });
  
if(test) {
	
	$("#fromId").removeClass("has-error")
    $(".from-error-message").html("");
	
	$("#toId").removeClass("has-error")
    $(".to-error-message").html("");
	
	$("#autoreloadId").removeClass("has-error")
    $(".error-message").html("");
	
  	$("#DownsampleID").removeClass("has-error")
	$(".downsample-error-message").html("");
	   var switch_id = switchId.replace(/:/g, "");
  	   var url = "/stats/switchid/"+switch_id+"/port/"+portId+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric;
  	   	   
	   loadGraph.loadGraphData(url,"GET",selMetric,domId).then(function(response) {	   
		if(graphDivCode ==0){
			$("#waitisl1").css("display", "none");
			$('body').css('pointer-events', 'all');
			showIslSwitchStats.showIslSwitchStatsData(response,selMetric,graphDivCode,"",startDate,endDate); 
		}
		if(graphDivCode ==1){
			$("#waitisl2").css("display", "none");
			$('body').css('pointer-events', 'all');
			showIslSwitchStats.showIslSwitchStatsData(response,selMetric,graphDivCode,"",startDate,endDate); 
		}
		
})
	
			
			if(autoreloadISL){  
				
				if(selectedGraph =='source') {	
					clearInterval(graphIntervalISL);
					graphIntervalISL = setInterval(function(){
						callIslSwitchIntervalData(srcSwitch,srcPort) 
					}, 1000*autoreloadISL);
				}
				
				if(selectedGraph == 'target') {
					clearInterval(graphIntervalISL);
					graphIntervalISL = setInterval(function(){
						callIslSwitchIntervalData(targetSwitch,targetPort) 
					}, 1000*autoreloadISL);
				}
			
		   }
	}	
}

$(function() {
	
	$("#datetimepicker7ISL,#datetimepicker8ISL,#downsamplingISL,#menulistISL,#autoreloadISL").on("change",function(event) {

		if($("#selectedGraph").val() =='source') {
			getISLGraphData(common.toggleSwitchID(srcSwitch),srcPort);	
		}
		if($("#selectedGraph").val() =='target'){
			getISLGraphData(common.toggleSwitchID(targetSwitch),targetPort);
		}
	});
});


function callIslSwitchIntervalData(switchId,portId){ 
	
	var currentDate = new Date();
	var startDate = new Date($("#datetimepicker7ISL").val());
	var convertedStartDate = moment(startDate).format("YYYY-MM-DD-HH:mm:ss");	
	var endDate = new Date()
	var convertedEndDate = moment(endDate).format("YYYY-MM-DD-HH:mm:ss");	
	var downsampling =$("#downsamplingISL").val()
	
	var switch_id = switchId.replace(/:/g, "");
	var url = "/stats/switchid/"+switch_id+"/port/"+portId+"/"+convertedStartDate+"/"+convertedEndDate+"/"+downsampling+"/"+selMetric;
	loadGraph.loadGraphData(url,"GET",selMetric).then(function(response) {
		
	if($("#selectedGraph").val() =='source'){
		$("#waitisl1").css("display", "none");
		$('body').css('pointer-events', 'all'); 	  
	}
	if($("#selectedGraph").val() =='target'){
		$("#waitisl2").css("display", "none");
		$('body').css('pointer-events', 'all'); 	  
	}
	
	
	showIslSwitchStats.showIslSwitchStatsData(response,selMetric,graphDivCode,"",startDate,endDate); 
	})
}

var cookie = new function() {
    this.set = function ( name, value, days ) {
        var expires = "";
        if ( days ) {
            var date = new Date();
            date.setTime( date.getTime() + ( days * 24 * 60 * 60 * 1000 ) );
            expires = "; expires=" + date.toGMTString();
        }
        document.cookie = name + "=" + value + expires + "; path=/";
    };

    this.get = function ( name ) {
        var nameEQ = name + "=";
        var ca = document.cookie.split( ';' );
        for ( var i = 0; i < ca.length; i++ ) {
            var c = ca[ i ];
            while ( c.charAt(0) == ' ' ) c = c.substring( 1, c.length );
            if ( c.indexOf( nameEQ ) == 0 ) return c.substring( nameEQ.length, c.length );
        }
        return null;
    };

    this.delete = function ( name ) {
        this.set( name, "", -1 );
    };
}

/** copy to clipboard functionality **/
$(function(){
	// disable right click
	 $( ".copy_to_clipBoard" ).contextmenu(function(e){
		 e.preventDefault();
		 $(e.target).addClass('selected');
		 var menuHtml = '<div class="contextmenu"><ul><li class="copy_data" onclick="copySelectedData()">Copy to clipboard</li></ul></div>';
		 $('body').find('.contextmenu').remove();
		 $('body').append(menuHtml);
		 $('.contextmenu').css({
			 	top: e.pageY+'px',
		        left: e.pageX+'px',
		        "z-index":2,
		    }).show();
	 });
	 	
	$('.contextmenu').click(function(e) {
	     $('.contextmenu').hide();
	 });
	 $(document).click(function() {
	     $('.contextmenu').hide();
	 });
})
function fallbackCopyTextToClipboard(text) {
	  var textArea = document.createElement("textarea");
	  textArea.value = text;
	  document.body.appendChild(textArea);
	  textArea.focus();
	  textArea.select();
	
	  try {
		 $('.copy_to_clipBoard').removeClass('selected');
	    var successful = document.execCommand('copy');
	    var msg = successful ? 'successful' : 'unsuccessful';
	    console.log('Fallback: Copying text command was ' + msg);
	  } catch (err) {
	    console.error('Fallback: Oops, unable to copy', err);
	  }
	
	  document.body.removeChild(textArea);
}
		
 function copySelectedData(){
	 	var text = $(".copy_to_clipBoard.selected").html();
	 	if (!navigator.clipboard) {
		    fallbackCopyTextToClipboard(text);
		    return;
		  }
		  navigator.clipboard.writeText(text).then(function() {
			$('.copy_to_clipBoard').removeClass('selected');
		    console.log('Async: Copying to clipboard was successful!');
		  }, function(err) {
		    console.error('Async: Could not copy text: ', err);
		  });
 }
/** copy to clipboard functionality end ***/
/* ]]> */