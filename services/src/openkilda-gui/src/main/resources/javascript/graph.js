/*<![CDATA[*/

/**
 * Below the javascript/ajax/jquery code to generate 
 * the force directed graph based on 
 * three api calls
 * @param {/switch} will return switch details
 * @param {/switch/links} will return links details
 * @return {/switch/flows} will return flow details
 */

var cookie = new function() {
    this.set = function ( name, value, days ) {
        var expires = "";
        days = typeof days !== 'undefined' ? days : 10;
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

/** ajax call to switch api to get switch details */

var responseData = [];

var ISL = {
	DISCOVERED: "#00baff",
    FAILED :  "#d93923",
    UNIDIR : "#333"
};

var RIGHT_CHECKBOXES = {
	SWITCH_CHECKED: 0,
	ISL_CHECKED: 1,
	FLOW_CHECKED: 0
}	

var cookieCheckboxes = cookie.get( 'RIGHT_CHECKBOXES');
if(cookieCheckboxes == null){
	cookie.set( 'RIGHT_CHECKBOXES', JSON.stringify(RIGHT_CHECKBOXES), 365 );
}

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
						flows.push(response[i]);
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
var optArray = [];

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
/*force = d3.layout.force()
    .charge(-2090)
    .linkDistance(200)
	.size([width, height])
	.on("tick", tick);


drag = force.drag()
	.on("dragstart", dragstart)
	.on("dragend", dragend);
*/
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
		
		
		force = self.force = d3.layout.force()
        .nodes(nodes)
        .links(links)
        .gravity(.05)
        .charge(-2090)
       	.linkDistance(200)
       	.size([width, height])
        .start();
		
		
		drag = d3.behavior.drag()
	        .on("dragstart", dragstart)
	        .on("drag", dragmove)
	        .on("dragend", dragend);
		 
		resize();
		//window.focus();
		d3.select(window).on("resize", resize);
		link = link.data(links)
		.enter().append("path")
		.attr("class", function(d, index) {
	        if (d.hasOwnProperty("flow_count")) {
	            return "link logical";
	        } else {
	            if (d.unidirectional || d.state && d.state.toLowerCase()== "failed"){
                    return "link physical down";
                }else{
                    return "link physical";
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
	        	element.setAttribute("class", "link logical overlay");
	        } else {
	            if (d.unidirectional || d.state && d.state.toLowerCase()== "failed"){
                    element.setAttribute("class","link physical pathoverlay");
                }else{
                	element.setAttribute("class","link physical overlay");
                }
	            
	            var rec = element.getBoundingClientRect();
			    $('#topology-hover-txt, #isl_hover').css('display', 'block');
			    $('#topology-hover-txt').css('top', rec.y + 'px');
			    $('#topology-hover-txt').css('left', rec.x + 'px');
			    
			    d3.select(".isldetails_div_source_port").html("<span>" + d.src_port + "</span>");
			    d3.select(".isldetails_div_destination_port").html("<span>" + d.dst_port + "</span>");
			    d3.select(".isldetails_div_source_switch_name").html("<span>" + d.source_switch_name + "</span>");
			    d3.select(".isldetails_div_destination_switch_name").html("<span>" + d.target_switch_name + "</span>");
			    d3.select(".switchdetails_div_speed").html("<span>" + d.speed/1000 + " Mbps</span>");
			    d3.select(".switchdetails_div_state").html("<span>" + d.state + "</span>");
			    d3.select(".switchdetails_div_bandwidth").html("<span>" + d.available_bandwidth/1000 + " Mbps</span>");
			    var bound = HorizontallyBound(document.getElementById("switchesgraph"), document.getElementById("topology-hover-txt"));
			    if(bound){
			    	$("#topology-hover-txt").removeClass("left");
			    }else{
			    	var left = rec.x - (300 + 100); // subtract width of tooltip box + circle radius
			    	$('#topology-hover-txt').css('left', left + 'px');
			    	$("#topology-hover-txt").addClass("left");
			    }
	        }
	    }).on("mouseout", function(d, index) {
	        var element = $("#link" + index)[0];
	        if (d.hasOwnProperty("flow_count")) {
	        	element.setAttribute("class", "link logical");
	        } else {
	            if (d.unidirectional || d.state && d.state.toLowerCase()== "failed"){
	                element.setAttribute("class","link physical down");
	            }else{
	                element.setAttribute("class","link physical");
	            }
	        }
	        
	        if (!$('#topology-hover-txt').is(':hover')) {
	    		$('#topology-hover-txt, #isl_hover').css('display', 'none');
	    	}
	    }).on("click", function(d, index) {
            var element = $("#link" + index)[0];
            if (d.hasOwnProperty("flow_count")) {
	        	element.setAttribute("class", "link logical overlay");
	        	showFlowDetails(d);
	        } else {
	            
	            if (d.unidirectional || d.state && d.state.toLowerCase()== "failed"){
                    element.setAttribute("class","link physical pathoverlay");
                }else{
                    element.setAttribute("class","link physical overlay");
                }
	        	showLinkDetails(d);
	        }
        }).attr("stroke", function(d, index) {
	        if (d.hasOwnProperty("flow_count")) {
	            return "#228B22";
	        } else {
	            if (d.unidirectional){
                    return ISL.UNIDIR;
                } else if(d.state && d.state.toLowerCase()== "discovered"){
                    return ISL.DISCOVERED;
                }
	            
	        	return ISL.FAILED;
	        }
	        
        });
		node = node.data(nodes)
			.enter().append("g")
			.attr("class", "node")
			.on("dblclick", dblclick)
			.call(drag);
		circle = node.append("circle")
			.attr("r", radius)
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
			
			
		text = node.append("text")
			.attr("dy", ".35em")
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
		flow_count = svg.selectAll(".flow_count")
	    	.data(links)
	    	.enter().append("g").attr("class","flow-circle");
	
		flow_count.append("circle")
	    .attr("dy", ".35em")
	    .style("font-size", nominal_text_size + "px")
	
	
	    .attr("r", function(d, index) {
	        var element = $("#link" + index)[0];
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
	    }).call(force.drag)
	
	
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
		force.on('end', function() {
			$("#wait").css("display", "none");
			$("#switchesgraph").removeClass("hide");
			if(zoomFitCall){
				zoomFit(0.95, 500);
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
				updateRightPanel(storageValue);
			}
		}
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
			return "M" + d.source.x + "," + d.source.y + "A" + dr +
				"," + dr + " 0 0 1," + d.target.x + "," +
				d.target.y + "A" + dr + "," + dr + " 0 0 0," +
				d.source.x + "," + d.source.y;
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
	    var pathEl = d3.select('#link' + index).node();
	    var midpoint = pathEl.getPointAtLength(pathEl.getTotalLength() / 2);
	    var ydata = midpoint.x / 2;
	    var xvalue = midpoint.y / 2;
	    var xvalue = (d.source.y + d.target.y) / 2;
	    var yvalue = (d.source.x + d.target.x) / 2;
	    if(d.source_switch == d.target_switch){
	    	return "translate(" + (yvalue+70) + "," + (xvalue-70) + ")";
	    }else{
	    	return "translate(" + yvalue + "," + xvalue + ")";
	    }
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
	force.resume();
	panzoom.reset();
}

function zoomClick(id) {
	
	if(id === 'zoom_in'){
		panzoom.zoomIn()
	}else{
		panzoom.zoomOut();
	}
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
    force.resume();
}
function dragstart(d) {
	force.stop()
	d3.event.sourceEvent.stopPropagation();
	//d3.select(this).classed("fixed", d.fixed = true);
}
function dragmove(d, i) {
    d.px += d3.event.dx;
    d.py += d3.event.dy;
    d.x += d3.event.dx;
    d.y += d3.event.dy; 
    tick(); // this is the key to make it work together with updating both px,py,x,y on d !
}

function dragend(d, i) {
	flagHover = false;
	d.fixed = true; // of course set the node to fixed so the force doesn't include the node in its auto positioning stuff
    tick();
 //   force.resume();
}

$("#switch").on("click", function(){
	checked = $('input[name="switch"]:checked').length; 
	updateRightPanel({SWITCH_CHECKED : checked});
});

function zoomFit(paddingPercent, transitionDuration) {
	var bounds = svg.node().getBBox();
	var parent = svg.node().parentElement;
	var fullWidth = $(parent).width(),
		fullHeight = $(parent).height();
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
var options = {
	      events: {
	        doubleClick: false
	      }
}
var panzoom = $("svg").svgPanZoom(options);
localStorage.clear();
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
        	/*unmatched.selectAll("circle")
    		.attr("class", function(d,index){
    			var element = document.getElementById("circle_" + d.switch_id);
    			var classes = "circle blue";
				if(d.state && d.state.toLowerCase() == "deactivated"){
					classes = "circle red";
				}
				element.setAttribute("class", classes);
			});*/
	       
	    }
    }
}

$('.isl_flow').click(function(e){
	var id = $(this).attr("id");
	var isLogicalChecked = $('#logical:checked').length; 
	var isPhysicalChecked = $('#physical:checked').length; 
	if(id == "logical"){
		updateRightPanel({FLOW_CHECKED : isLogicalChecked});
	}
	if(id == "physical"){
		updateRightPanel({ISL_CHECKED : isPhysicalChecked});
	}
	
});

$(document).ready(function() {
	$('body').on("click", function() { 
		$('#topology-hover-txt').css('display', 'none');
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
	/*var storageValue = cookie.get('RIGHT_CHECKBOXES');
	if(storageValue != null){
		try{
			storageValue = JSON.parse(storageValue);
		}catch(e){}
		
	}*/
	var duration = 500;
	if(obj.hasOwnProperty("SWITCH_CHECKED")){
		RIGHT_CHECKBOXES.SWITCH_CHECKED = obj.SWITCH_CHECKED;
		$("#switch").attr("checked", !!obj.SWITCH_CHECKED);
		var element = $(".switchname");
		if(obj.SWITCH_CHECKED){
			element.addClass("show").removeClass("hide");
		}else{
			element.removeClass("show").addClass("hide");
		}
	}
	if(obj.hasOwnProperty("ISL_CHECKED")){
		RIGHT_CHECKBOXES.ISL_CHECKED = obj.ISL_CHECKED;
		$("#physical").attr("checked", !!obj.ISL_CHECKED);
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
		$("#logical").attr("checked", !!obj.FLOW_CHECKED);
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
	cookie.set('RIGHT_CHECKBOXES', JSON.stringify(RIGHT_CHECKBOXES));
}



/* ]]> */