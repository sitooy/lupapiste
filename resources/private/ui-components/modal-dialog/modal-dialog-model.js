LUPAPISTE.ModalDialogModel = function (params) {
  "use strict";
  var self = this;
  self.showDialog = ko.observable(false);
  self.contentName = ko.observable();
  self.contentParams = ko.observable();

  self.submitButtonTitleLoc = ko.observable();
  self.submitButtonFunc = null;

  self.windowWidth = ko.observable();
  self.windowHeight = ko.observable();
  self.title = ko.observable();
  self.dialogVisible = ko.observable(false);

  self.showDialog.subscribe(function(show) {
    _.delay(function(show) {
      self.dialogVisible(show);
    }, 100, show);
  });

  self.dialogWidth = ko.pureComputed(function() {
    return self.windowWidth() - 200;
  });

  self.dialogHeight = ko.pureComputed(function() {
    return self.windowHeight() - 150;
  });

  self.closeDialog = function() {
    self.showDialog(false);
    $("html").removeClass("no-scroll");
  };

  hub.subscribe("show-dialog", function(data) {
    $("html").addClass("no-scroll");
    self.contentName(data.contentName);
    self.contentParams(data.contentParams);

    self.submitButtonTitleLoc(data.submitButtonTitleLoc);
    self.submitButtonFunc = data.submitButtonFunc;

    self.showDialog(true);
    self.title(loc(data.titleLoc));
  });

  var setWindowSize = function(width, height) {
    self.windowWidth(width);
    self.windowHeight(height);
  };

  var win = $(window);
  // set initial dialog size
  setWindowSize(win.width(), win.height());

  // listen widow change events
  win.resize(function() {
    setWindowSize(win.width(), win.height());
  });
};
