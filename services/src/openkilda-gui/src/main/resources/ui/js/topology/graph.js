/*<![CDATA[*/

/**
 * Below the javascript/ajax/jquery code to generate 
 * the force directed graph based on 
 * three api calls
 * @param {/switch} will return switch details
 * @param {/switch/links} will return links details
 * @return {/switch/flows} will return flow details
 */


/** ajax call to switch api to get switch details */

var storage = new LocalStorageHandler();
	
var responseData = {
	switch:{data:[]},
	isl:{data:[]},
	flow:{data:[]}
};
  

var ISL = {
	DISCOVERED: "#00baff",
    FAILED :  "#d93923",
    UNIDIR : "#333"
};

var RIGHT_CHECKBOXES = {
	SWITCH_CHECKED: 0,
	ISL_CHECKED: 1,
	FLOW_CHECKED: 0,
	REFRESH_CHECKED: 0,
	REFRESH_INTERVAL: 1,
	REFRESH_TYPE: "m"
}	
var switchIntervalId,islIntervalId;

var cookieCheckboxes = cookie.get( 'RIGHT_CHECKBOXES');
if(cookieCheckboxes == null){
	cookie.set( 'RIGHT_CHECKBOXES', JSON.stringify(RIGHT_CHECKBOXES));
}

var api = {
	getSwitches: function(){
		$.ajax({
			url : APP_CONTEXT + "/switch/list",
			type : 'GET',
			success : function(response) {
				if(response)
				{
					responseData.switch.data = response;
					api.getLink();
				}
				else
				{
					$("#wait").css("display", "none");
					common.infoMessage('No Switch Available','info');
				}
			},
			error : function(errResponse) {
				common.infoMessage('No Switch Available','info');
				$("#wait").css("display", "none");
			},
			dataType : "json"
		});
	},
	getLink: function(){
		$.ajax({
			url : APP_CONTEXT + "/switch/links",
			type : 'GET',
			success : function(response) {
				if (response) {
					responseData.isl.data = response;
				}
				var storageValue = cookie.get('RIGHT_CHECKBOXES');
				if(storageValue != null){
					try{
						storageValue = JSON.parse(storageValue);
						if(storageValue.FLOW_CHECKED){
							api.getFlowCount();
						}else{
							graph.init(responseData);
						}
					}catch(e){
						api.getFlowCount();
					}
				}
				
			},
			error : function(errResponse) {
				graph.init(responseData);
			},
			dataType : "json"
		});
	},
	
	getFlowCount: function(){
		$.ajax({
			url : APP_CONTEXT + "/flows/count",
			type : 'GET',
			success : function(response) {
				var flows = [];
				if (response) {
					for(var i=0;i<response.length;i++)
					{
						flows.push(response[i]);
					}
					
					responseData.flow.data = flows;
				}
				graph.init(responseData);
			},
			error : function(errResponse) {
				graph.init(responseData);
			},
			dataType : "json"
		});
	}
}

api.getSwitches();

//declare variables
var zoomFitCall = true;
var doubleClickTime = 0;
var threshold = 500; 
var text_center = false;
var flagHover = true;
var nominal_base_node_size = 40;
var nominal_text_size = 10;
var max_text_size = 24;
var nominal_stroke = 1.5;
var max_stroke = 4.5;
var max_base_node_size = 36;
var min_zoom = 0.15;
var max_zoom = 3;
var isDragMove = false;
var scale = 1.0;
var optArray = [];

var mLinkNum ={};
var linkedByIndex = {};
var nodes = [], links = [], flows = [];

var margin = {top: -5, right: -5, bottom: -5, left: -5},
	width = window.innerWidth,
	height = window.innerHeight,
	radius = 35,
	zoom, force, drag, svg,  link, node, text, flow_count,linksSourceArr,new_nodes=false;


var size = d3.scale.pow().exponent(1)
.domain([1,100])
.range([8,24]);
	
var force = d3.layout.force()
.linkDistance(function(d) { 
 		var distance = 150;
 		try{
		if(!d.flow_count){
			if(d.speed == "40000000"){
				distance = 100;
			}else {
				distance = 300;
			}
 		}
 		}catch(e){}
 		return distance;  })
.charge(-1000)
.size([width,height]);

var svg = d3.select("#switchesgraph").append("svg");
var zoom = d3.behavior.zoom().scaleExtent([min_zoom,max_zoom])
var g = svg.append("g");
svg.style("cursor","move");
function zoomEventCall(){
	zoom.on("zoom", function() {
		var stroke = nominal_stroke;
		if (nominal_stroke*zoom.scale()>max_stroke) stroke = max_stroke/zoom.scale();
		link.style("stroke-width",stroke);
		circle.style("stroke-width",stroke);
		var base_radius = nominal_base_node_size;
		if (nominal_base_node_size*zoom.scale()>max_base_node_size) base_radius = max_base_node_size/zoom.scale();
		    circle.attr("d", d3.svg.symbol()
		    .size(function(d) { return Math.PI*Math.pow(size(d.size)*base_radius/nominal_base_node_size||base_radius,2); })
		    .type(function(d) { return d.type; }))
		if (!text_center) {
			text.attr("dx", function(d) { 
				return nominal_base_node_size;
				//return (size(d.size)*base_radius/nominal_base_node_size||base_radius);
			
			});
		}
		
		var text_size = nominal_text_size;
		if (nominal_text_size*zoom.scale()>max_text_size) text_size = max_text_size/zoom.scale();
			text.style("font-size",text_size + "px");
			g.attr("transform", "translate(" + d3.event.translate + ")scale(" + d3.event.scale + ")");
});
}


