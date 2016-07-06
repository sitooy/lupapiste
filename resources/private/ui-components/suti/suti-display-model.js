LUPAPISTE.SutiDisplayModel = function() {
  "use strict";
  var self = this;

  ko.utils.extend( self, new LUPAPISTE.ComponentBaseModel());

  var service = lupapisteApp.services.sutiService;

  self.showSuti = service.sutiEnabled;
  self.open = ko.observable( true );
  self.suti = service.sutiDetails;
  self.waiting = ko.observable();

  function sutiComputed( key, refresh ) {
    return self.disposedComputed( {
      read: function() {
        return _.get( self.suti(), ["suti", key]);
      },
      write: function( value ) {
        var app = lupapisteApp.models.application;
        if( app.id() ) {
          service.updateApplication( app,
                                     _.set( {}, key, value),
                                     {refresh: refresh,
                                      waiting: self.waiting});
        }
      }
    });
  }

  self.sutiId = sutiComputed( "id", true );

  self.sutiAdded = sutiComputed( "added" );

  self.disposedComputed( function() {
    var app = lupapisteApp.models.application;
    if( app.id() ) {
      service.fetchApplicationData( app, {waiting: self.waiting} );
    }
  });

  self.sutiLink = function() {
    window.open( self.suti().www, "_blank");
  };

  self.note = self.disposedComputed( function() {
    var prods = self.suti().products;
    var msg = false;
    var error = false;
    if( _.trim( self.sutiId())) {
      if( _.isString( prods ) ) {
        msg = prods;
        error = true;
      }
      else {
        if( _.isEmpty( prods )) {
          msg = "suti.display-empty";
        }
      }
    }
    return msg && {text: msg, error: error};
  });


  self.products = self.disposedComputed( function() {
    var prods = [];
    if( !self.note()) {
      prods = _.map( self.suti().products, function( p ) {
        var expired = (moment.isMoment( p.expirydate) && p.expirydate.isBefore( moment()));
        return _.merge( {},
                        p,
                        {expired: expired,
                         state: "suti.display-" + (expired ? "expired" : "valid")
                        } );
      });
    }
    return prods;
  });
};
