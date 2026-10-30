FROM debian:trixie-slim@sha256:f6b08b09a05725f9431502d3b5714f5c4576e82c1a84fa1debe0c24a7e0c3ec4

#
# 1. OS packages, filesystem layout, and user creation
#     - Within reason, the versions of these apt dependencies do not matter
#     - Creates a non-root user for both required Nix compatibility, and for security best practices
#     - Creates the directory that will hold our software; it is owned by the non-root user
#

ENV OS_USER=customuser
ENV OS_USER_GROUP=custom

RUN apt-get update && \
    DEBIAN_FRONTEND=noninteractive apt-get install -y \
      ca-certificates \
      curl \
      xz-utils \
      bash \
      coreutils \
      sudo && \
    rm -rf /var/lib/apt/lists/*

RUN mkdir -p "/custom-software"

RUN groupadd -r "${OS_USER_GROUP}" && useradd -m -r -g "${OS_USER_GROUP}" "${OS_USER}" && \
    echo "${OS_USER} ALL=(root) NOPASSWD: ALL" > "/etc/sudoers.d/${OS_USER}" && chmod 440 "/etc/sudoers.d/${OS_USER}" && \
    chown "${OS_USER}":"${OS_USER_GROUP}" "/custom-software"

USER ${OS_USER}

#
# 2. Nix installation
#     - The Nix version only matters in one aspect: it must be new enough to support new features
#     - Aside from that, version does not matter as it is declarative based on the lockfile and flake
#

RUN curl -L https://nixos.org/nix/install | sh -s -- --no-daemon
ENV PATH=/home/${OS_USER}/.nix-profile/bin:$PATH
ENV NIX_CONFIG="experimental-features = nix-command flakes"

#
# 3. Snapshot the runtime environment from the flake
#     - We snapshot the runtime dev shell environment once at image build time, then reuse it at runtime without invoking Nix again
#     - This is for security reasons and for minimizing runtime complexity
#

COPY flake.nix flake.lock /custom-software/buildtime-workspace/

RUN mkdir -p /custom-software/runtime-workspace && \
    nix print-dev-env /custom-software/buildtime-workspace/#runtime > /custom-software/runtime-workspace/nix-env.sh

#
# 4. Application bits and entrypoint
#     - Copies the application distribution from the fleet release artifact
#     - Creates an entrypoint runtime wrapper that:
#         - Applies the flake-defined runtime dev shell environment recorded at image build time
#         - Runs java on the distribution's lib/ jars, with the main class and its arguments taken from CMD
#

COPY executable /custom-software/runtime-workspace/executable

RUN printf '%s\n' \
  '#!/usr/bin/env bash' \
  'set -euo pipefail' \
  'set -a' \
  ". /custom-software/runtime-workspace/nix-env.sh" \
  'set +a' \
  "exec java -XX:MaxRAMPercentage=75.0 -cp '/custom-software/runtime-workspace/executable/lib/*' \"\$@\"" \
  > /custom-software/runtime-workspace/run-executable && \
  chmod +x /custom-software/runtime-workspace/run-executable

ENTRYPOINT ["/custom-software/runtime-workspace/run-executable"]
CMD ["io.template.Main"]