graph = {

	init: function(responseData){
		 var isDirtyCordinates =(typeof(storage.get('isDirtyCordinates'))=='undefined') ?  false : storage.get('isDirtyCordinates');
	    if(typeof(storage.get('isDirtyCordinates'))=='undefined'){
	    	storage.set('isDirtyCordinates',isDirtyCordinates);
	    }
		if (responseData.switch.data.length == 0 && responseData.isl.data.length == 0 && responseData.flow.data.length == 0) {
			common.infoMessage('No Data Available','info');
			$("#wait").css("display", "none");
			$("#switchesgraph").removeClass("hide");
			return false;
		}

		/*
		 * A force layout requires two data arrays. The first array, here named
		 * nodes, contains the object that are the focal point of the visualization.
		 * The second array, called links below, identifies all the links between
		 * the nodes.
		 */
		nodes = responseData.switch.data;
		links = responseData.isl.data;
		flows = responseData.flow.data;
		linksSourceArr= [];
		var linksArr = [];
		if(nodes.length < 50){
			min_zoom = 0.5;
			zoom.scaleExtent([min_zoom,max_zoom])
		}
		if (links.length>0) {
			
			try{
				var result = common.groupBy(links, function(item)
				{
					return [item.source_switch, item.target_switch];
				});
				for(var i=0,len=result.length;i<len;i++){
					var row = result[i];
					
					if(row.length>=1){
						for(var j=0,len1=row.length;j<len1;j++){
							var key= row[j].source_switch+"_"+row[j].target_switch;
							if(typeof(linksSourceArr[key])!=='undefined'){
								linksSourceArr[key].push(row[j]);
							}else{
								linksSourceArr[key] = []
								linksSourceArr[key].push(row[j]);
							}

						}
					}
				}
			}catch(e){
			
			}
			
		}
		
		if(flows.length>0){
			links = links.concat(flows);
		}
		optArray = optArray.sort();
		
		// calculating nodes
		var nodelength = nodes.length;
		var linklength = links.length;
		for (var i = 0; i < nodelength; i++) {
			optArray.push(nodes[i].name);
			for (var j = 0; j < linklength; j++) {
				if(nodes[i].switch_id == links[j]["source_switch"] && nodes[i].switch_id == links[j]["target_switch"]){
					links[j].source = i;
					links[j].target = i;
				}else{
					if (nodes[i].switch_id == links[j]["source_switch"]) {
						links[j].source = i;
					} else if (nodes[i].switch_id == links[j]["target_switch"]) {
						links[j].target = i;
					}
				}	
			}
		}
		$(function () {
		    $("#search").autocomplete({
		        source: optArray,
		        appendTo: "#ui-container",
		        select: function (event, ui) {        
		        	searchNode(ui.item.value);
			    }
		    });
		});
		
		sortLinks();
		setLinkIndexAndNum();
		
		links.forEach(function (d, index, object) {
			if (typeof nodes[d.source] === 'undefined' || typeof nodes[d.target] === 'undefined') {
	        	object.splice(index, 1);
	        }
			linkedByIndex[d.source + "," + d.target] = true;
		});

		force
	    .nodes(nodes)
	    .links(links)
	    .start();
		
		drag = d3.behavior.drag()
	        .on("dragstart", dragstart)
	        .on("drag", dragmove)
	        .on("dragend", dragend);
		
		insertLinks(links); // creating links between nodes
		insertNodes(nodes); // create circles on nodes
		graph.circle();
		zoomEventCall();
		svg.call(zoom);  
		svg.on("dblclick.zoom", null);
	    resize();
		force.on('end', function() {
			$("#wait").css("display", "none");
			$("#switchesgraph").removeClass("hide");
			try{
				var positionsNodes = storage.get('NODES_COORDINATES');
				common.getData('/user/settings','GET').then(function(data){
					positions = data;
						if(positions){
							storage.set('NODES_COORDINATES',positions)
							// control the coordinates here
						    d3.selectAll("g.node").attr("transform", function(d){
						    	try{
						    		d.x = positions[d.switch_id][0];
							    	d.y = positions[d.switch_id][1];
						    	}catch(e){
						    		
						    	}
						    	 return "translate("+d.x+","+d.y+")";
						    });
						    
							tick();
						}
					
				},function(error){
					var errorData = error.responseJSON;
					var ifnoUserDatainDB = errorData && errorData['error-code']=='100001';
					if(positionsNodes && ifnoUserDatainDB ){
						storage.set('isDirtyCordinates', true);
						positions = positionsNodes;
						d3.selectAll("g.node").attr("transform", function(d){
					    	try{
					    		d.x = positions[d.switch_id][0];
						    	d.y = positions[d.switch_id][1];
					    	}catch(e){
					    		
					    	}
					    	 return "translate("+d.x+","+d.y+")";
					    });
					    
						tick();
					}
				})
				
			}catch(e){
				console.log(e);
			} 
			if(zoomFitCall){
				zoomFit(min_zoom, 500);
			}
		});
		
		force.on("tick", tick);

		/* Hide Flows */
		
		var storageValue = cookie.get('RIGHT_CHECKBOXES');
		if(storageValue != null){
			try{
				storageValue = JSON.parse(storageValue);
				updateRightPanel(storageValue);
			}catch(e){
				updateRightPanel(RIGHT_CHECKBOXES);
			}
		}
	},
	circle : function(){
		var filteredLinks = [];
		links.map(function(l,i){
			if(l && l.hasOwnProperty('flow_count')){
				var obj = l;
				obj.index=i;
				filteredLinks.push(obj);
			};
		})
		flow_count = g.selectAll(".flow_count")
	    	.data(filteredLinks)
	    	.enter().append("g").attr("class","flow-circle");
	
		flow_count.append("circle")
	    	.attr("dy", ".35em")
	    	.style("font-size", nominal_text_size + "px")
	
		    .attr("r", function(d, index) {
		    	var element = $("#link" + d.index)[0];
		        var f = d.flow_count;
		        if (element.getAttribute("stroke") == "#228B22" || element.getAttribute("stroke") == "green") {
		        	if(f<10){
		        		r = 10;
		    		}else if(f>=10 && f<100){
		        		r = 12;
		    		}else{
		    			r = 16;
		    		}
		            return r;
		        };
		    }).on("mouseover", function(d, index) {
		        var element = $("#link" + index)[0];
		        if (d.hasOwnProperty("flow_count")) {
		            classes = "link logical overlay";
		        } else {
		        	classes =  "link physical overlay";
		        }
		        element.setAttribute("class", classes);
		
		    }).on("mouseout", function(d, index) {
		        var element = $("#link" + index)[0];
		        if (d.hasOwnProperty("flow_count")) {
		            classes = "link logical";
		        } else {
		        	classes =  "link physical";
		        }
		        element.setAttribute("class", classes);
		    }).on(
		        "click",
		        function(d, index) {
		
		            showFlowDetails(d);
		        })
		    .attr("class", "linecircle")
		    .attr("id", function(d, index) {
		        var id = "_" + index;
		        return id;
		    })
		    .attr("fill", function(d) {
		        return "#d3d3d3";
		    });//.call(force.drag)
		    
		    flow_count.append("text")
		    .attr("dx", function(d) {
		    	var f = d.flow_count;
		    	if(f<10){
	        		r = -3;
	    		}else if(f>=10 && f<100){
	        		r = -6;
	    		}else{
	    			r = -9;
	    		}
		        return r
		    })
		    .attr("dy", function(d) {
		        return 5
		    })
		    .attr("fill", function(d) {
		        return "black";
		    })
		    .text(function(d) {
		        var value = d.flow_count;
		        return value;
		    });
	}
}
function sortLinks() {
	links.sort(function(a, b) {
		if (a.source > b.source) {
			return 1;
		} else if (a.source < b.source) {
			return -1;
		} else {
			if (a.target > b.target) {
				return 1;
			}
			if (a.target < b.target) {
				return -1;
			} else {
				return 0;
			}
		}
	});
}

