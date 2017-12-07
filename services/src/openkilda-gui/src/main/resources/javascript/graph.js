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
		$("#wait").css("display", "none");
		responseData.push(response);
		getLink();
	},
	dataType : "json"
});


/** function to retrieve link details from link api*/
function getLink() {
	$.ajax({                         		    
		url : APP_CONTEXT + "/switch/links",
		type : 'GET',
		success : function(response) {
			$("#wait").css("display", "none");
			responseData.push(response);
			getFlow();
		},
		dataType : "json"
	});
}

/** function to retrieve flow details from flow api*/
function getFlow() {
	$.ajax({
		url : APP_CONTEXT + "/switch/flows",      
		type : 'GET',
		success : function(response) {
			$("#wait").css("display", "none");
			responseData.push(response);
			init(responseData);
		},
		dataType : "json"
	});
}


//variables declaration
var w, h, svg, path, circle, text, circleElement, node, nodeEnter, image, force, link, textg, fill, links, nodes, flag, flagHover = true, circleClass, mLinkNum = {};
var doubleClickTime = 0;
var threshold = 500;

// When this function executes, the force layout calculations have started.
function init(data) {

	
	/* A force layout requires two data arrays. 
	 * The first array, here named nodes,
	 * contains the object that are the focal point of the visualization.
	 * The second array, called links below, identifies all the links
	 * between the nodes. */
	nodes = data[0].switches;
	links = data[1].switchrelation;
	var flows = data[2].flows;
	flows = flows.concat(flows);
	links = links.concat(flows);

	//calculating nodes
	var nodelength = nodes.length;
	for (var i = 0; i < nodelength; i++) {
		for (var j = 0; j < links.length; j++) {
			if (nodes[i].name == links[j]["source_switch"]) {
				links[j].source = i;
			} else if (nodes[i].name == links[j]["target_switch"]) {
				links[j].target = i;
			}
		}
	}

	sortLinks();
	setLinkIndexAndNum();

	
	/* Define the dimensions of the visualization.*/
	w = window.innerWidth - 190,
	h = window.innerHeight - 100;
	
	
	radius = 6;
	fill = d3.scale.category20();
	
	
	/* creating an SVG container to hold the visualization. 
	 * We only need to specify the dimensions for this container.*/
	svg = d3.select('.content').append('svg').attr('width', w)
			.attr('height', h);
	

	
	/*	create a force layout object and define its properties.
	 * Those include the dimensions of the visualization and the arrays
	 * of nodes and links.*/
	force = d3.layout.force().nodes(nodes).links(links).size([ w, h ])
			.linkDistance(180).charge(-990).gravity(0.12).linkStrength(
					function(l, i) {
						return 1;
					})
			.start();

	
	
	/*adding the nodes and links to the visualization.
	 * adding the nodes after the
	 * links to ensure that nodes appear on top of links*/
	node = svg.selectAll("g").data(force.nodes());

	
	
	/*defining links.*/
	link = svg.selectAll("g").data(force.links());

	linkEnter = link.enter().append("svg:g").call(force.drag).attr("id",
			function(d, index) {
				return "linkg_" + index;
			})
	path = linkEnter.append("svg:path").attr("class", "link").attr("id",
			function(d, index) {
				return "link" + index;
			}).on("mouseover", function(d, index) {
		var element = $("#link" + index)[0];
		element.setAttribute("class", "overlay");

	}).on("mouseout", function(d, index) {
		var element = $("#link" + index)[0];
		element.setAttribute("class", "link");

	}).on("click", function(d, index) {
		var element = $("#link" + index)[0];
		element.setAttribute("class", "overlay");
		showLinkDetails(d);
	}).attr("stroke", function(d, index) {
		if (d.state == "DISCOVERED") {
			return "#00baff";
		} else {
			return "#228B22";
		}

	});

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
//		console.log("circle" + index)
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
//			console.log(circleClass)
			if (circleClass == "circle") {
				var element = $("#circle" + index)[0];
				element.setAttribute("class", "circle");
			} else {

			}
		}
;
		if (!document.getElementById("showDetail").checked) {
			var element = document.getElementById(d.name);
			element.setAttribute("class", "hide");
		}
	});
	force.on("tick", tick);
}

// force.on("tick", tick);
function tick() {
	var dx, dy, dr;
	path
			.attr(
					"d",
					function(d) {
						var dx = d.target.x - d.source.x, dy = d.target.y
								- d.source.y, dr = Math.sqrt(dx * dx + dy * dy);
						 dr = dr+250;
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
						return "M" + d.source.x + "," + d.source.y + "A" + dr
								+ "," + dr + " 0 0 1," + d.target.x + ","
								+ d.target.y + "A" + dr + "," + dr + " 0 0 0,"
								+ d.source.x + "," + d.source.y;
					});

	nodeEnter.attr("transform", transform);
	text.attr("transform", function(d) {
		return "translate(" + d.x + "," + d.y + ")";
	});
}

/*function to assign position to the nodes of the switch */
function transform(d) {
	var dx = Math.max(radius, Math.min(w - radius, d.x));
	var dy = Math.max(radius, Math.min(h - radius, d.y));
	return "translate(" + dx + "," + dy + ")";
}


/*function to call showdata method on click of showDetails id*/
$(function() {
	$("#showDetail").on("click", showdata);
});


/*function to open isldetails page */
function showLinkDetails(d) {

	localStorage.setItem("linkData", JSON.stringify(d));
	url = 'isldetails';
	window.location = url;
}

/*function to open switchpage page */
function showSwitchDetails(d) {

	window.location = "switchport#" + d.name;
}


/*function to show switch details on mouse hover event */
function showdata() {
	$("text").each(function() {
		if ($('input[name="switch"]:checked').length) {
			$(this)[0].setAttribute("class", "show");
		} else {
			$(this)[0].setAttribute("class", "hide");
		}

	});
}


/*function to sort the links by source, then target */
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


/*function to set any links with duplicate source and target get an incremented 'linknum' */
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

/* function dlclick to check if on circle doubleclick event is fired. */
function dblclick(d) {
	var element = $("#circle" + d.index)[0];
	element.setAttribute("class", "circle")
	doubleClickTime = new Date();
	d3.select(this).classed("fixed", d.fixed = false);
}
/* ]]> */