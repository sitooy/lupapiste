var DocModel = function(schema, doc, application, authorizationModel, options) {
  "use strict";

  var self = this;

  // Magic key: if schema contains "_selected" radioGroup,
  // user can select only one of the schemas named in "_selected" group
  var SELECT_ONE_OF_GROUP_KEY = "_selected";

  self.schema = schema;
  self.schemaName = schema.info.name;
  self.schemaI18name = schema.info.i18name;
  if (!self.schemaI18name) {
      self.schemaI18name = self.schemaName;
  }
  self.model = doc.data;
  self.meta = doc.meta;
  self.docId = doc.id;
  self.appId = application.id;
  self.application = application;
  self.authorizationModel = authorizationModel;
  self.eventData = { doc: doc.id, app: self.appId };
  self.propertyId = application.propertyId;
  self.isDisabled = options && options.disabled;
  self.events = [];

  self.getMeta = function (path, m) {
    var meta = m ? m : self.meta;
    if (!path || !path.length) {
      return meta;
    }
    if (meta) {
      var key = path[0];
      var val = meta[key];
      if (path.length === 1) {
        return val;
      }
      return self.getMeta(path.splice(1, path.length - 1), val);
    }
  };

  self.sizeClasses = { "t": "form-input tiny", "s": "form-input short", "m": "form-input medium", "l": "form-input long"};

  // Context help
  self.addFocus = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    input$.focus();
  };

  self.findHelpElement = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var help$ = input$.siblings(".form-help");
    if (!help$.length) {
      help$ = input$.parent().siblings(".form-help");
    }
    if (!help$.length) {
      return false;
    }
    return help$;
  };
  self.findErrorElement = function (e) {
    var event = getEvent(e);
    var input$ = $(event.target);
    var error$ = input$.siblings(".errorPanel");
    if (!error$.length) {
      error$ = input$.parent().siblings(".errorPanel");
    }
    if (!error$.length) {
      return false;
    }
    return error$;
  };

  self.showHelp = function (e) {
    var element = self.findHelpElement(e);
    if (element) {
      element.stop();
      element.fadeIn("slow").css("display", "block");
      var st = $(window).scrollTop(); // Scroll Top
      var y = element.offset().top;
      if ((y - 80) < (st)) {
        $("html, body").animate({ scrollTop: y - 80 + "px" });
      }
    }
    self.showError(e);
  };
  self.hideHelp = function (e) {
    var element = self.findHelpElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
    self.hideError(e);
  };

  self.showError = function (e) {
    var element = self.findErrorElement(e);
    if (element && element.children && element.children().size()) {
      element.stop();
      element.fadeIn("slow").css("display", "block");
    }
  };

  self.hideError = function (e) {
    var element = self.findErrorElement(e);
    if (element) {
      element.stop();
      element.fadeOut("slow").css("display", "none");
    }
  };

  // trigger stored events once
  self.triggerEvents = function() {
    _.forEach(self.events, function(event) {
      hub.send(event.name, event.data);
    });
    self.events = [];
  };

  // ID utilities

  function pathStrToID(pathStr) {
    return self.docId + "-" + pathStr.replace(/\./g, "-");
  }

  function pathStrToLabelID(pathStr) {
    return "label-" + pathStrToID(pathStr);
  }

  function pathStrToGroupID(pathStr) {
    return "group-" + pathStrToID(pathStr);
  }

  // Option utils

  self.getCollection = function() {
    return (options && options.collection) ? options.collection : "documents";
  };

  function getUpdateCommand() {
    return (options && options.updateCommand) ? options.updateCommand : "update-doc";
  }

  // Element constructors
  function makeButton(id, label) {
    var button = document.createElement("button");
    button.id = id;
    button.className = "btn";
    button.innerHTML = label;
    return button;
  }

  function makeLabel(schema, type, pathStr, groupLabel) {
    var label = document.createElement("label");
    var path = groupLabel ? pathStr + "._group_label" : pathStr;

    var locKey = util.locKeyFromDocPath(self.schemaI18name + "." + path);
    if (schema.i18nkey) {
      locKey = schema.i18nkey;
    }

    var className = "form-label form-label-" + type;
    if (schema.labelclass) {
      className = className + " " + schema.labelclass;
    }

    label.id = pathStrToLabelID(pathStr);
    label.htmlFor = pathStrToID(pathStr);
    label.className = className;
    label.innerHTML = loc(locKey);
    return label;
  }

  function makeInput(type, pathStr, value, extraClass, readonly, subSchema) {
    var input = document.createElement("input");
    input.id = pathStrToID(pathStr);
    input.name = self.docId + "." + pathStr;
    input.setAttribute("data-docgen-path", pathStr);

    try {
      input.type = type;
    } catch (e) {
      // IE does not support HTML5 input types such as email
      input.type = "text";
    }

    input.className = "form-input " + type + " " + (extraClass || "");

    if (readonly) {
      input.readOnly = true;
    } else {
      input.onchange = function(e) {
        save(e);
        if (subSchema) {
          emit(getEvent(e).target.value, subSchema);
        }
      };
    }

    if (type === "checkbox") {
      input.checked = value;
    } else {
      input.value = value || "";
    }
    return input;
  }

  function makeEntrySpan(subSchema, pathStr) {
    var help = null;
    var helpLocKey = util.locKeyFromDocPath(self.schemaI18name + "." + pathStr + ".help");
    if (subSchema.i18nkey) {
      helpLocKey = subSchema.i18nkey + ".help";
    }
    var span = document.createElement("span");
    var sizeClass = self.sizeClasses[subSchema.size] || "";
    span.className = "form-entry " + sizeClass;

    if (subSchema.codes) {
      span.setAttribute("data-codes", subSchema.codes.join(" "));
    }

    // Display text areas in a wide container
    if (subSchema.type === "text") {
      span.className = "form-entry form-full-width";
    }

    // Override style with layout option
    if (subSchema.layout) {
      span.className = "form-entry form-" + subSchema.layout + " " + sizeClass;
    }

    // durable field error panels
    var errorPanel = document.createElement("span");
    errorPanel.className = "errorPanel";
    errorPanel.id = pathStrToID(pathStr) + "-errorPanel";
    span.appendChild(errorPanel);

    // Add span for help text
    if (loc.hasTerm(helpLocKey)) {
      help = document.createElement("span");
      help.className = "form-help";
      help.innerHTML = loc(helpLocKey);
      span.appendChild(help);
    }

    return span;
  }

  self.makeApprovalButtons = function (path, model) {
    var btnContainer$ = $("<span>").addClass("form-buttons");
    var statusContainer$ = $("<span>");
    var approvalContainer$ = $("<span>").addClass("form-approval-status empty").append(statusContainer$).append(btnContainer$);
    var approveButton$ = null;
    var rejectButton$ = null;
    var cmdArgs = { id: self.appId, doc: self.docId, path: path.join("."), collection: self.getCollection() };

    if (_.isEmpty(model)) {
      return approvalContainer$[0];
    }

    function setStatus(approval) {
      if (approval) {
        var text = loc(["document", approval.value]);
        if (approval.user && approval.timestamp) {
          text += " (" + approval.user.lastName + " " + approval.user.firstName;
          text += " " + moment(approval.timestamp).format("D.M.YYYY HH:mm") + ")";
        }
        statusContainer$.text(text);
        statusContainer$.addClass("approval-" + approval.value);
        approvalContainer$.removeClass("empty");
      }
    }

    function makeApprovalButton(verb, noun, cssClass) {
      var cmd = verb + "-doc";
      var title = loc(["document", verb]);
      var button =
        $(makeButton(self.docId + "_" + verb, title))
          .addClass(cssClass).addClass("btn-auto")
          .click(function () {
            ajax.command(cmd, cmdArgs)
              .success(function () {
                if (noun === "approved") {
                  approveButton$.hide();
                  rejectButton$.show();
                } else {
                  approveButton$.show();
                  rejectButton$.hide();
                }
                setStatus({ value: noun });
              })
              .call();
          });
      if (options && options.dataTestSpecifiers) {
        button.attr("data-test-id", verb + "-doc-" + self.schemaName);
      }
      return button;
    }

    function modelModifiedSince(model, timestamp) {
      if (model) {
        if (!timestamp) {
          return true;
        }
        if (_.has(model, "value")) {
          // Leaf
          return model.modified && model.modified > timestamp;
        }
        return _.find(model, function (myModel) { return modelModifiedSince(myModel, timestamp); });
      }
      return false;
    }

    var meta = self.getMeta(path);
    var approval = meta ? meta._approved : null;
    var requiresApproval = !approval || modelModifiedSince(model, approval.timestamp);
    var allowApprove = requiresApproval || (approval && approval.value === "rejected");
    var allowReject = requiresApproval || (approval && approval.value === "approved");

    if (self.authorizationModel.ok("approve-doc")) {
      approveButton$ = makeApprovalButton("approve", "approved", "btn-primary");
      btnContainer$.append(approveButton$);

      if (!allowApprove) {
          approveButton$.hide();
      }
    }
    if (self.authorizationModel.ok("reject-doc")) {
      rejectButton$ = makeApprovalButton("reject", "rejected", "btn-secondary");
      btnContainer$.append(rejectButton$);

      if (!allowReject) {
          rejectButton$.hide();
      }
    }

    if (allowApprove || allowReject) {
        approvalContainer$.removeClass("empty");
    }

    if (!requiresApproval) {
      setStatus(approval);
    }
    return approvalContainer$[0];
  };

  // Form field builders

  function buildCheckbox(subSchema, model, path) {
    var myPath = path.join(".");
    var span = makeEntrySpan(subSchema, myPath);
    var input = makeInput("checkbox", myPath, getModelValue(model, subSchema.name), subSchema.readonly);
    input.onmouseover = self.showHelp;
    input.onmouseout = self.hideHelp;
    span.appendChild(input);

    if (subSchema.label) {
      var label = makeLabel(subSchema, "checkbox", myPath);
      label.onmouseover = self.showHelp;
      label.onmouseout = self.hideHelp;
      span.appendChild(label);
    }

    return span;
  }

  function setMaxLen(input, subSchema) {
    var maxLen = subSchema["max-len"] || 255; // if you change the default, change in model.clj, too
    input.setAttribute("maxlength", maxLen);
  }

  function buildString(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var span = makeEntrySpan(subSchema, myPath);

    var supportedInputSubtypes = ["email", "time"];
    var inputType = (_.indexOf(supportedInputSubtypes, subSchema.subtype) > -1) ? subSchema.subtype : "text";

    var sizeClass = self.sizeClasses[subSchema.size] || "";
    var input = makeInput(inputType, myPath, getModelValue(model, subSchema.name), sizeClass, subSchema.readonly, subSchema);
    setMaxLen(input, subSchema);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, partOfChoice ? "string-choice" : "string", myPath));
    }

    if (subSchema.subtype === "maaraala-tunnus" ) {
      var kiitunAndInput = document.createElement("span");
      var kiintun = document.createElement("span");

      kiitunAndInput.className = "kiintun-and-maaraalatunnus";

      kiintun.className = "form-maaraala";
      kiintun.appendChild(document.createTextNode(util.prop.toHumanFormat(self.propertyId) + "-M"));

      input.onfocus = self.showHelp;
      input.onblur = self.hideHelp;
      input.onmouseover = self.showHelp;
      input.onmouseout = self.hideHelp;

      kiitunAndInput.appendChild(kiintun);
      kiitunAndInput.appendChild(input);
      span.appendChild(kiitunAndInput);

    }
    else if (subSchema.unit) {
      var inputAndUnit = document.createElement("span");
      var unit = document.createElement("span");

      inputAndUnit.className = "form-input-and-unit";
      inputAndUnit.appendChild(input);

      unit.className = "form-string-unit";
      unit.appendChild(document.createTextNode(loc(["unit", subSchema.unit])));

      input.onfocus = self.showHelp;
      input.onblur = self.hideHelp;
      input.onmouseover = self.showHelp;
      input.onmouseout = self.hideHelp;

      inputAndUnit.appendChild(unit);
      span.appendChild(inputAndUnit);

    } else {
      input.onfocus = self.showHelp;
      input.onblur = self.hideHelp;
      input.onmouseover = self.showHelp;
      input.onmouseout = self.hideHelp;
      span.appendChild(input);
    }

    return span;
  }

  function getModelValue(model, name) {
    return model[name] ? model[name].value : "";
  }

  function buildText(subSchema, model, path) {
    var myPath = path.join(".");
    var input = document.createElement("textarea");
    var span = makeEntrySpan(subSchema, myPath);

    input.id = pathStrToID(myPath);

    input.onfocus = self.showHelp;
    input.onblur = self.hideHelp;
    input.onmouseover = self.showHelp;
    input.onmouseout = self.hideHelp;

    input.name = myPath;
    input.setAttribute("rows", subSchema.rows || "10");
    input.setAttribute("cols", subSchema.cols || "40");
    setMaxLen(input, subSchema);

    if (subSchema.readonly) {
      input.readOnly = true;
    } else {
      input.onchange = save;
    }

    input.className = "form-input textarea";
    input.value = getModelValue(model, subSchema.name);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "text", myPath));
    }
    span.appendChild(input);
    return span;
  }

  function buildDate(subSchema, model, path) {
    var lang = loc.getCurrentLanguage();
    var myPath = path.join(".");
    var value = getModelValue(model, subSchema.name);

    var span = makeEntrySpan(subSchema, myPath);

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "date", myPath));
    }

    // date
    var input = $("<input>", {
      id: pathStrToID(myPath),
      name: self.docId + "." + myPath,
      type: "text",
      "class": "form-input text form-date",
      value: value
    });

    if (subSchema.readonly) {
      input.attr("readonly", true);
    } else {
      input.datepicker($.datepicker.regional[lang]).change(save);
    }
    input.appendTo(span);

    return span;
  }

  function buildTime(subSchema, model, path, partOfChoice) {
    // Set implisit options into a clone
    var timeSchema = _.clone(subSchema);
    timeSchema.subtype = "time";
    timeSchema.size = "m";
    timeSchema["max-len"] = 10; // hh:mm:ss.f
    return buildString(timeSchema, model, path, partOfChoice);
  }

  function buildSelect(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    var selectedOption = getModelValue(model, subSchema.name);
    var span = makeEntrySpan(subSchema, myPath);
    var sizeClass = self.sizeClasses[subSchema.size] || "";

    select.onfocus = self.showHelp;
    select.onblur = self.hideHelp;
    select.onmouseover = self.showHelp;
    select.onmouseout = self.hideHelp;
    select.setAttribute("data-docgen-path", myPath);

    select.name = myPath;
    select.className = "form-input combobox " + (sizeClass || "");

    select.id = pathStrToID(myPath);

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function(e) {
        save(e);
        emit(getEvent(e).target.value, subSchema);
      };
    }

    emitLater(selectedOption, subSchema);

    var otherKey = subSchema["other-key"];
    if (otherKey) {
      var pathToOther = path.slice(0, -1);
      pathToOther.push(otherKey);
      select.setAttribute("data-select-other-id", pathStrToID(pathToOther.join(".")));
    }

    var option = document.createElement("option");
    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    _(subSchema.body)
      .map(function(e) {
        var locKey = self.schemaI18name + "." + myPath.replace(/\.\d+\./g, ".") + "." + e.name;
        if (e.i18nkey) {
          locKey = e.i18nkey;
        } else if (subSchema.i18nkey) {
          locKey = subSchema.i18nkey + "." + e.name;

        }
        return [e.name, loc(locKey)];
      })
      .sortBy(function(e) {
          if (subSchema.sortBy === "displayname") {
            return e[1];
          }
          // lo-dash API doc tells that the sort is stable, so returning a static value equals to no sorting
          return 0;
      })
      .forEach(function(e) {
        var name = e[0];
        var option = document.createElement("option");
        option.value = name;
        option.appendChild(document.createTextNode(e[1]));
        if (selectedOption === name) {
          option.selected = "selected";
        }
        select.appendChild(option);
    });

    if (otherKey) {
      option = document.createElement("option");
      option.value = "other";
      option.appendChild(document.createTextNode(loc("select-other")));
      if (selectedOption === "other") {
        option.selected = "selected";
      }
      select.appendChild(option);
    }

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "select", myPath, true));
    }
    span.appendChild(select);
    return span;
  }

  function buildGroup(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var partsDiv = document.createElement("div");
    var div = document.createElement("div");

    appendElements(partsDiv, subSchema, myModel, path, save, partOfChoice);

    div.id = pathStrToGroupID(myPath);
    div.className = subSchema.layout === "vertical" ? "form-choice" : "form-group";

    var label = makeLabel(subSchema, "group", myPath, true);
    div.appendChild(label);

    if (subSchema.approvable) {
      label.appendChild(self.makeApprovalButtons(path, myModel));
    }
    div.appendChild(partsDiv);

    var listen = subSchema.listen;
    _.forEach(listen, function(listenEvent) {
      if (listenEvent === "filterByCode") {
        $(div).find("[data-codes]").addClass("hidden");
        hub.subscribe(listenEvent, function(event) {
          $(div).find("[data-codes]").addClass("hidden");
          $(div).find("[data-codes*='" + event.code + "']").removeClass("hidden");
        });
      }
    });
    return div;
  }

  function buildRadioGroup(subSchema, model, path) {
    var myPath = path.join(".");
    var myModel;
    if (model[subSchema.name] && model[subSchema.name].value) {
      myModel = model[subSchema.name].value;
    } else {
      myModel = _.first(subSchema.body).name;
    }

    var partsDiv = document.createElement("div");

    var span = makeEntrySpan(subSchema, myPath);
    span.className = span.className + " radioGroup";
    partsDiv.id = pathStrToID(myPath);

    $.each(subSchema.body, function (i, o) {
      var pathForId = myPath + "." + o.name;
      var input = makeInput("radio", myPath, o.name, subSchema.readonly);
      input.id = pathStrToID(pathForId);
      input.checked = o.name === myModel;

      span.appendChild(input);
      if (subSchema.label) {
        span.appendChild(makeLabel(subSchema, "radio", pathForId));
      }
    });

    partsDiv.appendChild(span);
    return partsDiv;
  }

  function buildBuildingSelector(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    var selectedOption = getModelValue(model, subSchema.name);
    var option = document.createElement("option");
    var span = makeEntrySpan(subSchema, myPath);
    span.className = "form-entry really-long";

    select.id = pathStrToID(myPath);

    select.name = myPath;
    select.className = "form-input combobox long";

    var otherKey = subSchema["other-key"];
    if (otherKey) {
      var pathToOther = path.slice(0, -1);
      pathToOther.push(otherKey);
      select.setAttribute("data-select-other-id", pathStrToID(pathToOther.join(".")));
    }

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function (e) {
        var event = getEvent(e);
        var target = event.target;

        var buildingId = target.value;

        function mergeFromWfs(overwriteWithBackendData) {
          ajax
          .command("merge-details-from-krysp",
              {id: self.appId, documentId: self.docId,
            path: myPath,
            buildingId: buildingId,
            overwrite: overwriteWithBackendData,
            collection: self.getCollection() })
            .success(function () {
              repository.load(self.appId);
            })
            .call();
        }

        if (buildingId !== "" && buildingId !== "other") {
          LUPAPISTE.ModalDialog.showDynamicYesNo(
              loc("overwrite.confirm"),
              loc("application.building.merge"),
              {title: loc("yes"), fn: _.partial(mergeFromWfs, true)},
              {title: loc("no"), fn: _.partial(mergeFromWfs, false)}
          );
        } else {
          mergeFromWfs(false);
        }
        return false;
      };
    }

    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    var otherOption = null;
    if (otherKey) {
      otherOption = document.createElement("option");
      otherOption.value = "other";
      otherOption.appendChild(document.createTextNode(loc("select-other")));
      if (selectedOption === "other") {
        otherOption.selected = "selected";
      }
      select.appendChild(otherOption);
    }

    ajax
      .command("get-building-info-from-wfs", { id: self.appId })
      .success(function (data) {
        $.each(data.data, function (i, building) {
          var name = building.buildingId;
          var usage = building.usage;
          var created = building.created;
          var option = document.createElement("option");
          option.value = name;
          option.appendChild(document.createTextNode(name + " (" + usage + ") - " + created));
          if (selectedOption === name) {
            option.selected = "selected";
          }
          if (otherOption) {
            select.insertBefore(option, otherOption);
          } else {
            select.appendChild(option);
          }
        });
      })
      .error(function (e) {
        var error = e.text ? e.text : "error.unknown";
        var option = document.createElement("option");
        option.value = name;
        option.appendChild(document.createTextNode(loc(error)));
        option.selected = "selected";
        select.appendChild(option);
        select.setAttribute("disabled", true);
      })
      .call();

    if (subSchema.label) {
      span.appendChild(makeLabel(subSchema, "select", myPath));
    }
    span.appendChild(select);
    return span;
  }

  function buildNewBuildingSelector(subSchema, model, path) {
    var myPath = path.join(".");
    var select = document.createElement("select");
    var selectedOption = getModelValue(model, subSchema.name);
    var option = document.createElement("option");
    var span = makeEntrySpan(subSchema, myPath);
    span.className = "form-entry really-long";

    select.id = pathStrToID(myPath);

    select.name = myPath;
    select.className = "form-input combobox long";

    if (subSchema.readonly) {
      select.readOnly = true;
    } else {
      select.onchange = function() {
        var target = select;
        var indicator = createIndicator(target);
        var path = target.name;
        var label = document.getElementById(pathStrToLabelID(path));
        var loader = loaderImg();
        var basePathEnd = (path.lastIndexOf(".") > 0) ? path.lastIndexOf(".") : path.length;
        var basePath = path.substring(0, basePathEnd);

        var option$ = $(target[target.selectedIndex]);
        var index = option$.val();
        var propertyId = option$.attr("data-propertyid") || "";
        var buildingId = option$.attr("data-buildingid") || "";
        var nationalId = option$.attr("data-nationalid") || (buildingId.length === 10 ? buildingId : "");
        var localShortId = option$.attr("data-localshortid") || (buildingId.length === 3 ? buildingId : "");
        // TODO local id coming in the next KRYSP version
        //var localId = option$.attr("data-localid") || "";

        var paths = [basePath + ".jarjestysnumero", basePath + ".kiinttun", basePath + ".rakennusnro", basePath + ".valtakunnallinenNumero"];
        var values = [index, propertyId, localShortId, nationalId];

        if (label) {
          label.appendChild(loader);
        }

        saveForReal(paths, values, _.partial(afterSave, label, loader, indicator, null));
        return false;
      };
    }

    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    $.each(self.application.buildings, function (i, building) {
          var name = building.index;
          var option = document.createElement("option");
          option.value = name;
          option.setAttribute("data-propertyid", building.propertyId || "");
          option.setAttribute("data-buildingid", building.buildingId || "");
          option.setAttribute("data-localshortid", building.localShortId || "");
          option.setAttribute("data-nationalid", building.nationalId || "");
          option.setAttribute("data-localid", building.localId || "");
          option.appendChild(document.createTextNode(util.buildingName(building)));
          if (selectedOption === name) {
            option.selected = "selected";
          }
          select.appendChild(option);
        });

    span.appendChild(makeLabel(subSchema, "select", myPath));
    span.appendChild(select);
    return span;
  }

  function buildForemanHistory(subSchema, model, path) {
    var params = {
      applicationId: self.appId
    };

    // TODO: move to function
    var paramsStr = _.map(_.keys(params), function(key) {
      return key + ": " + key;
    }).join(", ");

    return $("<foreman-history>")
      .attr("params", paramsStr)
      .addClass("form-table")
      .applyBindings(params)
      .get(0);
  }

  function buildFillMyInfoButton(subSchema, model, path) {
    var myNs = path.slice(0, path.length - 1).join(".");

    var params = {
      id: self.appId,
      documentId: self.docId,
      userId: currentUser.id(),
      path: myNs,
      collection: self.getCollection()
    };

    var paramsStr = _.map(_.keys(params), function(key) {
      return key + ": " + key;
    }).join(", ");

    return $("<fill-info-button params>")
      .attr("params", paramsStr)
      .applyBindings(params)
      .get(0);
  }

  function buildPersonSelector(subSchema, model, path) {
    var myPath = path.join(".");
    var span = makeEntrySpan(subSchema, myPath);
    span.className = span.className + " personSelector";
    var myNs = path.slice(0, path.length - 1).join(".");
    var select = document.createElement("select");
    var selectedOption = getModelValue(model, subSchema.name);
    var option = document.createElement("option");
    select.id = pathStrToID(myPath);
    select.name = myPath;
    select.className = "form-input combobox";
    select.onchange = function (e) {
      var event = getEvent(e);
      var target = event.target;
      var userId = target.value;
      if (!_.isEmpty(userId)) {
        ajax
        .command("set-user-to-document", { id: self.appId, documentId: self.docId, userId: userId, path: myNs, collection: self.getCollection() })
        .success(function () {
          save(event, function () { repository.load(self.appId); });
        })
        .call();
      }
      return false;
    };
    option.value = "";
    option.appendChild(document.createTextNode(loc("selectone")));
    if (selectedOption === "") {
      option.selected = "selected";
    }
    select.appendChild(option);

    _.each(self.application.auth, function (user) {
      // LUPA-89: don't print fully empty names
      if (user.firstName && user.lastName) {
        var option = document.createElement("option");
        var value = user.id;
        option.value = value;
        option.appendChild(document.createTextNode(user.lastName + " " + user.firstName));
        if (selectedOption === value) {
          option.selected = "selected";
        }
        if (user.invite) {
          option.disabled = true;
        }
        select.appendChild(option);
      }
    });

    var label = document.createElement("label");
    var locKey = ("person-selector");
    label.className = "form-label form-label-select";
    label.innerHTML = loc(locKey);
    span.appendChild(label);
    span.appendChild(select);

    // new invite
    var button =
      $("<button>", {
        "class": "icon-remove btn-primary",
        text: loc("personSelector.invite"),
        click: function () {
          $("#invite-document-name").val(self.schemaName).change();
          $("#invite-document-path").val(myNs).change();
          $("#invite-document-id").val(self.docId).change();
          LUPAPISTE.ModalDialog.open("#dialog-valtuutus");
          return false;
        }
      });
    if (options && options.dataTestSpecifiers) {
      button.attr("data-test-id", "application-invite-" + self.schemaName);
    }
    button.appendTo(span);

    return span;
  }

  function buildTableRow(subSchema, model, path, partOfChoice) {
    var myPath = path.join(".");
    var name = subSchema.name;
    var myModel = model[name] || {};
    var row = document.createElement("tr");
    appendElements(row, subSchema, myModel, path, save, partOfChoice);

    row.id = pathStrToGroupID(myPath);
    return row;
  }

  function buildUnknown(subSchema, model, path) {
    var div = document.createElement("div");

    error("Unknown element type:", subSchema.type, path);
    div.appendChild(document.createTextNode("Unknown element type: " + subSchema.type + " (path = " + path.join(".") + ")"));
    return div;
  }

  var builders = {
    group: buildGroup,
    string: buildString,
    hetu: buildString,
    text: buildText,
    checkbox: buildCheckbox,
    select: buildSelect,
    radioGroup: buildRadioGroup,
    date: buildDate,
    time: buildTime,
    element: buildElement,
    buildingSelector: buildBuildingSelector,
    newBuildingSelector: buildNewBuildingSelector,
    fillMyInfoButton: buildFillMyInfoButton,
    foremanHistory: buildForemanHistory,
    personSelector: buildPersonSelector,
    table: buildTableRow,
    unknown: buildUnknown
  };

  function removeData(id, doc, path) {
    ajax
      .command("remove-document-data", { doc: doc, id: id, path: path, collection: self.getCollection() })
      .success(function () {
        repository.load(id);
      })
      .call();
  }

  function build(subSchema, model, path, partOfChoice) {
    if (subSchema.hidden) {
      return;
    }

    if (subSchema.label === undefined) {
      subSchema.label = true;
    }

    var myName = subSchema.name;
    var myPath = path.concat([myName]);
    var builder = builders[subSchema.type] || buildUnknown;
    var repeatingId = myPath.join("-");

    function makeElem(myModel, id) {
      var elem = builder(subSchema, myModel, myPath.concat([id]), partOfChoice);
      if (elem) {
        elem.setAttribute("data-repeating-id", repeatingId);
        elem.setAttribute("data-repeating-id-" + repeatingId, id);

        if (subSchema.repeating && !self.isDisabled && authorizationModel.ok("remove-document-data")) {
          var removeButton = document.createElement("span");
          removeButton.className = "icon remove-grey inline-right";
          removeButton.onclick = function () {
            LUPAPISTE.ModalDialog.showDynamicYesNo(loc("document.delete.header"), loc("document.delete.message"),
                { title: loc("yes"), fn: function () { removeData(self.appId, self.docId, myPath.concat([id])); } },
                { title: loc("no") });
          };
          if (options && options.dataTestSpecifiers) {
            removeButton.setAttribute("data-test-class", "delete-schemas." + subSchema.name);
          }
          if (subSchema.type === "table") {
            var td = document.createElement("td");
            td.appendChild(removeButton);
            elem.appendChild(td, elem.childNodes[0]);
          } else {
            elem.insertBefore(removeButton, elem.childNodes[0]);
          }
        }
      }
      return elem;
    }

    function buildElements(models) {
      return _.map(models, function (val, key) {
        var myModel = {};
        myModel[myName] = val;
        return makeElem(myModel, key);
      });
    }

    function createTableHeader(models, pathStr) {
      var thead = document.createElement("thead");
      var tr = document.createElement("tr");
      _.each(subSchema.body, function(item) {
        var locKey = util.locKeyFromDocPath(self.schemaI18name + "." + pathStr + "." + item.name);
        if (item.i18nkey) {
          locKey = item.i18nkey;
        }
        var th = document.createElement("th");
        th.innerHTML = loc(locKey);
        tr.appendChild(th);
      });
      // remove button column
      tr.appendChild(document.createElement("th"));
      thead.appendChild(tr);
      return thead;
    }

    if (subSchema.repeating) {
      var models = model[myName];
      if (!models) {
          models = subSchema.initiallyEmpty ? [] : [{}];
      }

      var elements;

      if (subSchema.type === "table") {
        elements = buildElements(models);
        var div = document.createElement("div");
        div.className = "form-table";
        var table = document.createElement("table");
        table.id = "table-" + subSchema.name;
        var tbody = document.createElement("tbody");
        table.appendChild(createTableHeader(models, myPath.join(".")));
        _.each(elements, function(element) {
          tbody.appendChild(element);
        });
        table.appendChild(tbody);

        var label = makeLabel(subSchema, "table", myPath.join("."), true);
        div.appendChild(label);
        if (subSchema.approvable) {
          div.appendChild(self.makeApprovalButtons(path, models));
        }
        div.appendChild(table);

        elements = [div];
      } else {
        elements = buildElements(models);
      }

      var appendButton = makeButton(myPath.join("_") + "_append", loc([self.schemaI18name, myPath.join("."), "_append_label"]));

      var appender = function () {
        var parent$ = $(this).closest(".accordion-fields");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var myModel = {};
        myModel[myName] = {};
        $(this).before(makeElem(myModel, count));
      };

      var tableAppender = function () {
        var parent$ = $(this).closest(".accordion-fields").find("#" + "table-" + subSchema.name + " tbody");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var myModel = {};
        myModel[myName] = {};
        parent$.append(makeElem(myModel, count));
      };

      var copyElement = function() {
        var parent$ = $(this).closest(".accordion-fields").find("tbody");
        var count = parent$.find("*[data-repeating-id='" + repeatingId + "']").length;
        while (parent$.find("*[data-repeating-id-" + repeatingId + "='" + count + "']").length) {
          count++;
        }
        var lastItem$ = parent$.find("tr").last();

        var myModel = {};
        myModel[myName] = {};
        var newItem = makeElem(myModel, count);

        // copy last element items to new
        lastItem$.find("td").each(function(index) {
          var newInput$ = $($(newItem).find("input, select")[index]);
          var oldInput$ = $(this).find("input, select");
          var prop = "value";
          if(oldInput$.is(":checkbox")) {
            prop = "checked";
          }
          var oldValue = oldInput$.prop(prop);
          if(oldValue) {
            newInput$.prop(prop, oldInput$.prop(prop));
            newInput$.change();
          }
        });

        parent$.append(newItem);
      };

      var buttonGroup = document.createElement("div");
      buttonGroup.className = "button-group";
      buttonGroup.appendChild(appendButton);

      if (subSchema.type === "table") {
        $(appendButton).click(tableAppender);
        var locKey = [self.schemaI18name, myPath.join("."), "copyLabel"];
        if (subSchema.copybutton) {
          if (subSchema.i18nkey) {
            locKey = [subSchema.i18nkey, "copyLabel"];
          }
          var copyButton = makeButton(myPath.join("_") + "_copy", loc(locKey));
          $(copyButton).click(copyElement);
          buttonGroup.appendChild(copyButton);
        }
      } else {
        $(appendButton).click(appender);
      }

      elements.push(buttonGroup);
      return elements;
    }

    return builder(subSchema, model, myPath, partOfChoice);
  }

  function getSelectOneOfDefinition(schema) {
    var selectOneOfSchema = _.find(schema.body, function (subSchema) {
      return subSchema.name === SELECT_ONE_OF_GROUP_KEY && subSchema.type === "radioGroup";
    });

    if (selectOneOfSchema) {
      return _.map(selectOneOfSchema.body, function (subSchema) { return subSchema.name; }) || [];
    }

    return [];
  }

  function appendElements(body, schema, model, path, partOfChoice) {

    function toggleSelectedGroup(value) {
      $(body)
        .children("[data-select-one-of]")
        .hide()
        .filter("[data-select-one-of='" + value + "']")
        .show();
    }

    var selectOneOf = getSelectOneOfDefinition(schema);

    _.each(schema.body, function (subSchema) {
      var children = build(subSchema, model, path, save, partOfChoice);
      if (!_.isArray(children)) {
        children = [children];
      }
      _.each(children, function (elem) {
        if (_.indexOf(selectOneOf, subSchema.name) >= 0) {
          elem.setAttribute("data-select-one-of", subSchema.name);
          $(elem).hide();
        }
        if (elem) {
          // TODO can't really detect table cell from label key value
          if (!subSchema.label) {
            var td = document.createElement("td");
            td.appendChild(elem);
            elem = td;
          }
          body.appendChild(elem);
        }
      });
    });

    if (selectOneOf.length) {
      // Show current selection or the first of the group
      var myModel = _.first(selectOneOf);
      if (model[SELECT_ONE_OF_GROUP_KEY]) {
        myModel = model[SELECT_ONE_OF_GROUP_KEY].value;
      }

      toggleSelectedGroup(myModel);

      var s = "[name$='." + SELECT_ONE_OF_GROUP_KEY + "']";
      $(body).find(s).change(function () {
        toggleSelectedGroup(this.value);
      });
    }

    return body;
  }

  function loaderImg() {
    var img = document.createElement("img");
    img.src = "/img/ajax-loader-12.gif";
    img.alt = "...";
    img.width = 12;
    img.height = 12;
    return img;
  }

  function saveForReal(paths, values, callback) {
    var p = _.isArray(paths) ? paths : [paths];
    var v = _.isArray(values) ? values : [values];
    var updates = _.zip(
        _.map(p, function(path) {return path.replace(new RegExp("^" + self.docId + "."), "");}),
        v);

    ajax
      .command(getUpdateCommand(), { doc: self.docId, id: self.appId, updates: updates, collection: self.getCollection() })
    // Server returns empty array (all ok), or array containing an array with three
    // elements: [key status message]. Here we use just the status.
      .success(function (e) {
        var status = (e.results.length === 0) ? "ok" : e.results[0].result[0];
        callback(status, e.results);
      })
      .error(function (e) { error(e); callback("err"); })
      .fail(function (e) { error(e); callback("err"); })
      .call();
  }

  self.showValidationResults = function(results) {
    // remove warning and error highlights
    $("#document-" + self.docId).find("*").removeClass("warn").removeClass("error").removeClass("tip");
    // clear validation errors
    $("#document-" + self.docId + " .errorPanel").html("").fadeOut();
    // apply new errors & highlights
    if (results && results.length > 0) {
      _.each(results, function (r) {
        var level = r.result[0],
            code = r.result[1],
            pathStr = r.path.join("-");

        if (level !== "tip") {
          var errorPanel$ = $("#" + self.docId + "-" + pathStr + "-errorPanel");
          errorPanel$.append("<span class='" + level + "'>" + loc(["error", code]) + "</span>");
        }

        $("#" + pathStrToLabelID(pathStr)).addClass(level);
        $("#" + pathStrToID(pathStr)).addClass(level);
      });
    }
  };

  function validate() {
    if (!options || options.validate) {
      ajax
        .query("validate-doc", { id: self.appId, doc: self.docId, collection: self.getCollection() })
        .success(function (e) { self.showValidationResults(e.results); })
        .call();
    }
  }

  function disableBasedOnOptions() {
    if (!self.authorizationModel.ok(getUpdateCommand()) || options && options.disabled) {
      $(self.element).find("input, textarea").attr("readonly", true).unbind("focus");
      $(self.element).find("select, input[type=checkbox], input[type=radio]").attr("disabled", true);
      $(self.element).find("button").hide();
    }
  }

  function createIndicator(eventTarget, className) {
    className = className || "form-indicator";
    var parent$ = $(eventTarget.parentNode);
    parent$.find("." + className).remove();
    var indicator = document.createElement("span");
    indicator.className = className;
    parent$.append(indicator);
    return indicator;
  }

  function showIndicator(indicator, className, locKey) {
    var parent$ = $(indicator).closest("table");
    var i$ = $(indicator);

    if(parent$.length > 0) {
      // disable indicator text for table element
      i$.addClass(className).fadeIn(200);
    } else {
      i$.addClass(className).text(loc(locKey)).fadeIn(200);
    }

    setTimeout(function () {
      i$.removeClass(className).fadeOut(200, function () {});
    }, 4000);
  }

  function afterSave(label, loader, indicator, callback, status, results) {
    self.showValidationResults(results);
    if (label) {
      label.removeChild(loader);
    }
    if (status === "warn" || status === "tip") {
      showIndicator(indicator, "form-input-saved", "form.saved");
    } else if (status === "err") {
      showIndicator(indicator, "form-input-err", "form.err");
    } else if (status === "ok") {
      showIndicator(indicator, "form-input-saved", "form.saved");
    } else if (status !== "ok") {
      error("Unknown status:", status);
    }
    if (callback) { callback(); }
    // No return value or stoping the event propagation:
    // That would prevent moving to the next field with tab key in IE8.
  }

  function save(e, callback) {
    var event = getEvent(e);
    var target = event.target;
    var indicator = createIndicator(target);

    var path = target.name;
    var loader = loaderImg();

    var value = target.value;
    if (target.type === "checkbox") {
      value = target.checked;
    }

    var label = document.getElementById(pathStrToLabelID(path));
    if (label) {
      label.appendChild(loader);
    }

    saveForReal(path, value, _.partial(afterSave, label, loader, indicator, callback));
  }

  var emitters = {
    filterByCode: function(event, value, subSchema, sendLater) {
      var schemaValue = _.find(subSchema.body, {name: value});
      var code = schemaValue ? schemaValue.code : "";
      if (sendLater) {
        self.events.push({name: event, data: {code: code}});
      } else {
        hub.send(event, {code: code});
      }
    },
    valueChanged: function(event, value, subSchema, sendLater) {
      hub.send(event, {name: subSchema.name, value: value});
    },
    emitUnknown: function(event, value, subSchema) {
      error("Unknown emitter event:", event);
    }
  };

  function emit(value , subSchema, sendLater) {
    if (subSchema.emit) {
      _.forEach(subSchema.emit, function(event) {
        var emitter = emitters[event] || emitters.emitUnknown;
        emitter(event, value, subSchema, sendLater);
      });
    }
  }

  function emitLater(value, subSchema) {
    emit(value, subSchema, true);
  }

  function removeDoc(e) {
    var n$ = $(e.target).parent();
    while (!n$.is("section")) {
      n$ = n$.parent();
    }
    var op = self.schema.info.op;

    var documentName = "";
    if (op) {
      documentName = loc([op.name, "_group_label"]);
    } else {
      documentName = loc([self.schema.info.name, "_group_label"]);
    }

    function onRemovalConfirmed() {
      ajax.command("remove-doc", { id: self.appId, docId: self.docId, collection: self.getCollection() })
        .success(function () {
          n$.slideUp(function () { n$.remove(); });
          // This causes full re-rendering, all accordions change state etc. Figure a better way to update UI.
          // Just the "operations" list should be changed.
          repository.load(self.appId);
        })
        .call();
      return false;
    }

    var message = "<div>" + loc("removeDoc.message1") + " <strong>" + documentName + ".</strong></div><div>" + loc("removeDoc.message2") + "</div>";
    LUPAPISTE.ModalDialog.showDynamicYesNo(loc("removeDoc.sure"), message,
        { title: loc("removeDoc.ok"), fn: onRemovalConfirmed }, { title: loc("removeDoc.cancel") }, { html: true });

    return false;
  }

  function buildDescriptionElement(operation) {
    var wrapper = document.createElement("span");
    var descriptionSpan = document.createElement("span");
    var description = document.createTextNode("");
    var descriptionInput = document.createElement("input");
    var iconSpan = document.createElement("span");
    var iconSpanDescription = document.createElement("span");
    iconSpanDescription.appendChild(document.createTextNode(loc("edit")));

    // test ids
    if (options && options.dataTestSpecifiers) {
      descriptionSpan.setAttribute("data-test-id", "op-description");
      iconSpan.setAttribute("data-test-id", "edit-op-description");
      descriptionInput.setAttribute("data-test-id", "op-description-editor");
    }

    wrapper.className = "op-description-wrapper";
    descriptionSpan.className = "op-description";
    if (operation.description) {
      description.nodeValue = operation.description;
      descriptionInput.value = operation.description;
    } else {
      iconSpan.appendChild(iconSpanDescription);
    }

    wrapper.onclick = function(e) {
      var event = getEvent(e);
      // Prevent collapsing accordion when input is clicked
      event.stopPropagation();
    };

    descriptionInput.type = "text";
    descriptionInput.className = "accordion-input text hidden";

    var saveInput = function() {
      $(descriptionInput).off("blur");
      var value = _.trim(descriptionInput.value);
      if (value === "") {
        value = null;
        iconSpan.appendChild(iconSpanDescription);
      }

      ajax.command("update-op-description", {id: self.appId, "op-id": operation.id, desc: value })
        .success(function() {
          var indicator = createIndicator(descriptionInput, "accordion-indicator");
          showIndicator(indicator, "accordion-input-saved", "form.saved");
          hub.send("op-description-changed", {appId: self.appId, "op-id": operation.id, "op-desc": value});
        })
        .call();

      description.nodeValue = descriptionInput.value;
      $(descriptionInput).addClass("hidden");
      $(iconSpan).removeClass("hidden");
      $(descriptionSpan).removeClass("hidden");
    };

    descriptionInput.onfocus = function() {
      descriptionInput.onblur = function() {
        saveInput();
      };
    };

    descriptionInput.onkeyup = function(e) {
      // trigger save on enter and esc keypress
      var event = getEvent(e);
      event.stopPropagation();
      if (event.keyCode === 13 || event.keyCode === 27) {
        $(descriptionInput).off("blur");
        descriptionInput.blur();
        saveInput();
      }
    };

    iconSpan.className = "icon edit";
    iconSpan.onclick = function(e) {
      var event = getEvent(e);
      event.stopPropagation();

      if (iconSpan.contains(iconSpanDescription)) {
        iconSpan.removeChild(iconSpanDescription);
      }

      $(iconSpan).addClass("hidden");
      $(descriptionSpan).addClass("hidden");
      $(descriptionInput).removeClass("hidden");
      descriptionInput.focus();
    };

    descriptionSpan.appendChild(description);
    wrapper.appendChild(descriptionSpan);
    wrapper.appendChild(descriptionInput);
    wrapper.appendChild(iconSpan);
    return wrapper;
  }

  function buildElement() {
    var op = self.schema.info.op;

    var section = document.createElement("section");
    var icon = document.createElement("span");
    var title = document.createElement("h2");

    var sectionContainer = document.createElement("div");
    var elements = document.createElement("div");

    var accordionCollapsed = (options && options.accordionCollapsed) ? true : false;

    section.className = "accordion";
    section.setAttribute("data-doc-type", self.schemaName);
    elements.className = "accordion-fields";

    icon.className = "icon toggle-icon " + (accordionCollapsed ? "drill-right-white" : "drill-down-white");
    title.appendChild(icon);

    if (op) {
      title.appendChild(document.createTextNode(loc([op.name, "_group_label"])));
      if (authorizationModel.ok("update-op-description")) {
        title.appendChild(buildDescriptionElement(op));
      }
    } else {
      title.appendChild(document.createTextNode(loc([self.schema.info.name, "_group_label"])));
    }
    title.setAttribute("data-doc-id", self.docId);
    title.setAttribute("data-app-id", self.appId);
    title.onclick = accordion.click;
    if (self.schema.info.removable && !self.isDisabled && authorizationModel.ok("remove-doc")) {
      var removeSpan =
        $("<span>")
          .addClass("icon remove inline-right")
          .click(removeDoc);
      if (options && options.dataTestSpecifiers) {
        removeSpan.attr("data-test-class", "delete-schemas." + self.schemaName);
      }
      $(title).append(removeSpan);
    }

    if (self.schema.info.approvable) {
      elements.appendChild(self.makeApprovalButtons([], self.model));
    }

    sectionContainer.className = "accordion_content" + (accordionCollapsed ? "" : " expanded");
    sectionContainer.setAttribute("data-accordion-state", (accordionCollapsed ? "closed" : "open"));
    sectionContainer.id = "document-" + self.docId;

    appendElements(elements, self.schema, self.model, []);

    sectionContainer.appendChild(elements);
    section.appendChild(title);
    section.appendChild(sectionContainer);
    return section;
  }


  self.element = buildElement();
  // If doc.validationErrors is truthy, i.e. doc includes ready evaluated errors,
  // self.showValidationResults is called with in docgen.js after this docmodel has been appended to DOM.
  // So self.showValidationResults cannot be called here because of its jQuery lookups.
  if (!doc.validationErrors) {
    validate();
  }
  disableBasedOnOptions();

};