function setLinkIndexAndNum() {
	
	for (var i = 0; i < links.length; i++) {
		if (i != 0 && links[i].source == links[i - 1].source
				&& links[i].target == links[i - 1].target) {
			links[i].linkindex = links[i - 1].linkindex + 1;
		} else {
			links[i].linkindex = 1;
		}
		// save the total number of links between two nodes
		if (mLinkNum[links[i].target + "," + links[i].source] !== undefined) {
			mLinkNum[links[i].target + "," + links[i].source] = links[i].linkindex;
		} else {
			mLinkNum[links[i].source + "," + links[i].target] = links[i].linkindex;

		}
	}
}		
function isObjEquivalent(a, b) {
    // Create arrays of property names
    var aProps = Object.getOwnPropertyNames(a);
    var bProps = Object.getOwnPropertyNames(b);
    if (aProps.length != bProps.length) {
        return false;
    }

    for (var i = 0; i < aProps.length; i++) {
        var propName = aProps[i];
        if (a[propName] !== b[propName]) {
            return false;
        }
    }

     return true;
}
function tick() {

	var lookup = {};
	link.attr("d", function(d) {
		var islCount = 0;
		var matchedIndex = 1;
		var key = d.source_switch+"_"+d.target_switch;
		if(linksSourceArr && typeof(linksSourceArr[key])!=='undefined'){
			islCount = linksSourceArr[key].length;
		}
		if(islCount > 1){
			linksSourceArr[key].map(function(o,i){
				if(isObjEquivalent(o,d)){
					matchedIndex = i +1;
					return;
				}
			})
		}
		var x1 = d.source.x,
        y1 = d.source.y,
        x2 = d.target.x,
        y2 = d.target.y,
        dx = x2 - x1,
        dy = y2 - y1,
        dr = Math.sqrt(dx * dx + dy * dy),

        // Defaults for normal edge.
        drx = dr,
        dry = dr,
        xRotation = 0, // degrees
        largeArc = 0, // 1 or 0
        sweep = 1; // 1 or 0
		var lTotalLinkNum = mLinkNum[d.source.index + "," +
									d.target.index] ||
								mLinkNum[d.target.index + "," +
									d.source.index];

		if (lTotalLinkNum > 1) {

			dr = dr /
				(1 + (1 / lTotalLinkNum) *
					(d.linkindex - 1));
		}
	   
		// generate svg path
		keysof = Object.keys(d);
		lookup[d.key] = d.flow_count;
		if (lookup[d.Key] == undefined) {
			
			if(islCount == 1){
				return "M" + d.source.x + "," + d.source.y + "L" + d.target.x + "," + d.target.y;
			}else{
				if(islCount %2 !=0 && matchedIndex ==1){
					return "M" + d.source.x + "," + d.source.y + "L" + d.target.x + "," + d.target.y;
				}else if(matchedIndex % 2 ==0){
					return  "M" + d.source.x + "," + d.source.y + "A" + dr +
					"," + dr + " 0 0 1," + d.target.x + "," +
					d.target.y + "A" + dr + "," + dr + " 0 0 0," +
					d.source.x + "," + d.source.y; 
				}else{
					return  "M" + d.source.x + "," + d.source.y + "A" + dr +
					"," + dr + " 0 0 0," + d.target.x + "," +
					d.target.y + "A" + dr + "," + dr + " 0 0 1," +
					d.source.x + "," + d.source.y; 
				}
				
			}
		} else {
			if(d.source_switch == d.target_switch){
				  // Self edge.
		          if ( x1 === x2 && y1 === y2 ) {
		        	// Fiddle with this angle to get loop oriented.
		            xRotation = -45;

		            // Needs to be 1.
		            largeArc = 1;

		            // Change sweep to change orientation of loop. 
		            //sweep = 0;

		            // Make drx and dry different to get an ellipse
		            // instead of a circle.
		            drx = 50;
		            dry = 20;
		            
		            // For whatever reason the arc collapses to a point if the beginning
		            // and ending points of the arc are the same, so kludge it.
		            x2 = x2 + 1;
		            y2 = y2 + 1;
		          } 

				 return "M" + x1 + "," + y1 + "A" + drx + "," + dry + " " + xRotation + "," + largeArc + "," + sweep + " " + x2 + "," + y2;		
			}else{
				return "M" + d.source.x + "," + d.source.y + "L" + d.target.x + "," + d.target.y;
			}
		}

	});
	node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
	
	flow_count.attr("transform", function(d, index) {
		var xvalue = (d.source.y + d.target.y) / 2;
	    var yvalue = (d.source.x + d.target.x) / 2;
	    if(d.source_switch == d.target_switch){
	    	return "translate(" + (yvalue+70) + "," + (xvalue-70) + ")";
	    }else{
	    	return "translate(" + yvalue + "," + xvalue + ")";
	    }
	});
}

function reset() {
	storage.remove("NODES_COORDINATES");
	d3.selectAll('g.node')
    .each(function(d) {
    	var element = document.getElementById("circle_" + d.switch_id);
    	var classes = "circle blue";
		if(d.state && d.state.toLowerCase() == "deactivated"){
			classes = "circle red";
		}
		element.setAttribute("class", classes);
    	d3.select(this).classed("fixed", d.fixed = false);
    });
	force.charge(-1000).resume();
	zoom.scale(min_zoom);
	zoomFit(min_zoom,500);
}

function zoomClick(id) {

	var bounds = g.node().getBBox();
	var parent = g.node().parentElement;
	var fullWidth = $(parent).width(),
		fullHeight = $(parent).height() - 200
	var width = bounds.width,
		height = bounds.height;
	var midX = bounds.x + width / 2,
		midY = bounds.y + height / 2;
	var direction = 1,
    factor = 0.2,
    target_zoom = 1,
    center = [fullWidth / 2 - min_zoom * midX, fullHeight / 2 - min_zoom * midY],//[width / 2, height / 2],
    extent = zoom.scaleExtent(),
    translate = zoom.translate(),
    translate0 = [],
    l = [],
    view = {x: translate[0], y: translate[1], k: zoom.scale()};
   	direction = (id === 'zoom_in') ? 1 : -1;
	target_zoom = zoom.scale() * (1 + factor * direction);
	if (target_zoom < extent[0] || target_zoom > extent[1]) { return false; }
	translate0 = [(center[0] - view.x) / view.k, (center[1] - view.y) / view.k];
	view.k = target_zoom;
	l = [translate0[0] * view.k + view.x, translate0[1] * view.k + view.y];
	view.x += center[0] - l[0];
	view.y += center[1] - l[1];
	interpolateZoom([view.x, view.y], view.k);
}
function zoomed() {
	
	g.attr("transform",
        "translate(" + zoom.translate() + ")" +
        "scale(" + zoom.scale() + ")"
    );
}

function interpolateZoom (translate, scale) {
    var self = this;
    return d3.transition().duration(350).tween("zoom", function () {
        var iTranslate = d3.interpolate(zoom.translate(), translate),
            iScale = d3.interpolate(zoom.scale(), scale);
        return function (t) {
            zoom
                .scale(iScale(t))
                .translate(iTranslate(t));
            zoomed();
        };
    });
}

function dblclick(d,index) {
	var element = document.getElementById("circle_" + d.switch_id);
	var classes = "circle blue";
	if(d.state && d.state.toLowerCase() == "deactivated"){
		classes = "circle red";
	}
    element.setAttribute("class", classes);
    doubleClickTime = new Date();
    d3.select(this).classed("fixed", d.fixed = false);
    showSwitchDetails(d);
    //force.resume();
}
function dragstart(d) {
	force.stop()
	d3.event.sourceEvent.stopPropagation();
	//d3.select(this).classed("fixed", d.fixed = true);
}
function dragmove(d, i) {
	isDragMove = true;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy; 
    tick(); // this is the key to make it work together with updating both px,py,x,y on d !
}

function dragend(d, i) {
	flagHover = false;
	d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    tick();
    //force.resume();
    updateCoordinates();
}


$('#switch_icon').on('click',function(){
	if(RIGHT_CHECKBOXES.SWITCH_CHECKED){
		updateRightPanel({SWITCH_CHECKED : 0});
	}else{
		updateRightPanel({SWITCH_CHECKED : 1});
	}
	
})

