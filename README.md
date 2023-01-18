# probematic

> "probe" = "rehersal" in German. Pronounced PRO-beh ([listen](https://upload.wikimedia.org/wikipedia/commons/f/f9/De-probe.ogg))

Making probes automatic... and so much more.

### Pre-req

1. You need clojure installed `clj`
2. You need datomic dev-local installed locally [see here](https://docs.datomic.com/cloud/dev-local.html)
3. You need node/npm installed to build the CSS

### Build the CSS

``` sh
npm install

npx tailwindcss -i resources/public/css/main.css -o resources/public/css/compiled/main.css
```

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


## Notes for Future Me

```

# term 1
bb repl

# term 2
bb watch-css

# open user.clj
# connect to repl
# (dev)
# (go)
```

