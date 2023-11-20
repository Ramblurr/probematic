# probematic

[![AGPL-3.0-or-later](https://img.shields.io/badge/license-AGPL--v3--or--later-blue)](./LICENSE)

> "Probe" = "rehearsal" in German. Pronounced PRO-beh ([listen](https://upload.wikimedia.org/wikipedia/commons/f/f9/De-probe.ogg))

Canonical repo: https://github.com/Ramblurr/probematic

### Pre-req

1. You need clojure installed `clj`
2. You need node/npm installed to build the CSS

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

Jack-in using your favorite editor. Be sure to supply `-A:dev` to set the dev profile

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


## License

This software is licensed under the GNU AGPL v3.0 or later.

```
probematic
Copyright (C) 2022-2023 Casey Link

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.
```
