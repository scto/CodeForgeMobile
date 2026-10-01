#!/bin/sh
set -e

export HOME=/root
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export ANDROID_SDK_HOME=$HOME/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk
export CMAKE_HOME=$ANDROID_HOME/cmake
export JAVA_HOME=/usr/lib/jvm/default-java
export GRADLE_HOME=/usr/share/gradle
export GRADLE_USER_HOME=$HOME/.gradle
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$ANDROID_HOME/build-tools:$ANDROID_NDK_HOME:$CMAKE_HOME:$GRADLE_HOME/bin:/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export DEBIAN_FRONTEND=noninteractive
export COLORTERM=truecolor

resolve_runtime_hostname() {
    if [ -n "$CODEFORGEMOBILE_GUEST_HOSTNAME" ]; then
        printf '%s' "$CODEFORGEMOBILE_GUEST_HOSTNAME"
        return 0
    fi

    if command -v hostname >/dev/null 2>&1; then
        runtime_name=$(hostname 2>/dev/null || true)
        if [ -n "$runtime_name" ]; then
            printf '%s' "$runtime_name"
            return 0
        fi
    fi

    if [ -r /etc/hostname ]; then
        IFS= read -r file_name < /etc/hostname || true
        if [ -n "$file_name" ]; then
            printf '%s' "$file_name"
            return 0
        fi
    fi

    printf '%s' "ubuntu"
}

install_hostname_wrapper() {
    wrapper_dir=/tmp/codeforgemobile-runtime/bin
    mkdir -p "$wrapper_dir"
    cat > "$wrapper_dir/hostname" <<'EOF'
#!/bin/sh
resolve_name() {
    if [ -n "$CODEFORGEMOBILE_GUEST_HOSTNAME" ]; then
        printf '%s\n' "$CODEFORGEMOBILE_GUEST_HOSTNAME"
        return 0
    fi

    if [ -r /etc/hostname ]; then
        IFS= read -r file_name < /etc/hostname || true
        if [ -n "$file_name" ]; then
            printf '%s\n' "$file_name"
            return 0
        fi
    fi

    printf '%s\n' "ubuntu"
}

case "$1" in
    ""|-s|--short|-f|--fqdn)
        resolve_name
        ;;
    *)
        printf '%s\n' "hostname is managed from /etc/hostname in this session" >&2
        exit 1
        ;;
esac
EOF
    chmod 755 "$wrapper_dir/hostname"
    PATH="$wrapper_dir:$PATH"
    export PATH
}

RUNTIME_HOSTNAME=$(resolve_runtime_hostname)
export CODEFORGEMOBILE_GUEST_HOSTNAME="$RUNTIME_HOSTNAME"
export HOST="$RUNTIME_HOSTNAME"
export HOSTNAME="$RUNTIME_HOSTNAME"
install_hostname_wrapper

mkdir -p /etc
if [ -L /etc/resolv.conf ]; then
    rm -f /etc/resolv.conf
fi

if [ ! -s /etc/resolv.conf ]; then
    rm -f /etc/resolv.conf
    printf 'nameserver 1.1.1.1\nnameserver 8.8.8.8\n' > /etc/resolv.conf
fi

# Disable APT sandbox for PRoot environments
if [ -d /etc/apt ]; then
    mkdir -p /etc/apt/apt.conf.d
    echo 'APT::Sandbox::User "root";' > /etc/apt/apt.conf.d/99codeforgemobile-proot
fi

UBUNTU_INIT_DONE=/etc/codeforgemobile-ubuntu-init.done
UBUNTU_INIT_LOCK=/etc/codeforgemobile-ubuntu-init.lock
UBUNTU_INIT_FAILED=/etc/codeforgemobile-ubuntu-init.failed
UBUNTU_INIT_LOG=/tmp/codeforgemobile-ubuntu-init.log

