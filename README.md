# probematic

> "probe" = "rehersal" in German. Pronounced PRO-beh ([listen](https://upload.wikimedia.org/wikipedia/commons/f/f9/De-probe.ogg))

Making probes automatic.


### Pre-req

1. You need clojure installed `clj`
2. You need datomic dev-local installed locally [see here](https://docs.datomic.com/cloud/dev-local.html)

### Run in demo mode

``` sh
APP_PROFILE=demo clj -M:run-m
```

Then visit [http://localhost:6160]


### Run in repl

Jack-in using your favorite editor. Be sure to supply `-A:dev` to set the dev proflie

1. from the `user` ns run `(dev)`
   this loads the app code and switches you into the `dev` ns
2. run `(dev-extra/go)` to start the integrant system and boot the app
