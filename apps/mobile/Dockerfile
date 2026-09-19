FROM --platform=linux/amd64 ubuntu:24.04

ENV DEBIAN_FRONTEND=noninteractive
ENV ANDROID_HOME=/opt/android-sdk
ENV ANDROID_SDK_ROOT=/opt/android-sdk
ENV GOROOT=/usr/local/go
ENV GOPATH=/root/go
ENV GOTOOLCHAIN=auto
ENV PATH=$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$GOROOT/bin:$GOPATH/bin:$PATH

# ---- base packages ----
RUN apt-get update && apt-get install -y --no-install-recommends \
        openjdk-17-jdk-headless \
        wget unzip git curl ca-certificates \
    && rm -rf /var/lib/apt/lists/*

# ---- Android command-line tools ----
# Update CMDLINE_TOOLS_VERSION if the download URL stops working:
# https://developer.android.com/studio#command-tools
ARG CMDLINE_TOOLS_VERSION=13114758
RUN mkdir -p $ANDROID_HOME/cmdline-tools && \
    wget -q "https://dl.google.com/android/repository/commandlinetools-linux-${CMDLINE_TOOLS_VERSION}_latest.zip" \
         -O /tmp/cmdline-tools.zip && \
    unzip -q /tmp/cmdline-tools.zip -d /tmp && \
    mv /tmp/cmdline-tools $ANDROID_HOME/cmdline-tools/latest && \
    rm /tmp/cmdline-tools.zip

# ---- Android SDK packages ----
RUN yes | sdkmanager --licenses > /dev/null 2>&1; \
    sdkmanager \
        "platform-tools" \
        "platforms;android-36" \
        "build-tools;36.0.0" \
        "cmake;3.22.1" \
        "ndk;28.2.13676358"

# ---- Go ----
# Must match (or exceed) the `go` directive in go/go.mod
ARG GO_VERSION=1.25.3
RUN wget -q "https://go.dev/dl/go${GO_VERSION}.linux-amd64.tar.gz" -O /tmp/go.tar.gz && \
    tar -C /usr/local -xzf /tmp/go.tar.gz && \
    rm /tmp/go.tar.gz

# ---- gomobile ----
RUN go install golang.org/x/mobile/cmd/gomobile@latest && \
    go install golang.org/x/mobile/cmd/gobind@latest

# ---- entrypoint ----
COPY docker-entrypoint.sh /usr/local/bin/docker-entrypoint.sh
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

WORKDIR /workspace
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