run_ubuntu_init_in_foreground() {
    UBUNTU_INIT_RUNNER=/tmp/codeforgemobile-ubuntu-init.runner.sh

    cat > "$UBUNTU_INIT_RUNNER" <<'EOF'
#!/bin/sh
set +e

UBUNTU_INIT_DONE="$1"
UBUNTU_INIT_LOCK="$2"
UBUNTU_INIT_FAILED="$3"

setup_ready=1

echo "[CodeForgeMobile] Initializing Ubuntu (first boot)..."

# Install base packages
export DEBIAN_FRONTEND=noninteractive
if command -v apt-get >/dev/null 2>&1; then
    echo "[CodeForgeMobile] Updating apt repos & installing essential packages (openjdk-17-jdk, gradle)..."
    apt-get update && apt-get install -y --no-install-recommends \
        bash coreutils findutils grep sed gawk curl wget git \
        build-essential sudo procps ca-certificates tar gzip bzip2 \
        xz-utils unzip zip nano locales net-tools iputils-ping openjdk-17-jdk gradle || setup_ready=0
fi

# Setup $HOME/android-sdk environment & subdirectories
echo "[CodeForgeMobile] Setting up $HOME/android-sdk directory structure..."
mkdir -p "$HOME/android-sdk/cmdline-tools/latest"
mkdir -p "$HOME/android-sdk/build-tools"
mkdir -p "$HOME/android-sdk/platform-tools"
mkdir -p "$HOME/android-sdk/ndk"
mkdir -p "$HOME/android-sdk/cmake"
mkdir -p "$HOME/android-sdk/licenses"

# Pre-accept Android SDK licenses
printf "24333f8a63718c392d9080e206772cb2e444d1e6\n893307222625902b9f1176141758443f34566164\nd56f51874794514bf1a5f91ec65a85c6a1f76103\n" > "$HOME/android-sdk/licenses/android-sdk-license"
printf "84831b9409646a918e30573bab4c9c91346d8abd\n" > "$HOME/android-sdk/licenses/android-sdk-preview-license"
printf "d975f751698a77b39ddd0b12179942c370484129\n" > "$HOME/android-sdk/licenses/intel-android-sys-img-license"

# Download and install Google cmdline-tools if missing
if [ ! -f "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "[CodeForgeMobile] Downloading Google cmdline-tools..."
    mkdir -p /tmp/cmdline-tools-tmp
    (curl -sSL "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -o /tmp/cmdline-tools.zip || \
     wget -q "https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip" -O /tmp/cmdline-tools.zip || true)

    if [ -f /tmp/cmdline-tools.zip ]; then
        echo "[CodeForgeMobile] Unpacking Google cmdline-tools..."
        unzip -q /tmp/cmdline-tools.zip -d /tmp/cmdline-tools-tmp || true
        if [ -d /tmp/cmdline-tools-tmp/cmdline-tools ]; then
            cp -r /tmp/cmdline-tools-tmp/cmdline-tools/* "$HOME/android-sdk/cmdline-tools/latest/" || true
            chmod +x "$HOME/android-sdk/cmdline-tools/latest/bin/"* || true
        fi
        rm -rf /tmp/cmdline-tools.zip /tmp/cmdline-tools-tmp
    fi
fi

# Install build-tools 36.0.0, platform-tools, ndk, cmake via sdkmanager
if [ -x "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" ]; then
    echo "[CodeForgeMobile] Installing build-tools 36.0.0, platform-tools, ndk, cmake via sdkmanager..."
    export JAVA_HOME=$(dirname $(dirname $(readlink -f $(which java 2>/dev/null || echo /usr/bin/java))))
    export PATH=$JAVA_HOME/bin:$HOME/android-sdk/cmdline-tools/latest/bin:$PATH
    yes | "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$HOME/android-sdk" --licenses >/dev/null 2>&1 || true
    yes | "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$HOME/android-sdk" "build-tools;36.0.0" "platform-tools" "ndk;27.0.12077973" "cmake;3.22.1" >/dev/null 2>&1 || \
    yes | "$HOME/android-sdk/cmdline-tools/latest/bin/sdkmanager" --sdk_root="$HOME/android-sdk" "build-tools;35.0.0" "platform-tools" >/dev/null 2>&1 || true
fi

# Persist environment variables for all shells
mkdir -p /etc/profile.d
cat << 'ENVEOF' > /etc/profile.d/codeforge.sh
export TERM=xterm-256color
export COLORTERM=truecolor
export ANDROID_HOME=$HOME/android-sdk
export ANDROID_SDK_ROOT=$HOME/android-sdk
export ANDROID_SDK_HOME=$HOME/android-sdk
export ANDROID_NDK_HOME=$ANDROID_HOME/ndk
export CMAKE_HOME=$ANDROID_HOME/cmake
export JAVA_HOME=/usr/lib/jvm/default-java
export GRADLE_HOME=/usr/share/gradle
export GRADLE_USER_HOME=$HOME/.gradle
export PATH=$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$ANDROID_HOME/build-tools/36.0.0:$ANDROID_HOME/build-tools:$ANDROID_NDK_HOME:$CMAKE_HOME:$GRADLE_HOME/bin:$PATH
ENVEOF

cat << 'ENVEOF' > /etc/environment
ANDROID_HOME=/root/android-sdk
ANDROID_SDK_ROOT=/root/android-sdk
ANDROID_SDK_HOME=/root/android-sdk
ANDROID_NDK_HOME=/root/android-sdk/ndk
CMAKE_HOME=/root/android-sdk/cmake
JAVA_HOME=/usr/lib/jvm/default-java
GRADLE_HOME=/usr/share/gradle
GRADLE_USER_HOME=/root/.gradle
PATH="/usr/lib/jvm/default-java/bin:/root/android-sdk/cmdline-tools/latest/bin:/root/android-sdk/platform-tools:/root/android-sdk/build-tools/36.0.0:/root/android-sdk/build-tools:/root/android-sdk/ndk:/root/android-sdk/cmake:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"
ENVEOF

# Locale generieren (Ubuntu-spezifisch)
if command -v locale-gen >/dev/null 2>&1; then
    locale-gen en_US.UTF-8 || true
fi

if [ "$setup_ready" -eq 1 ]; then
    touch "$UBUNTU_INIT_DONE"
    rm -f "$UBUNTU_INIT_FAILED"
    echo "[CodeForgeMobile] Ubuntu bootstrap completed."
else
    date +%s > "$UBUNTU_INIT_FAILED"
    echo "[CodeForgeMobile] Ubuntu bootstrap failed; retry on next launch."
fi

rm -f "$UBUNTU_INIT_LOCK"
rm -f "$0"
EOF

    chmod 700 "$UBUNTU_INIT_RUNNER" || true

    if command -v tee >/dev/null 2>&1; then
        /bin/sh "$UBUNTU_INIT_RUNNER" "$UBUNTU_INIT_DONE" "$UBUNTU_INIT_LOCK" "$UBUNTU_INIT_FAILED" 2>&1 | tee "$UBUNTU_INIT_LOG"
    else
        /bin/sh "$UBUNTU_INIT_RUNNER" "$UBUNTU_INIT_DONE" "$UBUNTU_INIT_LOCK" "$UBUNTU_INIT_FAILED" > "$UBUNTU_INIT_LOG" 2>&1
        cat "$UBUNTU_INIT_LOG"
    fi

    if [ -f "$UBUNTU_INIT_LOCK" ] && [ ! -f "$UBUNTU_INIT_DONE" ] && [ ! -f "$UBUNTU_INIT_FAILED" ]; then
        date +%s > "$UBUNTU_INIT_FAILED"
        rm -f "$UBUNTU_INIT_LOCK"
    fi
}

if [ ! -f "$UBUNTU_INIT_DONE" ]; then
    while [ ! -f "$UBUNTU_INIT_DONE" ]; do
        if [ -f "$UBUNTU_INIT_LOCK" ]; then
            init_pid=$(cat "$UBUNTU_INIT_LOCK" 2>/dev/null || true)
            if [ -n "$init_pid" ] && kill -0 "$init_pid" 2>/dev/null; then
                echo "[CodeForgeMobile] Ubuntu first-boot setup already running; waiting for completion..."
                while [ -f "$UBUNTU_INIT_LOCK" ] && [ ! -f "$UBUNTU_INIT_DONE" ]; do
                    owner_pid=$(cat "$UBUNTU_INIT_LOCK" 2>/dev/null || true)
                    if [ -n "$owner_pid" ] && kill -0 "$owner_pid" 2>/dev/null; then
                        sleep 1
                    else
                        rm -f "$UBUNTU_INIT_LOCK"
                        break
                    fi
                done
                continue
            fi
            rm -f "$UBUNTU_INIT_LOCK"
        fi

        if ( set -C; printf '%s\n' "$$" > "$UBUNTU_INIT_LOCK" ) 2>/dev/null; then
            if [ -f "$UBUNTU_INIT_FAILED" ]; then
                echo "[CodeForgeMobile] Previous setup attempt failed; retrying in foreground."
                rm -f "$UBUNTU_INIT_FAILED"
            fi

            echo "[CodeForgeMobile] Ubuntu first-boot setup is running in foreground."
            echo "[CodeForgeMobile] Waiting for setup completion before entering shell..."
            run_ubuntu_init_in_foreground
            break
        fi
    done

    if [ -f "$UBUNTU_INIT_DONE" ]; then
        echo "[CodeForgeMobileix] Ubuntu first-boot setup completed."
    elif [ -f "$UBUNTU_INIT_FAILED" ]; then
        echo "[CodeForgeMobile] Ubuntu first-boot setup failed."
        echo "[CodeForgeMobile] Setup log: $UBUNTU_INIT_LOG"
        echo "[CodeForgeMobile] Resolve errors and restart session to retry."
        exit 1
    else
        echo "[CodeForgeMobile] Ubuntu first-boot setup did not finish correctly."
        echo "[CodeForgeMobile] Setup log: $UBUNTU_INIT_LOG"
        echo "[CodeForgeMobile] Resolve errors and restart session to retry."
        exit 1
    fi
fi

printf '\033]0;%s\007' "$RUNTIME_HOSTNAME"

# Farben für Ubuntu (z. B. auf ein Orange/Gelblich statt dem Arch-Cyan gewechselt: 38;5;208m)
ubuntu_bash_ps1="\[\e]0;\u@$RUNTIME_HOSTNAME:\w\a\]\[\e[38;5;208m\]\u\[\033[39m\]@$RUNTIME_HOSTNAME \[\033[39m\]\w \[\033[0m\]\\$ "

reattach_tty_streams() {
    tty >/dev/null 2>&1 && return 0

    if [ -e /dev/tty ] && exec 0<>/dev/tty 1>&0 2>&0 && tty >/dev/null 2>&1; then
        return 0
    fi

    fd0_resolved=$(readlink -f /proc/self/fd/0 2>/dev/null || true)
    case "$fd0_resolved" in
        /dev/pts/*|/dev/tty*)
            if [ -e "$fd0_resolved" ] && exec 0<>"$fd0_resolved" 1>&0 2>&0 && tty >/dev/null 2>&1; then
                return 0
            fi
            ;;
    esac

    if [ -n "$CODEFORGEMOBILE_HOST_TTY" ] && [ -e "$CODEFORGEMOBILE_HOST_TTY" ] && exec 0<>"$CODEFORGEMOBILE_HOST_TTY" 1>&0 2>&0 && tty >/dev/null 2>&1; then
        return 0
    fi

    return 1
}

if [ ! -f /linkerconfig/ld.config.txt ]; then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

launch_interactive_shell() {
    set +e

    if [ -z "$CODEFORGEMOBILE_SHELL" ] || [ ! -x "$CODEFORGEMOBILE_SHELL" ]; then
        if [ -x /bin/bash ]; then
            CODEFORGEMOBILE_SHELL=/bin/bash
        else
            CODEFORGEMOBILE_SHELL=/bin/sh
        fi
    fi

    shell_name=$(basename "$CODEFORGEMOBILE_SHELL")

    if [ "$shell_name" = "bash" ]; then
        if [ -z "$TERM" ] || [ "$TERM" = "dumb" ]; then
            export TERM=xterm-256color
        fi
        export HOST="$RUNTIME_HOSTNAME"
        export HOSTNAME="$RUNTIME_HOSTNAME"
        export PS1="$ubuntu_bash_ps1"
    fi

    if [ -n "$CODEFORGEMOBILE_SHELL" ] && [ -x "$CODEFORGEMOBILE_SHELL" ]; then
        reattach_tty_streams || true
        case "$shell_name" in
            bash)
                exec "$CODEFORGEMOBILE_SHELL" -i
                ;;
            zsh|ksh)
                exec "$CODEFORGEMOBILE_SHELL" -il
                ;;
            sh|ash|dash)
                exec "$CODEFORGEMOBILE_SHELL" -i
                ;;
            *)
                exec "$CODEFORGEMOBILE_SHELL"
                ;;
        esac
        echo "[CodeForgeMobile] Failed to exec CODEFORGEMOBILE_SHELL=$CODEFORGEMOBILE_SHELL, falling back..."
    fi

    if [ -x /bin/bash ]; then
        exec /bin/bash -i
    else
        exec /bin/sh -i
    fi
}

if [ "$#" -eq 0 ]; then
    cd "$HOME" || cd /
    launch_interactive_shell
else
    exec "$@"
fi
