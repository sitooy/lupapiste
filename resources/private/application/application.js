/**
 * application.js:
 */

;(function() {

	var application = {
		id: ko.observable(),
		state: ko.observable(),
		roles: ko.observable(),
	    permitType: ko.observable(),
		title: ko.observable(),
		created: ko.observable(),
		documents: ko.observableArray(),
		attachments: ko.observableArray(),
		comments: ko.observableArray(),
		streetAddress: ko.observableArray(),
		postalCode: ko.observableArray(),
		postalPlace: ko.observableArray(),
		verdict: ko.observable(),
		submitApplication: submitApplication,
		approveApplication: approveApplication
	};
	
	var emptyRh1 = {
		rakennuspaikanTiedot: {
			building_location: "",
			borough: "",
			stead_number: "",
			stead_name: "",
			lot: "",
			building_address: "",
			building_address_second: "",
			postal_code: "",
			post_office: "",
			tenure: "",
			owner: "",
			plan_readiness: "",
			exemption: ""			
		},
		rakennuksenTiedot: {
			ownership: "",
			ownership_other: "",
			builder: "",
			operation: "",
			main_use: "",
			building_volume: "",
			building_gross_floor_area: "",
			building_total_area: "",
			floor_count: 0,
			cellar_floor_area: "",
			supporting_structure_material: "",
			supporting_structure_material_other: "",
			build_style: "",
			facade_material: "",
			facade_material_other: "",
			network_interfaces: {
				sewer: false,
				water: false,
				electricity: false,
				gas: false,
				cabel: false
			},
			heating: "",
			heating_source: "",
			heating_source_other: "",
			building_equipments: {
                electricity: false,
                gas: false,
                sewer: false,
                water: false,
                warm_water: false,
                solar_panel: false,
                elevator: false,
                air_conditioning: false,
                saunas: false,
                swimming_pools: false,
                shelter: false
			},
			aaa: {
				a1: "",
				a2: "",
				a3: "",
				a4: "",
				a5: "",
				a6: "",
				a7: "",
				a8: "",
				a9: "",
				a10: ""
			}
		},
		huoneistonTiedot: {
			apartments: "",
			apartment_id: "",
			room_count: 0,
			kitchen_type: "",
			apartment_floor_area: "",
			apartment_equipments: {
				toilet: false,
				shower: false,
				sauna: false,
				balcony: false,
				warm_water: false
			},
			new_apartments: "",
			apartments_total_floor_area: ""
		},
		save: function(m) {
			var u = ko.mapping.toJS(m);
			delete u.save;
			ajax
				.command("rh1-demo", {id: application.id(), data: u})
				.success(function() { debug("RH1 save completed"); })
				.error(function() { error("RH1 save failed"); })
				.call();
			return false;
		}
	};
	
	var rh1 = ko.mapping.fromJS(emptyRh1);
	
	var authorization = {
		data: ko.observable({})
	};

	function makeSubscribable(initialValue, listener) {
		var v = ko.observable(initialValue);
		v.subscribe(listener);
		return v;
	}
	
	var applicationMap;
	var markers = new OpenLayers.Layer.Markers( "Markers" );

	var marker;
	var icon = (function() {
		var size = new OpenLayers.Size(21,25);
		var offset = new OpenLayers.Pixel(-(size.w/2), -size.h);
		return new OpenLayers.Icon('/img/marker-green.png', size, offset);
	})();
	

	function showApplication(data) {
		ajax.postJson("/rest/actions/valid",{id: data.id})
			.success(function(d) {
				authorization.data(d.commands);
				showApplicationPart2(data);
				hub.setPageReady("application");
			})
			.call();
	}

	function showApplicationPart2(data) {

		application.id(data.id);
		application.state(data.state);
		application.roles(data.roles);
		application.title(data.title);
		application.created(data.created);
		application.permitType(data.permitType);
		application.streetAddress(data.streetAddress);
		application.postalCode(data.postalCode);
		application.postalPlace(data.postalPlace);
		application.verdict(data.verdict);
		
		ko.mapping.fromJS(data.rh1 || emptyRh1, rh1);
		
		application.documents.removeAll();
		var documents = data.documents;
		if (documents) {
			for (var d in documents) {
				application.documents.push(documents[d]);
			}
		}

		application.attachments.removeAll();
		var attachments = data.attachments;
		if (attachments) {
			for (var attachmentId in attachments) {
				var attachment = attachments[attachmentId];
				attachment.open = "window.location.hash = '!/attachment/" + data.id + "/" + attachment.id + "';";
				application.attachments.push(attachment);
			}
		}

		application.comments.removeAll();
		var comments = data.comments;
		if (comments) {
			for (var i = 0; i < comments.length; i++) {
				application.comments.push(comments[i]);
			}
		}

		var position = map.toLatLon(data.location.lat, data.location.lon);
		
		if (marker) {
			markers.removeMarker(marker);
			marker.destroy();
			marker = null;
		}
		
		marker = map.makeMarker(position, icon);
		markers.addMarker(marker);
		if (applicationMap) applicationMap.setCenter(position, 12);

	}
	
	function uploadCompleted(file, size, type, attachmentId) {
		// if (attachments) attachments.push(new Attachment(file, type, size, attachmentId));
	}
		
	hub.subscribe({type: "page-change", pageId: "application"}, function(e) {
		var id = e.pagePath[0];
		if (application.id() != id) {
			repository.getApplication(id, showApplication, function() {
				// TODO: Show "No such application, or not permission"
				error("No such application, or not permission");
			});
		}
	});

	hub.subscribe("repository-application-reload", function(e) {
		if (application.id() === e.applicationId) {
			repository.getApplication(e.applicationId, showApplication, function() {
				// TODO: Show "No such application, or not permission"
				error("No such application, or not permission");
			});
		}
	});
			
	function submitComment(model) {
		var applicationId = application.id();
		console.log(model);
		ajax.command("add-comment", { id: applicationId, text: model.text()})
			.success(function(d) { 
				repository.reloadAllApplications();
				model.comment.text(undefined);
				// model.comment.text.isModified(false); FIXME TypeError: model.comment.text.isModified is not a function 
			})
			.call();
		return false;
	}

	function submitApplication(model) {
		var applicationId = application.id();
		console.log("applicationid:" + applicationId);
		ajax.command("submit-application", { id: applicationId})
		.success(function(d) {
			notify.success("hakemus j\u00E4tetty",model);
			repository.reloadAllApplications();
		})
		.call();
		return false;
	}

	function approveApplication(model) {
		var applicationId = application.id();
		ajax.command("approve-application", { id: applicationId})
			.success(function(d) {
				notify.success("hakemus hyv\u00E4ksytty",model);
				repository.reloadAllApplications();
			})
			.call();
		return false;
	}

	var comment = {
		text: ko.observable(),
		submit: submitComment
	};
	
	comment.disabled = ko.computed( function() { return comment.text() == "" || comment.text() == null; });
		
    var tab = {
        tabClick: function(data, event) {
           var self = event.target;
           console.log(self);
           $("#tabs li").removeClass('active');
           $(self).parent().addClass("active");
           console.log($(".tab_content"));
           $(".tab_content").hide();
           var selected_tab = $(self).attr("href");
           $(selected_tab).fadeIn();
        }
    };

    var accordian = {
        accordianClick: function(data, event) {
           self = event.target;
           $(self).next(".application_section_content").toggleClass('content_expanded');
        }
    };
    	
	$(function() {
		var page = $("#application");

		applicationMap = new OpenLayers.Map("application-map");
		applicationMap.addLayer(new OpenLayers.Layer.OSM());
		applicationMap.addLayer(markers);
		
		ko.applyBindings({application: application,
						  comment: comment,
						  authorization: authorization,
						  rh1: rh1,
						  tab: tab,
						  accordian: accordian}, page[0]);
		initUpload($(".dropbox", page), function() { return application.id(); }, uploadCompleted);
	});

})();