function zoomFit(paddingPercent, transitionDuration) {
	var bounds = g.node().getBBox();
	var parent = g.node().parentElement;
	var fullWidth = $(parent).width(),
		fullHeight = $(parent).height();
	var width = bounds.width,
		height = bounds.height;
	var midX = bounds.x + width / 2,
		midY = bounds.y + height / 2;
	if (width == 0 || height == 0) return; // nothing to fit
	//var scale = (paddingPercent || 0.75) / Math.max(width / fullWidth, height / fullHeight);
	var translate = [fullWidth / 2 - min_zoom * midX, fullHeight / 2 - min_zoom * midY];
	zoom.scale(min_zoom).translate(translate)
	g.transition().duration(transitionDuration || 0) // milliseconds
	.attr("transform", "translate(" + zoom.translate() + ")scale(" + zoom.scale() + ")");
	zoomFitCall = false;
	
}

function updateCoordinates(){
	var coordinates = {}
	nodes.forEach(function(d){
		coordinates[d.switch_id] = [Math.round(d.x * 100) / 100, Math.round(d.y * 100) / 100];
	})
	storage.set('isDirtyCordinates', true);
	storage.set('NODES_COORDINATES', coordinates);
}

function resize() {
    var w = window.innerWidth,
    h = window.innerHeight;
    svg.attr("width", width).attr("height", height);
    
    force.size([force.size()[0] + (w - width) / zoom.scale(), force.size()[1] + (h - height) / zoom.scale()]).resume();
    width = w;
    height = h;
}

function showFlowDetails(d) {

	url = 'flows#'+d.source_switch+'|'+d.target_switch;
	window.location = url;
}

/* function to open switchpage page */
function showSwitchDetails(d) {

	localStorage.setItem("switchDetailsJSON", JSON.stringify(d));
	window.location = "switch/details#" + d.switch_id;
}

function showLinkDetails(d) {
	console.log('d',d);
	localStorage.setItem("linkData", JSON.stringify(d));
	url = 'switch/isl';
	window.location = url;
}
var options = {
	      events: {
	        doubleClick: false
	      }
}

//var panzoom = $("svg").svgPanZoom(options);
//localStorage.clear();
var parentRect;
var childRect;
function HorizontallyBound(parentDiv, childDiv) {
    parentRect = parentDiv.getBoundingClientRect();
    childRect = childDiv.getBoundingClientRect();
    return parentRect.left <= childRect.left && parentRect.right >= childRect.right;
}

function searchNode(value) {
	//find the node
	var selectedVal = typeof value !== 'undefined' ? value : document.getElementById('search').value;
	
    selectedVal = $.trim(selectedVal)
    
    if($.inArray(selectedVal, optArray) > -1){
   
	    var node = svg.selectAll(".node");
	    if (selectedVal == "none") {
	        //node.style("stroke", "#666").style("stroke-width", "1");
	    } else {
	    	
	    	d3.selectAll('g.node')
	        .each(function(d) {
	        	var element = document.getElementById("circle_" + d.switch_id);
	        	var classes = "circle blue";
	    		if(d.state && d.state.toLowerCase() == "deactivated"){
	    			classes = "circle red";
	    		}
	    		element.setAttribute("class", classes);
	        });
	    	
	        var unmatched = node.filter(function (d, i) {
	            return d.name != selectedVal;
	        });
	        
	        var matched = node.filter(function (d, i) {
	            return d.name == selectedVal;
	        });
	        
	        unmatched.style("opacity", "0");
	        
	        
	        matched.filter(function (d, index) {
	        	var element = document.getElementById("circle_" + d.switch_id);
			    var classes = "circle blue hover";
				if(d.state && d.state.toLowerCase() == "deactivated"){
					classes = "circle red hover";
				}
			    element.setAttribute("class", classes);
	        });
	        
	        var link = svg.selectAll(".link")
	        link.style("opacity", "0");
	        
	        var circle = svg.selectAll(".flow-circle")
	        circle.style("opacity", "0");
	        
	        d3.selectAll(".node, .link, .flow-circle").transition()
	            .duration(5000)
	            .style("opacity", 1);
       	       
	    }
    }
}


$('#viewISL').click(function(e){
	try{
		var isl = responseData.isl.data;
		var unidirectional = [];
		var failed = [];
		for(var i=0,len=isl.length;i<len;i++){
			if (isl[i].unidirectional && isl[i].state && isl[i].state.toLowerCase()== "discovered"){
				unidirectional.push(isl[i]);
	        } 
			else if(isl[i].state && isl[i].state.toLowerCase()== "failed"){
				failed.push(isl[i]);
			}
		}
		setISLData(failed, "failed-isl-table");
		setISLData(unidirectional, "unidirectional-isl-table");
	}catch(e){
		console.log(e);
	}
});

