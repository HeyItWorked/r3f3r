FROM node:20-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends \
    ca-certificates curl dnsutils git iproute2 ipset iptables jq less \
    procps sudo zsh \
    && apt-get clean && rm -rf /var/lib/apt/lists/*

COPY init-firewall.sh /usr/local/bin/init-firewall.sh
RUN chmod 0755 /usr/local/bin/init-firewall.sh \
    && echo 'node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh' > /etc/sudoers.d/node-firewall \
    && chmod 0440 /etc/sudoers.d/node-firewall

USER node
WORKDIR /workspace
