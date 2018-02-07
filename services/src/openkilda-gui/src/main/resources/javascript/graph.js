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

var api = {
	getSwitches: function(){
		$.ajax({
			url : APP_CONTEXT + "/switch/list",
			type : 'GET',
			success : function(response) {
				if(response)
				{
					responseData.push(response);
					api.getLink();
				}
				else
				{
					$("#wait").css("display", "none");
					common.infoMessage('No Switch Avaliable','info');
				}
			},
			error : function(errResponse) {
				common.infoMessage('No Switch Avaliable','info');
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
					responseData.push(response);
				} 
				api.getFlowCount();
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
						if(response[i]["source_switch"] != response[i]["target_switch"]){
							flows.push(response[i]);
						}
					}
					
					responseData.push(flows);
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
var min_zoom = 0.5;
var max_zoom = 3;

var scale = 1.0;

var mLinkNum ={};
var linkedByIndex = {};
var nodes = [], links = [], flows = [];

var margin = {top: -5, right: -5, bottom: -5, left: -5},
	width = window.innerWidth,
	height = window.innerHeight,
	radius = 35,
	zoom, force, drag, svg,  link, node, text, flow_count;
var size = d3.scale.pow().exponent(1)
.domain([1, 100])
.range([8, 24]);
zoom = d3.behavior.zoom()
	.scale(scale)
	.scaleExtent([min_zoom, max_zoom])
	//.on("zoom", redraw);
//create force layout
force = d3.layout.force()
	.charge(-990)
	.linkDistance(160)
	.size([width, height])
	.on("tick", tick);



drag = force.drag()
	.on("dragstart", dragstart)
	.on("dragend", dragend);
svg = d3.select("#switchesgraph").append("svg")
	.attr("width", width)
	.attr("height", height)
	.append("g")
	.attr("class", "svg_g")
	.call(zoom)
	.on("dblclick.zoom", null)
	//.style("cursor", "move");

svg.append("rect")
.attr("class", "graphoverlay")
.attr("width", width)
.attr("height", height);

link = svg.selectAll(".link"),
node = svg.selectAll(".node");


graph = {

	init: function(data){
	    
		if (data[0].length == 0 && data[1].length == 0 && data[2].length == 0) {
			common.infoMessage('No Data Avaliable','info');
			return false;
		}

		
		/*
		 * A force layout requires two data arrays. The first array, here named
		 * nodes, contains the object that are the focal point of the visualization.
		 * The second array, called links below, identifies all the links between
		 * the nodes.
		 */
		nodes = data[0];
		links = data[1] == undefined ? [] : data[1];
		flows = data[2] == undefined ? [] : data[2];
		if (links.length>0 && flows.length>0) {
			links = links.concat(flows);
		}

		// calculating nodes
		var nodelength = nodes.length;
		var linklength = links.length;
		for (var i = 0; i < nodelength; i++) {
			for (var j = 0; j < linklength; j++) {
				
				if (nodes[i].switch_id == links[j]["source_switch"]) {
					links[j].source = i;
				} else if (nodes[i].switch_id == links[j]["target_switch"]) {
					links[j].target = i;
				}
				
			}
		}
		sortLinks();
		setLinkIndexAndNum();
		
		links.forEach(function (d) {
			linkedByIndex[d.source + "," + d.target] = true;
		});
		
		force
			.nodes(nodes)
			.links(links)
			.start();
		
		resize();
		//window.focus();
		d3.select(window).on("resize", resize);
		
		node = node.data(nodes)
			.enter().append("g")
			.attr("class", "node")
			.on("dblclick", dblclick)
			.call(drag);
		circle = node.append("circle")
			.attr("r", radius)
			.attr("class", "circle")
			.attr('id', function(nodes, index) {
			    circleElement = "circle" + index;
			    return "circle" + index;
			})
			.style("cursor", "move");
			
			
		text = node.append("text")
			.attr("dy", ".35em")
	        .style("font-size", nominal_text_size + "px")
			.attr("class", "switchname hide"); 
		if (text_center) {
		    text.text(function(d) {
		            return d.switch_id;
		        })
		        .style("text-anchor", "middle");
		} else {
		    text.attr("dx", function(d) {
		            return (size(d.size) || nominal_base_node_size);
		        })
		        .text(function(d) {
		            return d.switch_id;
		        });
		}
		
		images = node.append("svg:image").attr("xlink:href", function(d) {
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
		    var rec = element.getBoundingClientRect();
		    $('#topology-hover-txt').css('display', 'block');
		    $('#topology-hover-txt').css('top', rec.y + 'px');
		    $('#topology-hover-txt').css('left', rec.x + 'px');
		
		
		    d3.select(".switchdetails_div_controller").html("<span>" + d.switch_id + "</span>");
		    d3.select(".switchdetails_div_address").html("<span>" + d.address + "</span>");
		    d3.select(".switchdetails_div_name").html("<span>" + d.switch_id + "</span>");
		    d3.select(".switchdetails_div_desc").html("<span>" + d.description + "</span>");
		
		
		}).on("mouseout", function(d, index) {
		    $('#topology-hover-txt').css('display', 'none');
		    if (flagHover == false) {
		        flagHover = true;
		
		    } else {
		        if (circleClass == "circle") {
		            var element = $("#circle" + index)[0];
		            element.setAttribute("class", "circle");
		        } else {
		
		        }
		    }
		
		});
		
		
		link = link.data(links)
			.enter().append("path")
			.attr("class", "link")
			.attr("id", function(d, index) {
		        return "link" + index;
		    })
		    .attr("class", "link")
		    .on("mouseover", function(d, index) {
		        var element = $("#link" + index)[0];
		        element.setAttribute("class", "overlay");
	
		    }).on("mouseout", function(d, index) {
		        var element = $("#link" + index)[0];
		        element.setAttribute("class", "link");
	
		    }).on(
		        "click",
		        function(d, index) {
		            var element = $("#link" + index)[0];
		            element.setAttribute("class", "overlay");
	
		            if (element.getAttribute("stroke") == "#228B22" ||
		                element.getAttribute("stroke") == "green") {
		                showFlowDetails(d);
		            } else {
		                showLinkDetails(d);
		            }
	
		        }).attr("stroke", function(d, index) {
		        if (d.hasOwnProperty("flow_count")) {
		            return "#228B22";
		        } else {
		            return "#00baff";
		        }
	
		    });
		
		flow_count = svg.selectAll(".flow_count")
	    	.data(links)
	    	.enter().append("g");
	
		flow_count.append("circle")
	    .attr("dy", ".35em")
	    .style("font-size", nominal_text_size + "px")
	
	
	    .attr("r", function(d, index) {
	        var element = $("#link" + index)[0];
	        if (element.getAttribute("stroke") == "#228B22" || element.getAttribute("stroke") == "green") {
	
	            return 10;
	        };
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
	    .attr("id", function(d, index) {
	        var id = "_" + index;
	        return id;
	    })
	    .attr("fill", function(d) {
	        return "#d3d3d3";
	    }).call(force.drag)
	
	
	flow_count.append("text")
	    .attr("dx", function(d) {
	        return -3
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
		force.on('end', function() {
			$("#wait").css("display", "none");
			$("#switchesgraph").removeClass("hide");
			if(zoomFitCall){
				zoomFit(0.95, 500);
			}
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
function tick() {

	var lookup = {};
	link.attr("d", function(d) {
		dx = d.target.x - d.source.x;
		dy = d.target.y - d.source.y;
		var dr = Math.sqrt(dx * dx + dy * dy);
		dr = dr + 180;
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
			return "M" + d.source.x + "," + d.source.y + "A" + dr +
				"," + dr + " 0 0 1," + d.target.x + "," +
				d.target.y + "A" + dr + "," + dr + " 0 0 0," +
				d.source.x + "," + d.source.y;
		} else {
			return "M" + d.source.x + "," + d.source.y + "L" + d.target.x + "," + d.target.y;
		}

	});
	node.attr("transform", function(d) { return "translate(" + d.x + "," + d.y + ")"; });
	
	flow_count.attr("transform", function(d, index) {
	    var pathEl = d3.select('#link' + index).node();
	    var midpoint = pathEl.getPointAtLength(pathEl.getTotalLength() / 2);
	    var ydata = midpoint.x / 2;
	    var xvalue = midpoint.y / 2;
	    var xvalue = (d.source.y + d.target.y) / 2;
	    var yvalue = (d.source.x + d.target.x) / 2;
	    return "translate(" + yvalue + "," + xvalue + ")";
	});
}
function redraw() {
	svg.attr("transform", "translate(" + d3.event.translate + ")" +
		" scale(" + d3.event.scale + ")");
}

function zoomed() {
	
	svg.attr("transform",
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
function reset() {
	panzoom.reset();
}

function zoomClick(id) {
	
	if(id === 'zoom_in'){
		panzoom.zoomIn()
	}else{
		panzoom.zoomOut();
	}
}

function dblclick(d) {
	var element = $("#circle" + d.index)[0];
    element.setAttribute("class", "circle")
    doubleClickTime = new Date();
    d3.select(this).classed("fixed", d.fixed = false);
    force.resume();
}
function dragstart(d) {
	force.stop()
	d3.event.sourceEvent.stopPropagation();
	d3.select(this).classed("fixed", d.fixed = true);
}
function dragend(d, i) {
    flagHover = false;
    d.fixed = true;
    tick();
    force.resume();
    // d3.event.sourceEvent.stopPropagation();
}
$("#showDetail").on("click", function(){
	checked = $('input[name="switch"]:checked').length; 
	var element = $(".switchname");
	
	if(checked){
		element.addClass("show").removeClass("hide");
	}else{
		element.removeClass("show").addClass("hide");
	}
});

function zoomFit(paddingPercent, transitionDuration) {
	var bounds = svg.node().getBBox();
	var parent = svg.node().parentElement;
	var fullWidth = $(parent).width(),
		fullHeight = $(parent).height()-150;
	var width = bounds.width,
		height = bounds.height;
	var midX = bounds.x + width / 2,
		midY = bounds.y + height / 2;
	if (width == 0 || height == 0) return; // nothing to fit
	var scale = (paddingPercent || 0.75) / Math.max(width / fullWidth, height / fullHeight);
	var translate = [fullWidth / 2 - scale * midX, fullHeight / 2 - scale * midY];
	
	
	
	zoom.scale(scale).translate(translate);
	
	svg.transition().duration(transitionDuration || 0) // milliseconds
	.attr("transform", "translate(" + translate + ")scale(" + scale + ")");

	zoomFitCall = false;
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

	localStorage.setItem("linkData", JSON.stringify(d));
	url = 'switch/isl';
	window.location = url;
}

var panzoom = $("svg").svgPanZoom();
localStorage.clear();
/* ]]> */