function setISLData(response,id){
	// destroy table before creating new one
	if ( $.fn.DataTable.isDataTable('#'+id) ) {
		 $('#'+id).DataTable().destroy();
	}
	if(response && response.length){
		$('#'+id+' tbody').empty();
	}
	for(var i = 0; i < response.length; i++) {
		 var tableRow = "<tr id='div_"+(i+1)+"' class='flowDataRow'>"
						 	+"<td class='divTableCell' title ='"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"'>"+((response[i].source_switch_name === "" || response[i].source_switch_name == undefined)?"-":response[i].source_switch_name)+"</td>"
							+"<td class='divTableCell' title ='"+((response[i].source_switch === "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"'>"+((response[i].source_switch === "" || response[i].source_switch == undefined)?"-":response[i].source_switch)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"'>"+((response[i].src_port === "" || response[i].src_port == undefined)?"-":response[i].src_port)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"'>"+((response[i].target_switch_name === "" || response[i].target_switch_name == undefined)?"-":response[i].target_switch_name)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].target_switch === "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"'>"+((response[i].target_switch === "" || response[i].target_switch == undefined)?"-":response[i].target_switch)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"'>"+((response[i].dst_port === "" || response[i].dst_port == undefined)?"-":response[i].dst_port)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].cost === "" || response[i].cost == undefined)?"-":response[i].cost)+"'>"+((response[i].cost === "" || response[i].cost == undefined)?"-":response[i].cost)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"'>"+((response[i].state === "" || response[i].state == undefined)?"-":response[i].state)+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].speed === "" || response[i].speed == undefined)?"-":response[i].speed/1000 +" Mbps")+"'> "+ ((response[i].speed === "" || response[i].speed == undefined)?"-":response[i].speed/1000 + " Mbps")+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].available_bandwidth === "" || response[i].available_bandwidth == undefined)?"-":response[i].available_bandwidth/1000 +" Mbps")+"'> "+ ((response[i].available_bandwidth === "" || response[i].available_bandwidth == undefined)?"-":response[i].available_bandwidth/1000 + " Mbps")+"</td>"
						    +"<td class='divTableCell' title ='"+((response[i].latency === "" || response[i].latency == undefined)?"-":response[i].latency)+"'>"+((response[i].latency === "" || response[i].latency == undefined)?"-":response[i].latency)+"</td>"
						    +"</tr>";
		 
		 	 $('#'+id).append(tableRow);
 	 }
	common.customDataTableSorting();
	 var tableVar  =  $('#'+id).DataTable( {
		 "iDisplayLength": 8,
		 "aLengthMenu": false,
		 "bInfo":false,    
		  "responsive": true,
		  "bSortCellsTop": true,
		  "autoWidth": false,
		  language: {searchPlaceholder: "Search"},
		  "aaSorting": [[0, "asc"]],
		  "aoColumns": [
				  { sWidth: '14%',"sType": "name","bSortable": true },
	              { sWidth:  '8%' },
	              { sWidth: '8%',"sType": "name","bSortable": true },
	              { sWidth: '14%' },
	              { sWidth: '8%' },
	              { sWidth: '8%' },
	              { sWidth: '7%' },
	              { sWidth: '12%' },
	              { sWidth: '12%' },
	              { sWidth: '12%' },
	              { sWidth: '8%' }
		    ],
	      "columnDefs": [
	            {
	                "targets": [ 1 ],
	                "visible": false,
	                "searchable": true
	            },
	            {
	                "targets": [ 4 ],
	                "visible": false,
	                "searchable": true
	            }
	        ] 
	 });
	 
	 tableVar.columns().every( function () {

		 
	 var that = this;
	 $( 'input', this.header() ).on( 'keyup change', function () {
	      if ( that.search() !== this.value ) {
	             that.search(this.value).draw();
	         }
	     } );
	 } );
	 $('#'+id).show();
}
$('.isl_switch_icon').click(function(e){
	var id = $(this).attr("id");
	if(id == "logical"){
		updateRightPanel({FLOW_CHECKED : false,ISL_CHECKED : true});
	}
	if(id == "physical"){
		updateRightPanel({ISL_CHECKED : false,FLOW_CHECKED : true});
		if(responseData.flow.data.length == 0){
			location.reload();
		}
	}

	
});

function showSearch(idname,$event) {
	$event.stopPropagation();
	if($('#'+idname+'.heading_search_box').is(":visible")){
		$('#'+idname+'.heading_search_box').css('display', 'none');
	}else{
		$('#'+idname+'.heading_search_box').css('display', 'inline-block');
	}
}
$(document).ready(function() {
	$('#close_switch_detail').click(function(){
		$('#topology-click-txt').css('display', 'none');
	})
	$('body').on("click", function(e) { 
		$('#topology-hover-txt').css('display', 'none');
		var container = $('.refresh_toggle');
		if (!container.is(e.target) && container.has(e.target).length === 0) 
	    {
			var cssBlock = $('.refresh_list').css('display');	
			if(cssBlock == 'block'){
				$('.refresh_list').slideToggle();
			}
	    }
	});
	$('body').css('pointer-events', 'all');
});

$("#searchbox").click(function (e) {
    $('#ui-container').toggleClass('active');
    e.stopPropagation()
    $('#search').val('').focus();
});
	
$(function() {
	
	$('[data-toggle="tooltip"]').tooltip();
		
	$("#search").enterKey(function () {
		searchNode();
	})
	$('body').click(function(evt){    
	    if(evt.target.id == "ui-container")
	       return;
	    //For descendants of menu_content being clicked, remove this check if you do not want to put constraint on descendants.
	    if($(evt.target).closest('#ui-container').length)
	       return; 
	    if($('#search').is(":visible")){
	    	$('#ui-container').toggleClass('active');
	    }
	   
	});
});
$.fn.enterKey = function (fnc) {
    return this.each(function () {
        $(this).keypress(function (ev) {
            var keycode = (ev.keyCode ? ev.keyCode : ev.which);
            if (keycode == '13') {
                fnc.call(this, ev);
            }
        })
    })
}

function updateRightPanel(obj){
	
	var duration = 500;
	if(obj.hasOwnProperty("SWITCH_CHECKED")){
		RIGHT_CHECKBOXES.SWITCH_CHECKED = obj.SWITCH_CHECKED;
		var element = $(".switchname");
		if(obj.SWITCH_CHECKED){
			$('#switch_icon i').addClass('icon-switch').removeClass('inactive-icon-switch');
			element.addClass("show").removeClass("hide");
		}else{
			$('#switch_icon i').removeClass('icon-switch').addClass('inactive-icon-switch');
			element.removeClass("show").addClass("hide");
		}
	}
	if(obj.hasOwnProperty("ISL_CHECKED")){
		RIGHT_CHECKBOXES.ISL_CHECKED = obj.ISL_CHECKED;
		if(obj.ISL_CHECKED){
			$("#physical").show();
			$("#logical").hide();
		}
		if(obj.ISL_CHECKED){
			d3.selectAll(".physical").transition()
	            .duration(duration)
	            .style("opacity", 1);
		}else{
			d3.selectAll(".physical").transition()
	            .duration(duration)
	            .style("opacity", 0);
		}
	}
	if(obj.hasOwnProperty("FLOW_CHECKED")){ 
		RIGHT_CHECKBOXES.FLOW_CHECKED = obj.FLOW_CHECKED;
		if(obj.FLOW_CHECKED){
			$("#physical").hide();
			$("#logical").show();
		}
		if(obj.FLOW_CHECKED){
			d3.selectAll(".logical,.flow-circle").transition()
	            .duration(duration)
	            .style("opacity", 1);
		}else{
			d3.selectAll(".logical,.flow-circle").transition()
	            .duration(duration)
	            .style("opacity", 0);
		}
	}
	
	if(obj.hasOwnProperty("REFRESH_CHECKED")){
		RIGHT_CHECKBOXES.REFRESH_CHECKED = obj.REFRESH_CHECKED;
		$("#auto_refresh").attr("checked", !!obj.REFRESH_CHECKED);
		$("#auto_refresh").val(!!obj.REFRESH_CHECKED);
		if(obj.REFRESH_CHECKED){
			$('.stop_refresh').removeClass('active');
			$('.refresh_option').removeClass('active');
			$('.refresh_toggle i.icon-refresh-kilda').addClass('active')
			$('#'+obj.REFRESH_INTERVAL+obj.REFRESH_TYPE).addClass('active');
		
		}else{
			$('.stop_refresh').addClass('active');
			$('.refresh_option').removeClass('active');
			$('.refresh_toggle i.icon-refresh-kilda').removeClass('active')
			interval.clearSwitch();
			interval.clearISL();
		}
	}
	
	if(obj.hasOwnProperty("REFRESH_INTERVAL")){
		RIGHT_CHECKBOXES.REFRESH_INTERVAL = obj.REFRESH_INTERVAL;
		
		if(RIGHT_CHECKBOXES.REFRESH_CHECKED && common.isNumeric(RIGHT_CHECKBOXES.REFRESH_INTERVAL)){
			$("#auto_refresh_interval").val(obj.REFRESH_INTERVAL);
			interval.set();
		}
	}	
	
	if(obj.hasOwnProperty("REFRESH_TYPE")){
		RIGHT_CHECKBOXES.REFRESH_TYPE = obj.REFRESH_TYPE;
		if(RIGHT_CHECKBOXES.REFRESH_CHECKED){
			$("#m_s_dropdown").val(obj.REFRESH_TYPE);
			interval.set();
		}
	}
	cookie.set('RIGHT_CHECKBOXES', JSON.stringify(RIGHT_CHECKBOXES));
}
function getNewSwitch(nodes,response){
	var nodesArr ={"added":[],"removed":[]};
	for(var i=0;i<response.length; i++){
		var foundFlag= false;
		for(var j=0;j<nodes.length; j++){
			if(nodes[j].switch_id == response[i].switch_id){
				foundFlag =true;
			}
		}
		if(!foundFlag){
			nodesArr['added'].push(response[i]);
		}
	}
	for(var i=0;i<nodes.length; i++){
		var foundFlag= false;
		for(var j=0;j<response.length; j++){
			if(response[j].switch_id == nodes[i].switch_id){
				foundFlag =true;
			}
		}
		if(!foundFlag){
			nodesArr['removed'].push(nodes[i]);
		}
	}
	return nodesArr;
}
function getNewLinks(links,response){
	var linksArr ={"added":[],"removed":[]};
	for(var i = 0; i < response.length; i++){
		var foundFlag = false;
		for(var j = 0; j < links.length; j++){
			if(links[j].source_switch == response[i].source_switch && links[j].target_switch == response[i].target_switch && links[j].src_port ==  response[i].src_port && links[j].dst_port == response[i].dst_port){
				foundFlag =true;
			}
		}
		if(!foundFlag){
			linksArr['added'].push(response[i]);
		}
	}
	// checking for removed links
	for(var i = 0; i < links.length; i++){
		var foundFlag = false;
		for(var j = 0; j < response.length; j++){
			if(links[i].source_switch == response[j].source_switch && links[i].target_switch == response[j].target_switch && links[i].src_port ==  response[j].src_port && links[i].dst_port == response[j].dst_port){
				foundFlag =true;
			}
		}
		if(!foundFlag){
			linksArr['removed'].push(links[i]);
		}
	}
	return linksArr;
}
function insertNodes(nodes){
	node = g.selectAll(".node").data(nodes);
	node.enter().append("g")
	.attr("class", "node")
	.on("dblclick", dblclick)
	.call(drag);
	node.exit().remove();
circle = node.append("circle").attr("r", radius)
	.attr("class",  function(d, index) {
		var classes = "circle blue";
		if(d.state && d.state.toLowerCase() == "deactivated"){
			classes = "circle red";
		}
	    return classes;
	})
	.attr('id', function(d, index) {
	    return "circle_" + d.switch_id;
	})
	.style("cursor", "move");
	
text = node.append("text").attr("dy", ".35em")
    .style("font-size", nominal_text_size + "px")
	.attr("class", "switchname hide"); 
if (text_center) {
    text.text(function(d) {
            return d.name;
        })
        .style("text-anchor", "middle");
} else {
    text.attr("dx", function(d) {
            return (size(d.size) || nominal_base_node_size);
        })
        .text(function(d) {
            return d.name;
        });
}
images = node.append("svg:image").attr("xlink:href", function(d) {
    return "ui/images/switch.png";
}).attr("x", function(d) {
    return -29;
}).attr("y", function(d) {
    return -29;
}).attr("height", 58).attr("width", 58).attr("id", function(d, index) {
    return "image_" + index;
}).attr("cursor", "pointer").on("click", function(d, index) {
	$('#topology-hover-txt').css('display', 'none');
	
    var cName = document.getElementById("circle_" + d.switch_id).className;
    circleClass = cName.baseVal;

    var element = document.getElementById("circle_" + d.switch_id);
    
    var classes = "circle blue hover";
	if(d.state && d.state.toLowerCase() == "deactivated"){
		classes = "circle red hover";
	}
    element.setAttribute("class", classes);
    var rec = element.getBoundingClientRect();
    if(!isDragMove){
    	 $('#topology-click-txt, #switch_click').css('display', 'block');
		    $('#topology-click-txt').css('top', rec.y + 'px');
		    $('#topology-click-txt').css('left', rec.x + 'px');
		
		    d3.select(".switchdetails_div_click_switch_name").html("<span>" + d.name + "</span>");
		    d3.select(".switchdetails_div_click_controller").html("<span>" + d.switch_id + "</span>");
		    d3.select(".switchdetails_div_click_state").html("<span>" + d.state + "</span>");
		    d3.select(".switchdetails_div_click_address").html("<span>" + d.address + "</span>");
		    d3.select(".switchdetails_div_click_name").html("<span>" + d.switch_id + "</span>");
		    d3.select(".switchdetails_div_click_desc").html("<span>" + d.description + "</span>");
		    var bound = HorizontallyBound(document.getElementById("switchesgraph"), document.getElementById("topology-click-txt"));
		    if(bound){
		    	$("#topology-click-txt").removeClass("left");
		    }else{
		    	var left = rec.x - (300 + 100); // subtract width of tooltip box + circle radius
		    	$('#topology-click-txt').css('left', left + 'px');
		    	$("#topology-click-txt").addClass("left");
		    }
    }else{
    	isDragMove = false;
    }
   
}).on("mouseover", function(d, index) {
	$('#isl_hover').css('display', 'none');
	
    var cName = document.getElementById("circle_" + d.switch_id).className;
    circleClass = cName.baseVal;

    var element = document.getElementById("circle_" + d.switch_id);
    
    var classes = "circle blue hover";
	if(d.state && d.state.toLowerCase() == "deactivated"){
		classes = "circle red hover";
	}
    element.setAttribute("class", classes);
    var rec = element.getBoundingClientRect();
    $('#topology-hover-txt, #switch_hover').css('display', 'block');
    $('#topology-hover-txt').css('top', rec.y + 'px');
    $('#topology-hover-txt').css('left', rec.x + 'px');

    d3.select(".switchdetails_div_switch_name").html("<span>" + d.name + "</span>");
    d3.select(".switchdetails_div_controller").html("<span>" + d.switch_id + "</span>");
    d3.select(".switchdetails_div_state").html("<span>" + d.state + "</span>");
    d3.select(".switchdetails_div_address").html("<span>" + d.address + "</span>");
    d3.select(".switchdetails_div_name").html("<span>" + d.switch_id + "</span>");
    d3.select(".switchdetails_div_desc").html("<span>" + d.description + "</span>");
    var bound = HorizontallyBound(document.getElementById("switchesgraph"), document.getElementById("topology-hover-txt"));
    if(bound){
    	$("#topology-hover-txt").removeClass("left");
    }else{
    	var left = rec.x - (300 + 100); // subtract width of tooltip box + circle radius
    	$('#topology-hover-txt').css('left', left + 'px');
    	$("#topology-hover-txt").addClass("left");
    }
}).on("mouseout", function(d, index) {
	if (flagHover == false) {
        flagHover = true;
    }
    else{
    	var element = document.getElementById("circle_" + d.switch_id);
	    var classes = "circle blue";
		if(d.state && d.state.toLowerCase() == "deactivated"){
			classes = "circle red";
		}
	    element.setAttribute("class", classes);
    }
    if (!$('#topology-hover-txt').is(':hover')) {
		$('#topology-hover-txt, #switch_hover').css('display', 'none');
	}
});
}
function insertLinks(links){
	link = g.selectAll(".link").data(links);
	link.enter().append("path")
	.attr("class", function(d, index) {
        if (d.hasOwnProperty("flow_count")) {
            return "link logical";
        } else {
            if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered" || d.state && d.state.toLowerCase()== "failed"){
            	if(d.affected){
            		return "link physical down dashed_path";
            	}else{
            		return "link physical down";
            	}
            	
            }else{
            	if(d.affected){
            		return "link physical dashed_path";
            	}else{
            		return "link physical";
            	}
                
            }
        }
	})
	.attr("id", function(d, index) {
        return "link" + index;
    })
    .on("mouseover", function(d, index) {
    	$('#switch_hover').css('display', 'none');
	    var element = $("#link" + index)[0];	
        if (d.hasOwnProperty("flow_count")) {
        	if(d.affected){
        		element.setAttribute("class","link logical overlay dashed_path");
        	}else{
        		element.setAttribute("class","link logical overlay");
        	}
        	
        } else {
            if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered" || d.state && d.state.toLowerCase()== "failed"){
            	if(d.affected){
            		element.setAttribute("class","link physical dashed_path pathoverlay");
            	}else{
            		element.setAttribute("class","link physical pathoverlay");
            	}
            	
            }else{
            	if(d.affected){
            		element.setAttribute("class","link physical overlay dashed_path");
            	}else{
            		element.setAttribute("class","link physical overlay");
            	}
            	
            }
            $(element).on('mousemove',function(e){
            	$('#topology-hover-txt').css('top', (e.pageY) + 'px');
			    $('#topology-hover-txt').css('left', (e.pageX) + 'px');
			    var bound = HorizontallyBound(document.getElementById("switchesgraph"), document.getElementById("topology-hover-txt"));
			    if(bound){
			    	$("#topology-hover-txt").removeClass("left");
			    }else{
			    	var left = e.pageX - (300 + 100); // subtract width of tooltip box + circle radius
			    	$('#topology-hover-txt').css('left', left + 'px');
			    	$("#topology-hover-txt").addClass("left");
			    }
            })
            var rec = element.getBoundingClientRect();
		    $('#topology-hover-txt, #isl_hover').css('display', 'block');
		    d3.select(".isldetails_div_source_port").html("<span>" + ((d.src_port=="" || d.src_port == undefined)? "-":d.src_port) + "</span>");
		    d3.select(".isldetails_div_destination_port").html("<span>" + ((d.dst_port=="" || d.dst_port == undefined)? "-":d.dst_port) + "</span>");
		    d3.select(".isldetails_div_source_switch").html("<span>" + ((d.source_switch_name=="" || d.source_switch_name == undefined)? "-":d.source_switch_name) + "</span>");
		    d3.select(".isldetails_div_destination_switch").html("<span>" +  ((d.target_switch_name=="" || d.target_switch_name == undefined)? "-":d.target_switch_name)+ "</span>");
		    d3.select(".isldetails_div_speed").html("<span>" + ((d.speed=="" || d.speed == undefined)? "-":d.speed/1000) + " Mbps</span>");
		    d3.select(".isldetails_div_state").html("<span>" + ((d.state=="" || d.state == undefined)? "-":d.state) + "</span>");
		    d3.select(".isldetails_div_latency").html("<span>" + ((d.latency=="" || d.latency == undefined)? "-":d.latency) + "</span>");
		    d3.select(".isldetails_div_bandwidth").html("<span>" + ((d.available_bandwidth=="" || d.available_bandwidth == undefined)? "-":d.available_bandwidth/1000) + " Mbps</span>");
		    d3.select(".isldetails_div_unidirectional").html("<span>" + ((d.unidirectional=="" || d.unidirectional == undefined)? "-":d.unidirectional) + "</span>"); 
		    d3.select(".isldetails_div_cost").html("<span>" + ((d.cost=="" || d.cost == undefined)? "-":d.cost) + "</span>"); 
		      
        }
    }).on("mouseout", function(d, index) {
        var element = $("#link" + index)[0];
        if (d.hasOwnProperty("flow_count")) {
        	if(d.affected){
        		element.setAttribute("class", "link logical dashed_path");
        	}else{
        		element.setAttribute("class", "link logical");
        	}
        	
        } else {
            if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered" || d.state && d.state.toLowerCase()== "failed"){
               if(d.affected){
            	   element.setAttribute("class","link physical down dashed_path");
               }else{
            	   element.setAttribute("class","link physical down");  
               } 
            }else{
            	if(d.affected){
            		element.setAttribute("class","link physical dashed_path");
            	}else{
            		element.setAttribute("class","link physical");
            	}
                
            }
        }
        
        if (!$('#topology-hover-txt').is(':hover')) {
    		$('#topology-hover-txt, #isl_hover').css('display', 'none');
    	}
    }).on("click", function(d, index) {
        var element = $("#link" + index)[0];
        if (d.hasOwnProperty("flow_count")) {
        	if(d.affected){
        		element.setAttribute("class", "link logical overlay dashed_path");
        	}else{
        		element.setAttribute("class", "link logical overlay");
        	}
        	
        	showFlowDetails(d);
        } else {
            
            if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered" || d.state && d.state.toLowerCase()== "failed"){
                if(d.affected){
                	element.setAttribute("class","link physical pathoverlay dashed_path");
                }else{
                	element.setAttribute("class","link physical pathoverlay");
                }
            }else{
            	if(d.affected){
            		element.setAttribute("class","link physical overlay dashed_path");
            	}else{
            		element.setAttribute("class","link physical overlay");
            	}
                
            }
        	showLinkDetails(d);
        }
    }).attr("stroke", function(d, index) {
    	if (d.hasOwnProperty("flow_count")) {
            return "#228B22";
        } else {
            if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered"){
                return ISL.UNIDIR;
            } else if(d.state && d.state.toLowerCase()== "discovered"){
                return ISL.DISCOVERED;
            }
            
        	return ISL.FAILED;
        }
        
    });
	link.exit().remove();
}


