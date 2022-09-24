# probematic

Making probes automatic.

## Generated Documentation

* `/api` - the swagger docs for the "rest" http api
* `/qraphiql` - the graphql ui

## Resources

This app uses a variety of clojure libraries. While developing this app you'll need to be familiar with them.

* Lacinia ([docs](https://lacinia.readthedocs.io/en/latest/overview.html)) - graphql server
* Urania ([docs](http://funcool.github.io/urania/latest/)) - async data loader used by superlifter in our graphql resolvers
* Chime ([docs](https://github.com/jarohen/chime)) - lightweight scheduler
* tick ([docs](https://github.com/juxt/tick)) - time library
* Integrant ([docs](https://github.com/weavejester/integrant)) - data-driven dependency injection
