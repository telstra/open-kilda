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
				if(response.length)
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
				common.infoMessage(errResponse.responseJSON["error-message"],'info');
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
				if (response.length) {
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
				if (response.length) {
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

//api.getSwitches();

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
var max_zoom = 5;

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
	.on("dragstart", dragstart);
svg = d3.select("#switchesgraph").append("svg")
	.attr("width", width)
	.attr("height", height)
	.append("g")
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
		    //console.log(rec);
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
	  scale = 1.0;
	  svg.attr("transform", "translate(0,0)scale(1,1)");
	  zoom.scale(scale)
	      .translate([0,0]);
	  zoomFit(0.95, 500);
}

function zoomClick(id) {
    var direction = 1,
        factor = 0.2,
        target_zoom = 1,
        center = [width / 2, height / 2],
        extent = zoom.scaleExtent(),
        translate = zoom.translate(),
        translate0 = [],
        l = [],
        view = {x: translate[0], y: translate[1], k: zoom.scale()};

    direction = (id === 'zoom_in') ? 1 : -1;
    target_zoom = zoom.scale() * (1 + factor * direction);

    //if (target_zoom < extent[0] || target_zoom > extent[1]) { return false; }

    translate0 = [(center[0] - view.x) / view.k, (center[1] - view.y) / view.k];
    view.k = target_zoom;
    l = [translate0[0] * view.k + view.x, translate0[1] * view.k + view.y];

    view.x += center[0] - l[0];
    view.y += center[1] - l[1];

    interpolateZoom([view.x, view.y], view.k);
}

function dblclick(d) {
	var element = $("#circle" + d.index)[0];
    element.setAttribute("class", "circle")
    doubleClickTime = new Date();
    d3.select(this).classed("fixed", d.fixed = false);
}
function dragstart(d) {
	force.stop()
	d3.event.sourceEvent.stopPropagation();
	d3.select(this).classed("fixed", d.fixed = true);
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
	var fullWidth = parent.clientWidth,
		fullHeight = parent.clientHeight-150;
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

	//localStorage.setItem("flowDetailsData", JSON.stringify(d));
	url = 'flows#'+d.source_switch+'|'+d.target_switch;
	window.location = url;
}

/* function to open switchpage page */
function showSwitchDetails(d) {

	window.location = "switch/details#" + d.switch_id;
}

function showLinkDetails(d) {

	localStorage.setItem("linkData", JSON.stringify(d));
	url = 'switch/isl';
	window.location = url;
}
response = [{"switch_id":"00:00:00:22:3d:5a:04:7b11","address":"198.18.196.134:49238","hostname":"198.18.196.134","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:281","address":"198.18.196.135:43227","hostname":"198.18.196.135","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:201","address":"198.18.196.102:35561","hostname":"198.18.196.102","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:a51","address":"198.18.196.70:39245","hostname":"198.18.196.70","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:bc1","address":"198.18.196.71:60816","hostname":"198.18.196.71","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:df1","address":"198.18.193.86:41702","hostname":"198.18.193.86","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:4b1","address":"198.18.195.130:52800","hostname":"198.18.195.130","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:931","address":"198.18.195.131:48174","hostname":"198.18.195.131","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:691","address":"198.18.195.195:54040","hostname":"198.18.195.195","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:6f1","address":"198.18.195.226:36292","hostname":"198.18.195.226","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:011","address":"198.18.195.227:42504","hostname":"198.18.195.227","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:341","address":"198.18.195.228:37178","hostname":"198.18.195.228","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:4f1","address":"198.18.195.162:60395","hostname":"198.18.195.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:911","address":"198.18.199.46:57021","hostname":"198.18.199.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:731","address":"198.18.199.45:46952","hostname":"198.18.199.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f11","address":"198.18.193.62:41548","hostname":"198.18.193.62","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:9d1","address":"198.18.193.61:33219","hostname":"198.18.193.61","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:4c1","address":"198.18.193.60:42787","hostname":"198.18.193.60","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:191","address":"198.18.196.3:54198","hostname":"198.18.196.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:711","address":"198.18.195.34:59221","hostname":"198.18.195.34","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:e11","address":"198.18.195.36:58093","hostname":"198.18.195.36","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:ed1","address":"198.18.195.37:42109","hostname":"198.18.195.37","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:001","address":"198.18.195.38:39268","hostname":"198.18.195.38","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:051","address":"198.18.195.2:44292","hostname":"198.18.195.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:351","address":"198.18.195.3:34371","hostname":"198.18.195.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:2f1","address":"198.18.195.98:60161","hostname":"198.18.195.98","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:411","address":"198.18.195.99:47511","hostname":"198.18.195.99","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:571","address":"198.18.199.206:51888","hostname":"198.18.199.206","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:431","address":"198.18.199.205:41928","hostname":"198.18.199.205","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:a11","address":"198.18.199.238:38076","hostname":"198.18.199.238","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:851","address":"198.18.199.237:47393","hostname":"198.18.199.237","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:971","address":"198.18.193.46:56021","hostname":"198.18.193.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f71","address":"198.18.193.45:45426","hostname":"198.18.193.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:e51","address":"198.18.199.56:44310","hostname":"198.18.199.56","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:3a1","address":"198.18.199.55:39323","hostname":"198.18.199.55","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:381","address":"198.18.199.54:38611","hostname":"198.18.199.54","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:eb1","address":"198.18.199.78:48674","hostname":"198.18.199.78","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:c71","address":"198.18.199.77:55480","hostname":"198.18.199.77","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:641","address":"198.18.199.76:33676","hostname":"198.18.199.76","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:1b1","address":"198.18.199.142:53133","hostname":"198.18.199.142","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:271","address":"198.18.199.141:38878","hostname":"198.18.199.141","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:3c1","address":"198.18.199.140:50128","hostname":"198.18.199.140","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:211","address":"198.18.199.110:36434","hostname":"198.18.199.110","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:cd1","address":"198.18.199.109:44015","hostname":"198.18.199.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:341","address":"198.18.199.108:46646","hostname":"198.18.199.108","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:301","address":"198.18.129.109:51303","hostname":"198.18.129.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:991","address":"198.18.196.133:33227","hostname":"198.18.196.133","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:991","address":"198.18.196.100:38463","hostname":"198.18.196.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:8d1","address":"198.18.196.101:42911","hostname":"198.18.196.101","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:931","address":"198.18.196.69:49761","hostname":"198.18.196.69","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:4a1","address":"198.18.196.164:55067","hostname":"198.18.196.164","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:08:7d1","address":"198.18.196.163:46926","hostname":"198.18.196.163","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:07:9f1","address":"198.18.196.162:52624","hostname":"198.18.196.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:d11","address":"198.18.196.2:46285","hostname":"198.18.196.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:391","address":"198.18.195.194:34105","hostname":"198.18.195.194","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:101","address":"198.18.195.4:59998","hostname":"198.18.195.4","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:601","address":"198.18.199.28:36300","hostname":"198.18.199.28","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:591","address":"198.18.199.29:53474","hostname":"198.18.199.29","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:631","address":"198.18.193.87:60394","hostname":"198.18.193.87","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:001","address":"198.18.195.100:56192","hostname":"198.18.195.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:811","address":"198.18.193.151:55178","hostname":"198.18.193.151","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:91:341","address":"198.18.193.5:52407","hostname":"198.18.193.5","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:43:121","address":"198.18.193.6:60760","hostname":"198.18.193.6","description":"NoviFlow Inc OF_13 NW400 .3 .5 "},{"switch_id":"00:00:00:22:3d:5a:04:7b","address":"198.18.196.134:49238","hostname":"198.18.196.134","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:28","address":"198.18.196.135:43227","hostname":"198.18.196.135","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:20","address":"198.18.196.102:35561","hostname":"198.18.196.102","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:a5","address":"198.18.196.70:39245","hostname":"198.18.196.70","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:bc","address":"198.18.196.71:60816","hostname":"198.18.196.71","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:df","address":"198.18.193.86:41702","hostname":"198.18.193.86","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:4b","address":"198.18.195.130:52800","hostname":"198.18.195.130","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:93","address":"198.18.195.131:48174","hostname":"198.18.195.131","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:69","address":"198.18.195.195:54040","hostname":"198.18.195.195","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:6f","address":"198.18.195.226:36292","hostname":"198.18.195.226","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:01","address":"198.18.195.227:42504","hostname":"198.18.195.227","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:34","address":"198.18.195.228:37178","hostname":"198.18.195.228","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:4f","address":"198.18.195.162:60395","hostname":"198.18.195.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:91","address":"198.18.199.46:57021","hostname":"198.18.199.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:73","address":"198.18.199.45:46952","hostname":"198.18.199.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f1","address":"198.18.193.62:41548","hostname":"198.18.193.62","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:9d","address":"198.18.193.61:33219","hostname":"198.18.193.61","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:4c","address":"198.18.193.60:42787","hostname":"198.18.193.60","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:19","address":"198.18.196.3:54198","hostname":"198.18.196.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:71","address":"198.18.195.34:59221","hostname":"198.18.195.34","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:e1","address":"198.18.195.36:58093","hostname":"198.18.195.36","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:ed","address":"198.18.195.37:42109","hostname":"198.18.195.37","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:00","address":"198.18.195.38:39268","hostname":"198.18.195.38","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:05","address":"198.18.195.2:44292","hostname":"198.18.195.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:35","address":"198.18.195.3:34371","hostname":"198.18.195.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:2f","address":"198.18.195.98:60161","hostname":"198.18.195.98","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:41","address":"198.18.195.99:47511","hostname":"198.18.195.99","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:57","address":"198.18.199.206:51888","hostname":"198.18.199.206","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:43","address":"198.18.199.205:41928","hostname":"198.18.199.205","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:a1","address":"198.18.199.238:38076","hostname":"198.18.199.238","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:85","address":"198.18.199.237:47393","hostname":"198.18.199.237","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:97","address":"198.18.193.46:56021","hostname":"198.18.193.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f7","address":"198.18.193.45:45426","hostname":"198.18.193.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:e5","address":"198.18.199.56:44310","hostname":"198.18.199.56","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:3a","address":"198.18.199.55:39323","hostname":"198.18.199.55","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:38","address":"198.18.199.54:38611","hostname":"198.18.199.54","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:eb","address":"198.18.199.78:48674","hostname":"198.18.199.78","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:c7","address":"198.18.199.77:55480","hostname":"198.18.199.77","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:64","address":"198.18.199.76:33676","hostname":"198.18.199.76","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:1b","address":"198.18.199.142:53133","hostname":"198.18.199.142","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:27","address":"198.18.199.141:38878","hostname":"198.18.199.141","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:3c","address":"198.18.199.140:50128","hostname":"198.18.199.140","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:21","address":"198.18.199.110:36434","hostname":"198.18.199.110","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:cd","address":"198.18.199.109:44015","hostname":"198.18.199.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:34","address":"198.18.199.108:46646","hostname":"198.18.199.108","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:30","address":"198.18.129.109:51303","hostname":"198.18.129.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:99","address":"198.18.196.133:33227","hostname":"198.18.196.133","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:99","address":"198.18.196.100:38463","hostname":"198.18.196.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:8d","address":"198.18.196.101:42911","hostname":"198.18.196.101","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:93","address":"198.18.196.69:49761","hostname":"198.18.196.69","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:4a","address":"198.18.196.164:55067","hostname":"198.18.196.164","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:08:7d","address":"198.18.196.163:46926","hostname":"198.18.196.163","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:07:9f","address":"198.18.196.162:52624","hostname":"198.18.196.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:d1","address":"198.18.196.2:46285","hostname":"198.18.196.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:39","address":"198.18.195.194:34105","hostname":"198.18.195.194","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:10","address":"198.18.195.4:59998","hostname":"198.18.195.4","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:60","address":"198.18.199.28:36300","hostname":"198.18.199.28","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:59","address":"198.18.199.29:53474","hostname":"198.18.199.29","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:63","address":"198.18.193.87:60394","hostname":"198.18.193.87","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:00","address":"198.18.195.100:56192","hostname":"198.18.195.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:81","address":"198.18.193.151:55178","hostname":"198.18.193.151","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:91:34","address":"198.18.193.5:52407","hostname":"198.18.193.5","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:43:12","address":"198.18.193.6:60760","hostname":"198.18.193.6","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:7b112","address":"198.18.196.134:49238","hostname":"198.18.196.134","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:282","address":"198.18.196.135:43227","hostname":"198.18.196.135","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:202","address":"198.18.196.102:35561","hostname":"198.18.196.102","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:a52","address":"198.18.196.70:39245","hostname":"198.18.196.70","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:bc2","address":"198.18.196.71:60816","hostname":"198.18.196.71","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:df2","address":"198.18.193.86:41702","hostname":"198.18.193.86","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:4b2","address":"198.18.195.130:52800","hostname":"198.18.195.130","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:932","address":"198.18.195.131:48174","hostname":"198.18.195.131","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:692","address":"198.18.195.195:54040","hostname":"198.18.195.195","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:6f2","address":"198.18.195.226:36292","hostname":"198.18.195.226","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:012","address":"198.18.195.227:42504","hostname":"198.18.195.227","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:342","address":"198.18.195.228:37178","hostname":"198.18.195.228","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:4f2","address":"198.18.195.162:60395","hostname":"198.18.195.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:912","address":"198.18.199.46:57021","hostname":"198.18.199.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:732","address":"198.18.199.45:46952","hostname":"198.18.199.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f12","address":"198.18.193.62:41548","hostname":"198.18.193.62","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:9d2","address":"198.18.193.61:33219","hostname":"198.18.193.61","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:4c2","address":"198.18.193.60:42787","hostname":"198.18.193.60","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:192","address":"198.18.196.3:54198","hostname":"198.18.196.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:712","address":"198.18.195.34:59221","hostname":"198.18.195.34","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:e12","address":"198.18.195.36:58093","hostname":"198.18.195.36","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:ed2","address":"198.18.195.37:42109","hostname":"198.18.195.37","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:002","address":"198.18.195.38:39268","hostname":"198.18.195.38","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:052","address":"198.18.195.2:44292","hostname":"198.18.195.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:352","address":"198.18.195.3:34371","hostname":"198.18.195.3","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:2f2","address":"198.18.195.98:60161","hostname":"198.18.195.98","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:412","address":"198.18.195.99:47511","hostname":"198.18.195.99","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:572","address":"198.18.199.206:51888","hostname":"198.18.199.206","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:432","address":"198.18.199.205:41928","hostname":"198.18.199.205","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:a12","address":"198.18.199.238:38076","hostname":"198.18.199.238","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:852","address":"198.18.199.237:47393","hostname":"198.18.199.237","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:972","address":"198.18.193.46:56021","hostname":"198.18.193.46","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:f72","address":"198.18.193.45:45426","hostname":"198.18.193.45","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:e52","address":"198.18.199.56:44310","hostname":"198.18.199.56","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:3a2","address":"198.18.199.55:39323","hostname":"198.18.199.55","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:382","address":"198.18.199.54:38611","hostname":"198.18.199.54","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:eb2","address":"198.18.199.78:48674","hostname":"198.18.199.78","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:c72","address":"198.18.199.77:55480","hostname":"198.18.199.77","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:642","address":"198.18.199.76:33676","hostname":"198.18.199.76","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:1b2","address":"198.18.199.142:53133","hostname":"198.18.199.142","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:272","address":"198.18.199.141:38878","hostname":"198.18.199.141","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:3c2","address":"198.18.199.140:50128","hostname":"198.18.199.140","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:212","address":"198.18.199.110:36434","hostname":"198.18.199.110","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:03:cd2","address":"198.18.199.109:44015","hostname":"198.18.199.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:342","address":"198.18.199.108:46646","hostname":"198.18.199.108","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:302","address":"198.18.129.109:51303","hostname":"198.18.129.109","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:992","address":"198.18.196.133:33227","hostname":"198.18.196.133","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:992","address":"198.18.196.100:38463","hostname":"198.18.196.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:8d2","address":"198.18.196.101:42911","hostname":"198.18.196.101","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:01:932","address":"198.18.196.69:49761","hostname":"198.18.196.69","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6b:00:4a2","address":"198.18.196.164:55067","hostname":"198.18.196.164","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:08:7d2","address":"198.18.196.163:46926","hostname":"198.18.196.163","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:07:9f2","address":"198.18.196.162:52624","hostname":"198.18.196.162","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:d12","address":"198.18.196.2:46285","hostname":"198.18.196.2","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:392","address":"198.18.195.194:34105","hostname":"198.18.195.194","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:102","address":"198.18.195.4:59998","hostname":"198.18.195.4","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:602","address":"198.18.199.28:36300","hostname":"198.18.199.28","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:02:592","address":"198.18.199.29:53474","hostname":"198.18.199.29","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:632","address":"198.18.193.87:60394","hostname":"198.18.193.87","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:6c:00:002","address":"198.18.195.100:56192","hostname":"198.18.195.100","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:00:22:3d:5a:04:812","address":"198.18.193.151:55178","hostname":"198.18.193.151","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:91:342","address":"198.18.193.5:52407","hostname":"198.18.193.5","description":"NoviFlow Inc OF_13 NW400.3.5"},{"switch_id":"00:00:09:35:ae:8d:43:122","address":"198.18.193.6:60760","hostname":"198.18.193.6","description":"NoviFlow Inc OF_13 NW400 .3 .5 "}]

responseData.push(response);
response = [{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:99","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:6c:00:28","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:8d","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:6c:00:20","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:99","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:6c:00:20","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:93","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:01:a5","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:93","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:01:a5","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:63","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:df","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:63","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:03:df","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:93","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:4b","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:93","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:4b","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:39","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:69","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:04:39","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:69","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6b:00:34","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:6f","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:01","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:6f","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6b:00:34","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:03:01","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:73","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:91","speed":40000000},{"src_port":5,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:9d","available_bandwidth":0,"dst_port":5,"target_switch":"00:00:00:22:3d:5a:03:91","speed":10000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:73","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:03:91","speed":40000000},{"src_port":5,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:f1","available_bandwidth":0,"dst_port":5,"target_switch":"00:00:00:22:3d:5a:03:73","speed":10000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:4c","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:03:f1","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:4c","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:03:f1","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:4c","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:9d","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:4c","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:03:9d","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:d1","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:02:19","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:d1","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:02:19","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:ed","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:02:71","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6b:00:00","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:01:e1","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:01:ed","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:01:e1","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:10","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:02:05","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:35","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:02:05","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:35","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:02:05","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:10","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:02:35","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:00","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:02:2f","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:41","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:02:2f","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:02:41","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:02:2f","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:00","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:02:41","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:43","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:57","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:43","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:57","speed":40000000},{"src_port":28,"latency":0,"source_switch":"00:00:00:22:3d:5a:07:9f","available_bandwidth":0,"dst_port":28,"target_switch":"00:00:00:22:3d:5a:03:43","speed":10000000},{"src_port":28,"latency":0,"source_switch":"00:00:00:22:3d:5a:08:7d","available_bandwidth":0,"dst_port":28,"target_switch":"00:00:00:22:3d:5a:02:a1","speed":10000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:85","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:02:a1","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:85","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:02:a1","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:f7","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:03:97","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:f7","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:97","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6b:00:38","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:03:e5","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6b:00:3a","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:e5","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:c7","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:03:eb","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:5a:03:c7","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:03:eb","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:64","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:03:c7","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:64","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:03:c7","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:3c","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:1b","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:3c","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:1b","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:3c","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:04:27","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:3c","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:04:27","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:34","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:04:21","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:34","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:04:21","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:34","available_bandwidth":0,"dst_port":4,"target_switch":"00:00:00:22:3d:5a:03:cd","speed":40000000},{"src_port":3,"latency":0,"source_switch":"00:00:00:22:3d:6c:00:34","available_bandwidth":0,"dst_port":3,"target_switch":"00:00:00:22:3d:5a:03:cd","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:5a:07:9f","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:6b:00:4a","speed":40000000},{"src_port":4,"latency":0,"source_switch":"00:00:00:22:3d:5a:08:7d","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:6b:00:4a","speed":40000000},{"src_port":2,"latency":0,"source_switch":"00:00:00:22:3d:5a:07:9f","available_bandwidth":0,"dst_port":2,"target_switch":"00:00:00:22:3d:5a:08:7d","speed":40000000},{"src_port":1,"latency":0,"source_switch":"00:00:00:22:3d:5a:07:9f","available_bandwidth":0,"dst_port":1,"target_switch":"00:00:00:22:3d:5a:08:7d","speed":40000000}]
responseData.push(response);

response = [{"source_switch":"00:00:00:22:3d:6c:00:28","target_switch":"00:00:00:22:3d:5a:04:99","flow_count":2},{"source_switch":"00:00:00:22:3d:6c:00:20","target_switch":"00:00:00:22:3d:5a:01:8d","flow_count":1},{"source_switch":"00:00:00:22:3d:5a:04:4b","target_switch":"00:00:00:22:3d:5a:04:93","flow_count":3}]

responseData.push(response);
graph.init(responseData);


/* ]]> */