function restartGraphWithNewIsl(newLinks,removedLinks){
	try{
		var result = common.groupBy(newLinks, function(item)
		{
			return [item.source_switch, item.target_switch];
		});
		for(var i=0,len=result.length;i<len;i++){
			var row = result[i];
			
			if(row.length>=1){
				for(var j=0,len1=row.length;j<len1;j++){
					var key= row[j].source_switch+"_"+row[j].target_switch;
					if(typeof(linksSourceArr[key])!=='undefined'){
						linksSourceArr[key].push(row[j]);
					}else{
						linksSourceArr[key] = []
						linksSourceArr[key].push(row[j]);
					}

				}
			}
		}
	}catch(e){
	
	}
	var nodelength = nodes.length;
	var linklength = newLinks.length;
	for (var i = 0; i < nodelength; i++) {
		optArray.push(nodes[i].name);
		for (var j = 0; j < linklength; j++) {
			if(nodes[i].switch_id == newLinks[j]["source_switch"] && nodes[i].switch_id == newLinks[j]["target_switch"]){
				newLinks[j].source = i;
				newLinks[j].target = i;
			}else{
				if (nodes[i].switch_id == newLinks[j]["source_switch"]) {
					newLinks[j].source = i;
				} else if (nodes[i].switch_id == newLinks[j]["target_switch"]) {
					newLinks[j].target = i;
				}
			}
			
		}
	}
	links = links.concat(newLinks);
	// splice removed links 
	if(removedLinks && removedLinks.length){
		links = links.filter(function(d){
			var foundFlag = false;
			for(var i =0; i< removedLinks.length; i++){
				if(d.source_switch == removedLinks[i].source_switch && d.target_switch == removedLinks[i].target_switch && d.src_port ==  removedLinks[i].src_port && d.dst_port == removedLinks[i].dst_port){
					foundFlag= true;
					 var key= d.source_switch+"_"+d.target_switch;
					 linksSourceArr[key].splice(0,1)
					break;
				}	
			}
			return !foundFlag;
		})
	}
	force.nodes(nodes).links(links);
	if (force.alpha() == 0) {
		$("#wait1").css("display", "block");
		$("#switchesgraph").removeClass("show").addClass('hide');
		force.start();
		console.log('adding new links')
		insertLinks(links);
		insertNodes(nodes);
		graph.circle();
		force.on('end',function(){
			$("#wait1").css("display", "none");
			$("#switchesgraph").removeClass("hide").addClass('show');
			try{
				positions = storage.get('NODES_COORDINATES');
				if(positions){
					// control the coordinates here
				    d3.selectAll("g.node").attr("transform", function(d){
				    	try{
				    		d.x = positions[d.switch_id][0];
					    	d.y = positions[d.switch_id][1];
				    	}catch(e){
				    		
				    	}
				    	
				        return "translate("+d.x+","+d.y+")";
				    });
				    
					tick();
				}
			}catch(e){
				console.log(e);
			}
		})
	}
}
var interval = {
	get:function(){
		
		if($("#m_s_dropdown").val() == "m"){
			multiplier = 60000;
		}else{
			multiplier = 1000;
		}
		
		return multiplier * parseInt($("#auto_refresh_interval").val());
	},
	set:function(){
		interval.clearSwitch();
		interval.clearISL();
		switchIntervalId = setTimeout(this.switch, this.get());
		islIntervalId = setTimeout(this.isl, this.get());
	},
	clearSwitch:function(){
		if(switchIntervalId){
			clearTimeout(switchIntervalId);
			switchIntervalId = undefined;
		}
	},	
	clearISL:function(){
		if(islIntervalId){
			clearTimeout(islIntervalId);
			islIntervalId = undefined;
		}
	},	
	switch: function(reloadInterval){
		console.info("Switch API Called after ", $("#auto_refresh_interval").val(), $("#m_s_dropdown").val() == "m" ? "miniute(s)": "second(s)");
		$.ajax({
			url : APP_CONTEXT + "/switch/list",
			type : 'GET',
			success : function(response) {
				if(response)
				{
					var switchArr = [];
					if(nodes.length != response.length){
						// new switch is added
						switchArr = getNewSwitch(nodes,response);
					}
					var newNodes = switchArr['added'];
					var removedNodes = switchArr['removed'];
					nodes.forEach(function(d){
						for(var i=0,len=response.length;i<len;i++){
							if(d.switch_id == response[i].switch_id){
								d.state = response[i].state;
								var classes = "circle blue";
								if(d.state && d.state.toLowerCase() == "deactivated"){
									classes = "circle red";
								}
								var element = document.getElementById("circle_" + d.switch_id);
							    element.setAttribute("class", classes);
							    break;
							}
						}
					});
					if((newNodes && newNodes.length) || (removedNodes && removedNodes.length)){
						if((newNodes && newNodes.length)){
							nodes = nodes.concat(newNodes);
							new_nodes = true;
						}
						if(removedNodes && removedNodes.length){
							new_nodes= true;
							nodes =nodes.filter(function(node){
								var foundFlag =false;
								for(var i =0; i<removedNodes.length; i++){
									if(removedNodes[i].switch_id == node.switch_id){
										foundFlag = true;
										break;
									}
								}
								return !foundFlag;
							})
						}
					}else{
						new_nodes = false;
					}
					if(switchIntervalId){
						interval.clearSwitch(switchIntervalId);
						switchIntervalId = setTimeout(interval.switch, interval.get());
					}
				}	
			},
			dataType : "json"
		});
		
	},
	isl: function(reloadInterval){
		console.info("ISL API Called after ", $("#auto_refresh_interval").val(), $("#m_s_dropdown").val() == "m" ? "miniute(s)": "second(s)");
		$.ajax({
			url : APP_CONTEXT + "/switch/links",
			type : 'GET',
			success : function(response) {
				if(response)
				{ var linksArr =[];
					// compare response to see if new ISL added or removed or nothing happend
					if(links.length !== response.length){
						linksArr = getNewLinks(links,response);
					}
					var newLinks = linksArr['added'] || [];
					var removedLinks = linksArr['removed'] || [];
					links.forEach(function(d,index){ 
						for(var i=0,len=response.length;i<len;i++){
							if(d.source_switch == response[i].source_switch && d.target_switch == response[i].target_switch && d.src_port ==  response[i].src_port && d.dst_port == response[i].dst_port){
									d.state = response[i].state;
									if(response[i].affected){
										d['affected']= response[i].affected;
									}else{
										d['affected']= false;
									}
									d.unidirectional = response[i].unidirectional;
									if (d.unidirectional || d.state && d.state.toLowerCase()== "failed"){
										if(d.affected){
											classes = "link physical down dashed_path";
									    }else{
									    	classes = "link physical down";
									    }
										
					                }else{
					                	if(d.affected){
					                		classes = "link physical dashed_path";
									    }else{
									    	classes = "link physical";
									    }
					                	
					                }
								    var element = document.getElementById("link" + index);
								    
								    var stroke = ISL.FAILED;
								    
								    if (d.unidirectional && d.state && d.state.toLowerCase()== "discovered"){
					                    stroke = ISL.UNIDIR;
					                } else if(d.state && d.state.toLowerCase()== "discovered"){
					                	stroke = ISL.DISCOVERED;
					                }
								    
								    if(element){
								    	element.setAttribute("class", classes);
									    element.setAttribute("stroke", stroke);
								    }
								    
						            
								    break;
								}
							
						}
					});
					if((newLinks && newLinks.length) || (removedLinks && removedLinks.length) || (new_nodes)){
						// calculating nodes
						new_nodes = false;
						restartGraphWithNewIsl(newLinks,removedLinks);
					}
					if(islIntervalId){
						interval.clearISL(islIntervalId);
						islIntervalId = setTimeout(interval.isl, interval.get());
					}
				}	
			},
			dataType : "json"
		});
	}
}

