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

var responseData = [];
$.ajax({
	url : APP_CONTEXT + "/switch",
	type : 'GET',
	success : function(response) {
		responseData.push(response);
		console.log(response);
		getLink();
	},
	error : function(errResponse) {
		$("#wait").css("display", "none");
		responseData.push({
			'switches' : []
		});

	},
	dataType : "json"
});

/** function to retrieve link details from link api */
function getLink() {
	$.ajax({
		url : APP_CONTEXT + "/switch/links",
		type : 'GET',
		success : function(response) {
			var tempData = JSON.stringify(response);
			if (tempData == undefined || tempData == "" || tempData == null
					|| tempData == '{}') {
				responseData.push({
					'switchrelation' : []
				});
			} else {
				responseData.push(response);
				console.log(response);

			}
			getFlowCount();
		},
		error : function(errResponse) {
			responseData.push({
				'switchrelation' : []
			});
			getFlow();
		},
		dataType : "json"
	});
}

/*	*//** function to retrieve flow details from flow api */
/*
 * function getFlow() { $.ajax({ url : APP_CONTEXT + "/switch/flows", type :
 * 'GET', success : function(response) { var tempFlowData =
 * JSON.stringify(response); if(tempFlowData == undefined ||tempFlowData
 * ==""||tempFlowData==null||tempFlowData=='{}'){
 * responseData.push({'flows':[]}); }else{ responseData.push(response);
 * console.log(response);
 *  } init(responseData); $("#wait").css("display", "none");
 *  }, error : function(errResponse) {
 * 
 * responseData.push({'flows':[]}); init(responseData);
 *  }, dataType : "json" }); }
 */

function getFlowCount() {
	$.ajax({
		url : APP_CONTEXT + "/switch/flowcount",
		type : 'GET',
		success : function(response) {
			var tempFlowData = JSON.stringify(response);
			if (tempFlowData == undefined || tempFlowData == ""
					|| tempFlowData == null || tempFlowData == '{}') {
				responseData.push({
					'flows' : []
				});
			} else {
				responseData.push(response);
				console.log(response);

			}
			
			$("#wait").css("display", "none");
			$('body').css('pointer-events','all'); 
			init(responseData);
		},
		error : function(errResponse) {

			responseData.push({
				'flows' : []
			});
			init(responseData);

		},
		dataType : "json"
	});
}

// variables declaration
var w, h, svg, path, circle, text, circleElement, node,elem, nodeEnter, image, force, link, textg, fill, links, nodes, flag, flagHover = true, circleClass, mLinkNum = {},elemEnter,linkcirc;
var doubleClickTime = 0;
var threshold = 500;

