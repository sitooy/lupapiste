var loc;

;(function() {

  loc = function(key) {
    var term = loc.terms[key];
    if (term === undefined) {
      debug("Missing localization key", key);
      return "$$NOT_FOUND$$" + key;
    }
    return term;
  };
  
  loc.supported = [];
  loc.currentLanguage = null;
  loc.terms = {};
  
  function resolveLang() {
    var url = window.parent ? window.parent.location.pathname : location.pathname;
    var langEndI = url.indexOf("/", 1);
    var lang = langEndI > 0 ? url.substring(1, langEndI) : null;
    return _.contains(loc.supported, lang) ? lang : "fi";
  }
  
  loc.setTerms = function(newTerms) {
    loc.supported = _.keys(newTerms);
    loc.currentLanguage = resolveLang();
    loc.terms = newTerms[loc.currentLanguage];
  }

  hub.subscribe("change-lang", function(e) {
    var lang = e.lang;
    if (_.contains(loc.supported, lang)) {
      var url = location.href.replace("/" + loc.currentLanguage + "/", "/" + lang + "/");
      window.location = url;
    }
  });

  // FIXME: This does not work with new localizations.
  loc.toMap = function() { return loc.terms["error"]; };

  loc.getCurrentLanguage = function() { return loc.currentLanguage; };
  loc.getSupportedLanguages = function() { return loc.supported; };
  
})();
