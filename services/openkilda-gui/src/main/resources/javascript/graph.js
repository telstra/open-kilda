/*<![CDATA[*/

/* 3 API's call Switches , switchrelation, flows*/
var responseData = [];
$.ajax({
	url : APP_CONTEXT + "/switch",// switchrelationdata,
	type : 'GET',
	success : function(response) {
		$("#wait").css("display", "none");
		responseData.push(response);
		getLink();
	},
	dataType : "json"
});
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

var w, h, svg, path, circle, text, circleElement, node, nodeEnter, image, force, link, textg, fill, flag, flagHover = true, circleClass;
var doubleClickTime = 0;
var threshold = 500;
function init(data) {

	var nodes = data[0].switches;
	var links = data[1].switchrelation;
	var flows = data[2].flows;

	flows = flows.concat(flows);

	links = links.concat(flows);

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
	// any links with duplicate source and target get an incremented 'linknum'
	for (var i = 0; i < links.length; i++) {
		if (i != 0 && links[i].source == links[i - 1].source
				&& links[i].target == links[i - 1].target) {
			links[i].linknum = links[i - 1].linknum + 1;
		} else {
			links[i].linknum = 1;
		}
	}

	w = window.innerWidth - 190,
	// h = 537;,
	h = window.innerHeight - 100;
	radius = 6;
	fill = d3.scale.category20();
	svg = d3.select('.content').append('svg').attr('width', w)
			.attr('height', h);
	force = d3.layout.force().nodes(nodes).links(links).size([ w, h ])
			.linkDistance(180).charge(-990).gravity(0.12).linkStrength(
					function(l, i) {
						return 1;
					})
			// .on("tick", tick)
			.start();

	node = svg.selectAll("g").data(force.nodes());

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
		/*
		 * var popup = document.getElementById("linkDiv");
		 * $("#linkDiv").html("Src_Port:"+d.src_port+"<br/>Src_Switch:"+d.source_switch+"<br/>Availble_Bandwidth:"+d.available_bandwidth +"<br/>Latency:"+d.latency+"<br/>Dst_Port:"
		 * +d.dst_port+"<br/>Dst_Switch:"+d.target_switch+"<br/>Speed:"+d.speed);
		 * popup.classList.toggle("show");
		 */
	}).on("mouseout", function(d, index) {
		var element = $("#link" + index)[0];
		element.setAttribute("class", "link");
		/*
		 * var popup = document.getElementById("linkDiv");
		 * popup.classList.toggle("show");
		 */
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

	function dragstart(d, i) {
		force.stop()
		force.resume();
		d3.event.sourceEvent.stopPropagation();
	}

	function dragmove(d, i) {
		d.px += d3.event.dx;
		d.py += d3.event.dy;
		d.x += d3.event.dx;
		d.y += d3.event.dy;
		tick();
	}

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
	})// .on("mouseover", function(d, index) {
	// var element = $("#circle" + index)[0];
	// element.setAttribute("class", "nodeover");
	// }).on("mouseout", function(d, index) {
	// var element = $("#circle" + index)[0];
	// element.setAttribute("class", "circle");
	// });

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
					console.log("hello")
				}
			}, threshold);
		}

	}).on("mouseover", function(d, index) {
		console.log("circle" + index)
		var cName = document.getElementById("circle" + index).className;
		circleClass = cName.baseVal;

		var element = $("#circle" + index)[0];
		element.setAttribute("class", "nodeover");

		var element = document.getElementById(d.name);
		element.setAttribute("class", "show");
	}).on("mouseout", function(d, index) {
		if (flagHover == false) {
			flagHover = true;
			// console.log("if")
			// var element = $("#circle"+index)[0];
			// element.setAttribute("class", "circle");
			// circle.classed('fixed', true)
		} else {
			console.log(circleClass)
			if (circleClass == "circle") {
				var element = $("#circle" + index)[0];
				element.setAttribute("class", "circle");
			} else {

			}
		}
		// var element = $("#circle" + index)[0];
		// element.setAttribute("class", "circle");
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
	path.attr("d", function(d) {
		dx = d.target.x - d.source.x;
		dy = d.target.y - d.source.y;
		dr = 280 / d.linknum; // linknum is defined above
		return "M" + d.source.x + "," + d.source.y + "A" + dr + "," + dr
				+ " 0 0,1 " + d.target.x + "," + d.target.y;
	});
	nodeEnter.attr("transform", transform);
	text.attr("transform", function(d) {
		return "translate(" + d.x + "," + d.y + ")";
	});
}

function transform(d) {
	var dx = Math.max(radius, Math.min(w - radius, d.x));
	var dy = Math.max(radius, Math.min(h - radius, d.y));
	return "translate(" + dx + "," + dy + ")";
}

$(function() {
	$("#showDetail").on("click", showdata);
});

function showLinkDetails(d) {
	var modalHeader = "";

	if (d.hasOwnProperty("flowid")) {
		console.log(d)
		var popup = document.getElementById("linkDiv");
		console.log(popup)
		var htmlT = "<div class='form-group row'><label class='col-sm-4 col-form-label'>Flow Id:</label>"
				+ "<p class='col-sm-8'>"
				+ d.flowid
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Source Port:</label>"
				+ "<p class='col-sm-8'>"
				+ d.src_port
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Source Vlan:</label>"
				+ "<p class='col-sm-8'>"
				+ d.src_vlan
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Source Switch:</label>"
				+ "<p class='col-sm-8'>"
				+ d.source_switch
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Target Switch:</label>"
				+ "<p class='col-sm-8'>"
				+ d.target_switch
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Destination Port:</label>"
				+ "<p class='col-sm-8'>"
				+ d.dst_port
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Destination Vlan:</label>"
				+ "<p class='col-sm-8'>"
				+ d.dst_vlan
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Maximum Bandwidth:</label>"
				+ "<p class='col-sm-8'>"
				+ d.maximum_bandwidth
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Status:</label>"
				+ "<p class='col-sm-8'>" + d.status + "</p></div>";

		$("#linkDiv").html();
		modalHeader = "Flow Details";
		$("#myModal div.modal-header>h5.link-modal-title").html("").html(
				modalHeader);
		$("#myModal .modal-body").html(htmlT);
		$("div#myModal").modal("show");

	} else {
		var popup = document.getElementById("linkDiv");
		var htmlT = "<div class='form-group row'><label class='col-sm-4 col-form-label'>Source Port:</label>"
				+ "<p class='col-sm-8'>"
				+ d.src_port
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Source Switch:</label>"
				+ "<p class='col-sm-8'>"
				+ d.source_switch
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Available Bandwidth:</label>"
				+ "<p class='col-sm-8'>"
				+ d.available_bandwidth
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Latency:</label>"
				+ "<p class='col-sm-8'>"
				+ d.latency
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Destination Port:</label>"
				+ "<p class='col-sm-8'>"
				+ d.dst_port
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Destination Switch:</label>"
				+ "<p class='col-sm-8'>"
				+ d.target_switch
				+ "</p></div><div class='form-group row'><label class='col-sm-4 col-form-label'>Speed:</label>"
				+ "<p class='col-sm-8'>" + d.speed + "</p></div>";

		$("#linkDiv").html();
		modalHeader = "Link Details";
		$("#myModal div.modal-header>h5.link-modal-title").html("").html(
				modalHeader);
		$("#myModal .modal-body").html(htmlT);
		$("div#myModal").modal("show");
	}
}

function showSwitchDetails(d) {

	window.location = "switchport#" + d.name;
}

function showdata() {
	$("text").each(function() {
		if ($('input[name="switch"]:checked').length) {
			$(this)[0].setAttribute("class", "show");
		} else {
			$(this)[0].setAttribute("class", "hide");
		}

	});
}

function dblclick(d) {
	var element = $("#circle" + d.index)[0];
	element.setAttribute("class", "circle")
	doubleClickTime = new Date();
	d3.select(this).classed("fixed", d.fixed = false);
}
/*]]>*/