// When this function executes, the force layout calculations have started.
function init(data) {

	console.log(data)
   
	if (data[0].switches.length == 0 && data[1].switchrelation.length == 0
			&& data[2].length == 0) {
		$.toast({
			heading : 'Topology',
			text : 'No Data Avaliable',
			showHideTransition : 'fade',
			position : 'top-right',
			hideAfter : 6000,
			icon : 'warning'
		})
		return false;
	}

	nodes = [];
	links = [];
	/*
	 * A force layout requires two data arrays. The first array, here named
	 * nodes, contains the object that are the focal point of the visualization.
	 * The second array, called links below, identifies all the links between
	 * the nodes.
	 */
	nodes = data[0].switches;
	links = data[1].switchrelation;
	var flows = data[2];
	if (JSON.stringify(links) == '[]') {

	} else {

		links = links.concat(flows);
	}

	// calculating nodes
	var nodelength = nodes.length;
	for (var i = 0; i < nodelength; i++) {

		// if(links != undefined){
		for (var j = 0; j < links.length; j++) {
			if (nodes[i].name == links[j]["source_switch"]) {
				links[j].source = i;
			} else if (nodes[i].name == links[j]["target_switch"]) {
				links[j].target = i;
			}
		}
		// }else{
		// links=[];
		// }
	}
	sortLinks()
	setLinkIndexAndNum();

	/* Define the dimensions of the visualization. */
	w = window.innerWidth - 190, h = window.innerHeight - 100;

	radius = 6;
	fill = d3.scale.category20();

	/*
	 * creating an SVG container to hold the visualization. We only need to
	 * specify the dimensions for this container.
	 */
	svg = d3.select('.content').append('svg').attr('width', w)
			.attr('height', h);

	/*
	 * create a force layout object and define its properties. Those include the
	 * dimensions of the visualization and the arrays of nodes and links.
	 */
	force = d3.layout.force().nodes(nodes).links(links).size([ w, h ])
			.linkDistance(180).charge(-990).gravity(0.12).linkDistance(150)
			.linkStrength(function(l, i) {
				return 1;
			}).start();

	/*
	 * adding the nodes and links to the visualization. adding the nodes after
	 * the links to ensure that nodes appear on top of links
	 */
	node = svg.selectAll("g").data(force.nodes());

	/* defining links. */
	link = svg.selectAll("g").data(force.links());
	
	linkEnter = link.enter().append("svg:g").call(force.drag)
	.attr("id",function(d, index) {
				return "linkg_" + index;
			})
	path = linkEnter.append("svg:path").attr("class", "link")
	.attr("id",function(d, index) {
				return "link" + index;
			}).on("mouseover",function(d, index) {
				var element = $("#link" + index)[0];
				element.setAttribute("class", "overlay");

//				if (element.getAttribute("stroke") == "#228B22"
//						|| element.getAttribute("stroke") == "green") {
//				}

			}).on("mouseout", function(d, index) {
		var element = $("#link" + index)[0];
		element.setAttribute("class", "link");

	}).on(
			"click",
			function(d, index) {
				var element = $("#link" + index)[0];
				element.setAttribute("class", "overlay");

				if (element.getAttribute("stroke") == "#228B22"
						|| element.getAttribute("stroke") == "green") {
					showFlowDetails(d);
				} else {
					showLinkDetails(d);
				}

			}).attr("stroke", function(d, index) {
		if (d.state == "DISCOVERED") {
			return "#00baff";
		} else {
			if (d.status == "UP" || d.status == "ALLOCATED") {
				return "#228B22";
			} else {
				return "green";
			}
		}

	})
		
		
	var node_drag = d3.behavior.drag().on("dragstart", dragstart).on("drag",
			dragmove).on("dragend", dragend);
		/* function dragstart to execute graph dragstart */
		function dragstart(d, i) {
			force.stop()
			force.resume();
			d3.event.sourceEvent.stopPropagation();
		}

	/* function dragstart to execute graph dragmove */
	function dragmove(d, i) {
		d.px += d3.event.dx;
		d.py += d3.event.dy;
		d.x += d3.event.dx;
		d.y += d3.event.dy;
		tick();
	}

	/* function dragstart to execute graph dragend */
	function dragend(d, i) {
		flagHover = false;
		d.fixed = true;
		tick();
		force.resume();
		// d3.event.sourceEvent.stopPropagation();
	}

	nodeEnter = node.enter().append("svg:g").call(force.drag).attr("id",
			function(d, index) {
				return "g_" + index;
			}).call(node_drag).on("dblclick", dblclick);
	circle = nodeEnter.append("svg:circle").attr("r", 35).attr("class",
			"circle").attr('id', function(nodes, index) {
		circleElement = "circle" + index;
		return "circle" + index;
	})

	text = svg.append("svg:g").selectAll("g").data(force.nodes()).enter()
			.append("svg:g");

	// A copy of the text with a thick white stroke for legibility.
	text.append("svg:text").attr("x", 40).attr("y", ".31em").attr("class",
			"hide").text(function(d) {
		d3.select(this).attr("id", d.name);
		return d.name;
	});

	images = nodeEnter.append("svg:image").attr("xlink:href", function(d) {
		return "images/switch.png";
	}).attr("x", function(d) {
		return -29;
	}).attr("y", function(d) {
		return -29;
	}).attr("height", 58).attr("width", 58).attr("id", function(d, index) {
		return "image_" + index;
	}).attr("cursor", "pointer").on("click", function(d, index) {
		if (d3.event.defaultPrevented)
			return;
		flagHover = true;
		var t0 = new Date();
		if (t0 - doubleClickTime > threshold) {
			setTimeout(function() {
				if (t0 - doubleClickTime > threshold) {
					showSwitchDetails(d);
				}
			}, threshold);
		}

	}).on("mouseover", function(d, index) {
		var cName = document.getElementById("circle" + index).className;
		circleClass = cName.baseVal;

		var element = $("#circle" + index)[0];
		element.setAttribute("class", "nodeover");

		var element = document.getElementById(d.name);
		element.setAttribute("class", "show");
	}).on("mouseout", function(d, index) {
		if (flagHover == false) {
			flagHover = true;

		} else {
			if (circleClass == "circle") {
				var element = $("#circle" + index)[0];
				element.setAttribute("class", "circle");
			} else {

			}
		}
		if (!document.getElementById("showDetail").checked) {
			var element = document.getElementById(d.name);
			element.setAttribute("class", "hide");
		}
	});
	 elem = svg.selectAll("g myCircleText")
	 	.data(force.links())
	 
	 elemEnter = elem.enter().append("svg:g");
	 
	 linkcirc = elemEnter.append("svg:circle")
		.data(force.links())
		.attr("r",function(d,index){
			var element = $("#link" + index)[0];
			if (element.getAttribute("stroke") == "#228B22" || element.getAttribute("stroke") == "green") {

				return 10;
			}
			
		}).on("mouseover", function(d, index) {
			var element = $("#link" + index)[0];
			element.setAttribute("class", "overlay");
			
		}).on("mouseout", function(d, index) {
			var element = $("#link" + index)[0];
			element.setAttribute("class", "link");
		}).on(
				"click",
				function(d, index) {
					
						showFlowDetails(d);
				})
		.attr("class", "linecircle")
		.attr("id",function(d,index){
	  
	    var id = "_"+index;
		
			return id;
		})
		.attr("fill",function(d){
			return "#d3d3d3";
		}).call(force.drag)
		
	
	elemEnter.append("text")
		.attr("dx", function(d){return -3})
		.attr("dy",function(d){return 5})
		.attr("fill",function(d){
			return "black";
		})
		.text(function(d){
			var value = d.flow_count;
			return value;
			});
	
	 	force.on("tick", tick);
	
}


