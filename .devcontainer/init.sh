#!/usr/bin/env bash

echo export JAVA_TOOL_OPTIONS=\"\$JAVA_TOOL_OPTIONS -Dsun.java2d.xrender=false\" >> /home/codespace/.bashrc

# prepare deps

clojure -Sdeps '{:deps {nrepl/nrepl {:mvn/version,"1.0.0"},cider/cider-nrepl {:mvn/version,"0.30.0"}, djblue/portal {:mvn/version "0.40.0"}}}' -P
