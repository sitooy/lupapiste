LUPAPISTE.InfoLinkModel = function( params ) {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.infoService;

  self.link = service.infoLink( params.id );

  self.isTemporary = self.disposedPureComputed( function() {
    return service.isTemporaryId( self.link().id );
  });

  self.canEdit = self.disposedPureComputed( function() {
    return service.canEdit();
  });

  var editing = ko.observable( self.isTemporary() || Boolean(self.link().editing));

  self.textFocus = ko.observable( editing());
  self.urlFocus  = ko.observable();

  self.textInput = ko.observable(_.get(self.link().editing, "text"));
  self.urlInput  = ko.observable(_.get(self.link().editing, "url"));

  delete self.link().editing;

  self.editorEdit = self.disposedComputed( {
    read: function() {
      return editing();
    },
    write: function( flag ) {
      if( flag ) {
        self.textInput( self.link().text );
        self.urlInput( self.link().url );
        self.textFocus( true );
        self.urlFocus( false );
      }
      editing( flag );
      self.link().editing = flag;
    }
  });

  self.editorView = self.disposedPureComputed( function() {
    return service.canEdit() && !self.editorEdit();
  });

  self.canSave = self.disposedComputed( function() {
    return _.trim( self.textInput() ) && _.trim( self.urlInput());
  });

  self.save = function() {
    if( self.canSave()) {
      self.sendEvent( service.serviceName,
                      "save",
                      {id: self.link().id,
                       text: self.textInput(),
                       url: self.urlInput()});
    }
  };

  self.remove = function() {
    self.sendEvent( service.serviceName,
                    "delete",
                    {id: self.link().id});
  };

  self.cancel = function() {
    self.editorEdit( false );
    if( self.isTemporary() ) {
      self.remove();
    }
  };

  self.addHubListener( "side-panel-esc-pressed",
                       function( data ) {
                         if( self.editorEdit()
                             && (self.textFocus() || self.urlFocus())) {
                           self.cancel();
                           data.canClose(false);
                         }
                       });

  self.addHubListener( "side-panel-closing", self.cancel );

  self.handleKey = function( data, event ) {
    if( event.which === 9 ) {
      // Tab switches between text fields.
      self.textFocus(!self.textFocus());
      // textFocus value has now changed.
      self.urlFocus( !self.textFocus());
    } else {
      return true;
    }
  };

  self.addEventListener( service.serviceName,
                         "save-edit-state",
                         function( data ) {
                           var id = self.link().id;
                           var obj = _.get( data.states, id, {});
                           if( self.editorEdit() ) {
                             data.states[id] = _.set( obj, "editing",
                                                      {text: self.textInput(),
                                                       url: self.urlInput()});

                           }
                         });

  // Drag'n'drop

  var ddHub = "info-link-drag-drop-event";

  self.dragging = ko.observable();

  var zoneId = null;

  self.dragStart = function( item, event ) {
    zoneId = null;
    // Dragging only allowed using the drag handle.
    var goodGrab =  _.get( document.elementFromPoint( event.clientX,
                                                      event.clientY ),
                           "className") === "lupicon-arrows-up-down";
    item.dragging( goodGrab );
    return goodGrab;
  };

  self.dragEnd = function( item ) {
    item.dragging( false );
    hub.send( ddHub, {});
  };

  function isTarget( targetId, link ) {
        return link().id === targetId;
  }

  self.dragDrop = function( item ) {
    var dragId = item.link().id;
    if( zoneId && zoneId !== dragId ) {
      var links = service.infoLinks();
      var drag = _.find( links, _.partial( isTarget, dragId ));
      _.remove( links, _.partial( isTarget, dragId ) );
      links.splice( _.findIndex( links, _.partial( isTarget, zoneId)),
                    0, drag );
      service.infoLinks( links );
    }
  };

  self.showSlotBefore = ko.observable();

  self.addHubListener(ddHub, function( params ) {
    self.showSlotBefore( self.link().id === params.zoneId );
  });

  self.dragOver = function( event, dragData, zoneData ) {
    zoneId = zoneData.id;
    hub.send( ddHub, {zoneId: zoneId});
  };

};
