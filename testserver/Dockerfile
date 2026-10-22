FROM itzg/minecraft-server:latest

RUN apt-get update && apt-get install -y --no-install-recommends \
    curl \
    && rm -rf /var/lib/apt/lists/*

USER minecraft

EXPOSE 25565 5005 25575
