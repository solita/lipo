FROM clojure:openjdk-11-tools-deps
RUN apt-get update && \
    apt-get install -y tmux && \
    rm -rf /var/lib/apt/lists/*
COPY ./deps.edn /app/
COPY ./config-aws.edn /app/config.edn
WORKDIR /app
RUN clj < /dev/null
EXPOSE 80
COPY ./resources /app/resources
COPY ./src /app/src
CMD tmux -S /tmp/lipo-tmux new-session -d "clj -M -e \"(do (require 'lipo.main) (lipo.main/main))\" -r"  && tail --retry --follow=name /app/lipo.log
