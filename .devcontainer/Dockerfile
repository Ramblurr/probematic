FROM mcr.microsoft.com/devcontainers/universal:2-linux

# Install Clojure
ENV CLOJURE_VERSION=1.11.1.1273
WORKDIR /tmp
RUN set -e;\
apt-get update; \
apt-get install -y curl make rlwrap wget ripgrep fd-find fzf; \
rm -rf /var/lib/apt/lists/*; \
wget https://download.clojure.org/install/linux-install-$CLOJURE_VERSION.sh; \
chmod +x linux-install-$CLOJURE_VERSION.sh; \
./linux-install-$CLOJURE_VERSION.sh; \
su codespace -c "clojure -e '(clojure-version)'"; \
rm ./linux-install-$CLOJURE_VERSION.sh

# Install Lein
RUN set -e; \
wget https://raw.githubusercontent.com/technomancy/leiningen/stable/bin/lein -O /bin/lein; \
chmod uog+x /bin/lein; \
su codespace -c "JAVA_CMD=/home/codespace/java/current/bin/java /bin/lein"

# Install babashka
RUN set -e; \
  wget https://raw.githubusercontent.com/babashka/babashka/master/install -O /tmp/bb-install; \
  chmod +x /tmp/bb-install; \
  /tmp/bb-install; \
  su codespace -c "bb --version"; \
  rm /tmp/bb-install

# Cleanup
RUN apt-get purge -y --auto-remove