// force.on("tick", tick);
function tick() {
	var dx,dy,keysof;
	var lookup = {};
	path
			.attr(
					"d",
					function(d,i) {
						dx = d.target.x - d.source.x;
						dy = d.target.y- d.source.y;
						var dr = Math.sqrt(dx * dx + dy * dy);
						dr = dr + 180;
						var lTotalLinkNum = mLinkNum[d.source.index + ","
								+ d.target.index]
								|| mLinkNum[d.target.index + ","
										+ d.source.index];

						if (lTotalLinkNum > 1) {

							dr = dr
									/ (1 + (1 / lTotalLinkNum)
											* (d.linkindex - 1));
						}

						// generate svg path
						keysof = Object.keys(d);
							lookup[d.key] = d.flow_count;
						if(lookup[d.Key] == undefined){
							return "M" + d.source.x + "," + d.source.y + "A" + dr
							+ "," + dr + " 0 0 1," + d.target.x + ","
							+ d.target.y + "A" + dr + "," + dr + " 0 0 0,"
							+ d.source.x + "," + d.source.y;
						}else{
							return "M" + d.source.x + "," + d.source.y + "L" + d.target.x + "," + d.target.y;
							}
						
					});

	nodeEnter.attr("transform", transform);
	text.attr("transform", function(d) {
		return "translate(" + d.x + "," + d.y + ")";
	});
	elemEnter.attr("transform", function(d,index) {
		
		
		var pathEl   = d3.select('#link'+index).node();
		var midpoint = pathEl.getPointAtLength(pathEl.getTotalLength()/2);
		var ydata = midpoint.x/2;
		var xvalue = midpoint.y/2;
		var xvalue = (d.source.y + d.target.y)/2;
	  	var yvalue = (d.source.x + d.target.x )/2;
	  	return "translate(" + yvalue + "," +xvalue + ")";
		})
    
	//elemEnter.attr("transform", gtransform);
}

/* function to assign position to the nodes of the switch */
function transform(d) {
	var dx = Math.max(radius, Math.min(w - radius, d.x));
	var dy = Math.max(radius, Math.min(h - radius, d.y));
	return "translate(" + dx + "," + dy + ")";
}
function gtransform(d){
  	var xvalue = (d.source.y + d.target.y)/2;
  	var yvalue = (d.source.x + d.target.x )/2;
  	return "translate(" + yvalue + "," +xvalue + ")";
}
/* function to call showdata method on click of showDetails id */
$(function() {
	$("#showDetail").on("click", showdata);
});

/* function to open isldetails page */
function showLinkDetails(d) {

	localStorage.setItem("linkData", JSON.stringify(d));
	url = 'isldetails';
	window.location = url;
}

function showFlowDetails(d) {

	localStorage.setItem("flowDetailsData", JSON.stringify(d));
	url = 'switchflows';
	window.location = url+'#'+ d.source_switch+'#'+ d.target_switch;
}

/* function to open switchpage page */
function showSwitchDetails(d) {

	window.location = "switchport#" + d.name;
}

/* function to show switch details on mouse hover event */
function showdata() {
	
	$("text").each(function() {
		if ($('input[name="switch"]:checked').length) {
			
			$(this)[0].setAttribute("class", "show");
		
		} else {			
			if($(this)[0].getAttribute("id")) {
				$(this)[0].setAttribute("class", "hide");
			} else {
				$(this)[0].setAttribute("class", "show");
			}
		}
	});
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
/*
 * function to set any links with duplicate source and target get an incremented
 * 'linknum'
 */
function setLinkIndexAndNum() {

	// if(links !=undefined){
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
	// }else{
	// links = [];
	// }
}

/* function dlclick to check if on circle doubleclick event is fired. */
function dblclick(d) {
	var element = $("#circle" + d.index)[0];
	element.setAttribute("class", "circle")
	doubleClickTime = new Date();
	d3.select(this).classed("fixed", d.fixed = false);
}





/* ]]> */