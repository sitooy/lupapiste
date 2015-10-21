LUPAPISTE.ApplicationBulletinsService = function() {
  "use strict";
  var self = this;

  self.data = ko.observableArray([]);
  self.bulletinsLeft = ko.observable(0);

  self.fetchBulletins = _.debounce(function (query) {
    ajax.datatables("application-bulletins", {page:         query.page,
                                              searchText:   util.getIn(query, ["searchText"],         ""),
                                              municipality: util.getIn(query, ["municipality", "id"], "")})
      .success(function(res) {
        self.bulletinsLeft(res.left);
        if (query.page === 1) {
          self.data(res.data);
        } else {
          self.data(self.data().concat(res.data));
        }
      })
      .call();
  }, 250);

  self.fetchMunicipalities = function() {
    return [297, 753];
  }
};

