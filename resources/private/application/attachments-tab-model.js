LUPAPISTE.AttachmentsTabModel = function(appModel) {
  "use strict";

  var self = this;

  self.appModel = appModel;

  var postVerdictStates = {verdictGiven:true, constructionStarted:true, closed:true};
  self.postVerdict = ko.observable(false);

  self.preAttachmentsByOperation = ko.observableArray();
  self.postAttachmentsByOperation = ko.observableArray();

  self.unsentAttachmentsNotFound = ko.observable(false);
  self.sendUnsentAttachmentsButtonDisabled = ko.computed(function() {
    return self.appModel.pending() || self.appModel.processing() || self.unsentAttachmentsNotFound();
  });

  var fGroupByOperation = function(attachment) {
    return attachment.op ? attachment.op['name'] : 'attachments.general';
  }

  /* Sorting function to sort attachments into
   * same order as in allowedAttachmentTypes -observable
   */
  var fSortByAllowedAttachmentType = function(a, b) {
    var types = _.flatten(self.appModel.allowedAttachmentTypes(), true);

    var atg = a.type['type-group'];
    var atgIdx = _.indexOf(types, atg);
    var atid = a.type['type-id'];

    var btg = b.type['type-group'];
    var btgIdx = _.indexOf(types, btg);
    var btid = b.type['type-id'];

    if ( atg === btg ) {
      // flattened array of allowed attachment types.
      // types[atgIdx + 1] is array of type-ids,
      // which correnspond to type-group in atgIdx
      return _.indexOf(types[atgIdx + 1], atid) - _.indexOf(types[btgIdx + 1], btid);
    } else {
      return atgIdx - btgIdx;
    }
  }

  function getPreAttachments(source) {
    return _.filter(source, function(attachment) {
          return !postVerdictStates[attachment.applicationState];
      });
  }

  function getPostAttachments(source) {
    return _.filter(source, function(attachment) {
          return postVerdictStates[attachment.applicationState];
      });
  }

  /*
   * Returns attachments (source), grouped by grouping function f.
   * Optionally sorts using sort
   */
  function getAttachmentsByGroup(source, f, sort) {
    var attachments = _.map(source, function(a) {
      a.latestVersion = _.last(a.versions || []);
      a.statusName = LUPAPISTE.statuses[a.state] || "unknown";
      return a;
    });
    if ( _.isFunction(sort) ) {
      attachments.sort(sort);
    }
    var grouped = _.groupBy(attachments, f);
    return _.map(grouped, function(attachments, group) { return {group: group, attachments: attachments}; });
  }

  function unsentAttachmentFound(attachments) {
    return _.some(attachments, function(a) {
      var lastVersion = _.last(a.versions);
      return lastVersion &&
             (!a.sent || lastVersion.created > a.sent) &&
             (!a.target || (a.target.type !== "statement" && a.target.type !== "verdict"));
    });
  }

  self.refresh = function(appModel) {
    self.appModel = appModel;
    var rawAttachments = ko.mapping.toJS(appModel.attachments);
    // Post/pre verdict state?
    self.postVerdict(!!postVerdictStates[self.appModel.state()]);

    var preAttachments = getPreAttachments(rawAttachments);
    var postAttachments = getPostAttachments(rawAttachments)

    var preGrouped = getAttachmentsByGroup(preAttachments, fGroupByOperation, fSortByAllowedAttachmentType);
    var postGrouped = getAttachmentsByGroup(postAttachments, fGroupByOperation, fSortByAllowedAttachmentType);

    self.preAttachmentsByOperation(preGrouped);
    self.postAttachmentsByOperation(postGrouped);

    self.unsentAttachmentsNotFound(!unsentAttachmentFound(rawAttachments));
  }

  self.sendUnsentAttachmentsToBackingSystem = function() {
    ajax
      .command("move-attachments-to-backing-system", {id: self.appModel.id(), lang: loc.getCurrentLanguage()})
      .success(self.appModel.reload)
      .processing(self.appModel.processing)
      .pending(self.appModel.pending)
      .call();
  };

  self.newAttachment = function() {
    attachment.initFileUpload(self.appModel.id(), null, null, true);
  };

  self.copyOwnAttachments = function(model) {
    ajax.command("copy-user-attachments-to-application", {id: self.appModel.id()})
      .success(self.appModel.reload)
      .processing(self.appModel.processing)
      .call();
    return false;
  };

  self.deleteSingleAttachment = function(a) {
    var attId = _.isFunction(a.id) ? a.id() : a.id;
    LUPAPISTE.ModalDialog.showDynamicYesNo(
      loc("attachment.delete.header"),
      loc("attachment.delete.message"),
      {title: loc("yes"),
       fn: function() {
        ajax.command("delete-attachment", {id: self.appModel.id(), attachmentId: attId})
          .success(function() {
            self.appModel.reload();
          })
          .error(function (e) {
            LUPAPISTE.ModalDialog.showDynamicOk(loc("error.dialog.title"), loc(e.text));;
          })
          .processing(self.appModel.processing)
          .call();
        return false;
      }},
      {title: loc("no")});
  };

  self.attachmentTemplatesModel = new function() {
    var templateModel = this;
    templateModel.ok = function(ids) {
      ajax.command("create-attachments", {id: self.appModel.id(), attachmentTypes: ids})
        .success(function() { repository.load(self.appModel.id()); })
        .complete(LUPAPISTE.ModalDialog.close)
        .call();
    };

    templateModel.init = function() {
      templateModel.selectm = $("#dialog-add-attachment-templates .attachment-templates").selectm();
      templateModel.selectm.ok(templateModel.ok).cancel(LUPAPISTE.ModalDialog.close);
      return templateModel;
    };

    templateModel.show = function() {
      var data = _.map(self.appModel.allowedAttachmentTypes(), function(g) {
        var groupId = g[0];
        var groupText = loc(["attachmentType", groupId, "_group_label"]);
        var attachemntIds = g[1];
        var attachments = _.map(attachemntIds, function(a) {
          var id = {"type-group": groupId, "type-id": a};
          var text = loc(["attachmentType", groupId, a]);
          return {id: id, text: text};
        });
        return [groupText, attachments];
      });
      templateModel.selectm.reset(data);
      LUPAPISTE.ModalDialog.open("#dialog-add-attachment-templates");
      return templateModel;
    };
  }();
};
