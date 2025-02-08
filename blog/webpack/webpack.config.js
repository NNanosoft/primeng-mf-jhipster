const { shareAll, withModuleFederationPlugin } = require('@angular-architects/module-federation/webpack');

module.exports = withModuleFederationPlugin({
  name: 'blog',

  exposes: {
    './translation-module': 'app/shared/language/translation.module.ts',
    './entity-navbar-items': 'app/entities/entity-navbar-items.ts',
    './entity-routes': 'app/entities/entity.routes.ts',
  },

  shared: {
    ...shareAll({ singleton: true, strictVersion: true, requiredVersion: 'auto' }),
  },
});
