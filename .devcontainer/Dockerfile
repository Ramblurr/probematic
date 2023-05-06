FROM mcr.microsoft.com/devcontainers/universal:2-linux

# Install Clojure - see https://github.com/Quantisan/docker-clojure/blob/master/target/openjdk-14-slim-buster/tools-deps/Dockerfile
ENV CLOJURE_VERSION=1.11.1.1273
WORKDIR /tmp
RUN \
apt-get update && \
apt-get install -y curl make rlwrap wget ripgrep fd-find fzf && \
rm -rf /var/lib/apt/lists/* && \
wget https://download.clojure.org/install/linux-install-$CLOJURE_VERSION.sh && \
chmod +x linux-install-$CLOJURE_VERSION.sh && \
./linux-install-$CLOJURE_VERSION.sh && \
su codespace -c "clojure -e '(clojure-version)'" && \
rm ./linux-install-$CLOJURE_VERSION.sh

# Install Lein
RUN \
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O /bin/lein && \
chmod uog+x /bin/lein && \
su codespace -c "JAVA_CMD=/home/codespace/java/current/bin/java /bin/lein"

# Cleanup
RUN apt-get purge -y --auto-remove