$(document).on('change', '#auto_refresh_interval, #m_s_dropdown', function () {
	updateRightPanel({
		REFRESH_INTERVAL : $("#auto_refresh_interval").val(),
		REFRESH_TYPE : $("#m_s_dropdown").val()
	});
})
function stopAutoRefresh(){
	$("#auto_refresh_interval").val('');
	$('#auto_refresh').val(false);
	$("#m_s_dropdown").val('')
	$('.refresh_option').removeClass('active');
	$('.stop_refresh').addClass('active');
	 updateRightPanel({
			REFRESH_CHECKED : false, 
			REFRESH_INTERVAL : 5,
			REFRESH_TYPE : 's'
		});
}
function toggleRefresh(){
	$('.refresh_list').slideToggle();	 
}
// set refresh value
function setAutoRefresh(interval,type){
	 $("#auto_refresh_interval").val(interval);
	 $("#m_s_dropdown").val(type)
	 $('#auto_refresh').val(true);
	 $('.stop_refresh').removeClass('active');
	 $('.refresh_option').removeClass('active');
	 $(this).addClass('active')
	 updateRightPanel({
			REFRESH_CHECKED : true, 
			REFRESH_INTERVAL : interval,
			REFRESH_TYPE : type
		});
}
function refreshDropdown(){
	var minutes = document.createElement('select');
	minutes.setAttribute('id', 'auto_refresh_interval');
	for (var m=1; m<=60; m++) {
	    var option = document.createElement('option');
	    option.setAttribute('value', m);
	    option.appendChild(document.createTextNode(m));
	    minutes.appendChild(option);
	}
	$("#refresh_dropdown").html(minutes);
}

/* ]]> */