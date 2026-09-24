#!/usr/bin/env bash
set -euo pipefail

# Resolve the services used by this project before closing outbound traffic.
# Run again after DNS changes to refresh the allowlist.
ipset create r3f3r_allowed hash:net -exist
ipset flush r3f3r_allowed

for domain in \
  github.com api.github.com raw.githubusercontent.com objects.githubusercontent.com \
  registry.npmjs.org repo.maven.apache.org \
  marketplace.visualstudio.com vscode.blob.core.windows.net update.code.visualstudio.com; do
  while read -r address; do
    if [[ "$address" =~ ^[0-9]+\.[0-9]+\.[0-9]+\.[0-9]+$ ]]; then
      ipset add r3f3r_allowed "$address" -exist
    fi
  done < <(dig +short A "$domain")
done

gateway=$(ip -4 route show default | awk 'NR == 1 { print $3 }')
if [[ -z "$gateway" ]]; then
  echo 'Could not identify the Docker gateway; firewall unchanged.' >&2
  exit 1
fi

# Keep Docker's NAT rules intact so its built-in DNS resolver still works.
iptables -P INPUT ACCEPT
iptables -P FORWARD ACCEPT
iptables -P OUTPUT ACCEPT
iptables -F
iptables -A INPUT -i lo -j ACCEPT
iptables -A OUTPUT -o lo -j ACCEPT
iptables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -d 127.0.0.11 -j ACCEPT
iptables -A INPUT -s "$gateway" -p tcp -m multiport --dports 5173,8080 -j ACCEPT
iptables -A OUTPUT -d "$gateway" -p tcp --dport 1521 -j ACCEPT
iptables -A OUTPUT -m set --match-set r3f3r_allowed dst -p tcp \
  -m multiport --dports 22,80,443 -j ACCEPT
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT DROP

# The project uses IPv4; close IPv6 to avoid an unfiltered route.
if command -v ip6tables >/dev/null 2>&1; then
  ip6tables -P INPUT ACCEPT
  ip6tables -P FORWARD ACCEPT
  ip6tables -P OUTPUT ACCEPT
  ip6tables -F
  ip6tables -A INPUT -i lo -j ACCEPT
  ip6tables -A OUTPUT -o lo -j ACCEPT
  ip6tables -A INPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  ip6tables -A OUTPUT -m conntrack --ctstate ESTABLISHED,RELATED -j ACCEPT
  ip6tables -P INPUT DROP
  ip6tables -P FORWARD DROP
  ip6tables -P OUTPUT DROP
fi
