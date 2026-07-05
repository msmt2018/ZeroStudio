#!/bin/sh
set -e

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
export HOME=/root

resolve_runtime_hostname() {
    if [ -n "$TERMIX_GUEST_HOSTNAME" ]; then
        printf '%s' "$TERMIX_GUEST_HOSTNAME"
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

    printf '%s' "arch"
}

install_hostname_wrapper() {
    wrapper_dir=/tmp/termix-runtime/bin
    mkdir -p "$wrapper_dir"
    cat > "$wrapper_dir/hostname" <<'EOF'
#!/bin/sh
resolve_name() {
    if [ -n "$TERMIX_GUEST_HOSTNAME" ]; then
        printf '%s\n' "$TERMIX_GUEST_HOSTNAME"
        return 0
    fi

    if [ -r /etc/hostname ]; then
        IFS= read -r file_name < /etc/hostname || true
        if [ -n "$file_name" ]; then
            printf '%s\n' "$file_name"
            return 0
        fi
    fi

    printf '%s\n' "arch"
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
export TERMIX_GUEST_HOSTNAME="$RUNTIME_HOSTNAME"
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

if [ -f /etc/pacman.conf ]; then
    sed -i 's/^#DisableSandbox/DisableSandbox/' /etc/pacman.conf || true
fi

ARCH_INIT_DONE=/etc/termix-arch-init.done
ARCH_INIT_LOCK=/etc/termix-arch-init.lock
ARCH_INIT_FAILED=/etc/termix-arch-init.failed
ARCH_INIT_LOG=/tmp/termix-arch-init.log

run_arch_init_in_foreground() {
    ARCH_INIT_RUNNER=/tmp/termix-arch-init.runner.sh

    cat > "$ARCH_INIT_RUNNER" <<'EOF'
#!/bin/sh
set +e

ARCH_INIT_DONE="$1"
ARCH_INIT_LOCK="$2"
ARCH_INIT_FAILED="$3"

keyring_ready=1

echo "[Termix] Initializing Arch Linux keyring (first boot)..."
pacman-key --init || keyring_ready=0

case "$(uname -m)" in
    aarch64|armv7l|armv6l)
        pacman-key --populate archlinuxarm || keyring_ready=0
        ;;
    *)
        pacman-key --populate archlinux || keyring_ready=0
        ;;
esac

if [ -f /etc/locale.gen ]; then
    sed -i -E "s/^#\s*(en_US.UTF-8\s+UTF-8)/\1/" /etc/locale.gen || true
    locale-gen || true
fi

if [ "$keyring_ready" -eq 1 ]; then
    touch "$ARCH_INIT_DONE"
    rm -f "$ARCH_INIT_FAILED"
    echo "[Termix] Arch keyring bootstrap completed."
else
    date +%s > "$ARCH_INIT_FAILED"
    echo "[Termix] Arch keyring bootstrap failed; retry on next launch."
fi

rm -f "$ARCH_INIT_LOCK"
rm -f "$0"
EOF

    chmod 700 "$ARCH_INIT_RUNNER" || true

    if command -v tee >/dev/null 2>&1; then
        /bin/sh "$ARCH_INIT_RUNNER" "$ARCH_INIT_DONE" "$ARCH_INIT_LOCK" "$ARCH_INIT_FAILED" 2>&1 | tee "$ARCH_INIT_LOG"
    else
        /bin/sh "$ARCH_INIT_RUNNER" "$ARCH_INIT_DONE" "$ARCH_INIT_LOCK" "$ARCH_INIT_FAILED" > "$ARCH_INIT_LOG" 2>&1
        cat "$ARCH_INIT_LOG"
    fi

    if [ -f "$ARCH_INIT_LOCK" ] && [ ! -f "$ARCH_INIT_DONE" ] && [ ! -f "$ARCH_INIT_FAILED" ]; then
        date +%s > "$ARCH_INIT_FAILED"
        rm -f "$ARCH_INIT_LOCK"
    fi
}

if [ ! -f "$ARCH_INIT_DONE" ]; then
    while [ ! -f "$ARCH_INIT_DONE" ]; do
        if [ -f "$ARCH_INIT_LOCK" ]; then
            init_pid=$(cat "$ARCH_INIT_LOCK" 2>/dev/null || true)
            if [ -n "$init_pid" ] && kill -0 "$init_pid" 2>/dev/null; then
                echo "[Termix] Arch first-boot setup already running; waiting for completion..."
                while [ -f "$ARCH_INIT_LOCK" ] && [ ! -f "$ARCH_INIT_DONE" ]; do
                    owner_pid=$(cat "$ARCH_INIT_LOCK" 2>/dev/null || true)
                    if [ -n "$owner_pid" ] && kill -0 "$owner_pid" 2>/dev/null; then
                        sleep 1
                    else
                        rm -f "$ARCH_INIT_LOCK"
                        break
                    fi
                done
                continue
            fi
            rm -f "$ARCH_INIT_LOCK"
        fi

        if ( set -C; printf '%s\n' "$$" > "$ARCH_INIT_LOCK" ) 2>/dev/null; then
            if [ -f "$ARCH_INIT_FAILED" ]; then
                echo "[Termix] Previous setup attempt failed; retrying in foreground."
                rm -f "$ARCH_INIT_FAILED"
            fi

            echo "[Termix] Arch first-boot setup is running in foreground."
            echo "[Termix] Waiting for setup completion before entering shell..."
            run_arch_init_in_foreground
            break
        fi
    done

    if [ -f "$ARCH_INIT_DONE" ]; then
        echo "[Termix] Arch first-boot setup completed."
    elif [ -f "$ARCH_INIT_FAILED" ]; then
        echo "[Termix] Arch first-boot setup failed."
        echo "[Termix] Setup log: $ARCH_INIT_LOG"
        echo "[Termix] Resolve errors and restart session to retry."
        exit 1
    else
        echo "[Termix] Arch first-boot setup did not finish correctly."
        echo "[Termix] Setup log: $ARCH_INIT_LOG"
        echo "[Termix] Resolve errors and restart session to retry."
        exit 1
    fi
fi

printf '\033]0;%s\007' "$RUNTIME_HOSTNAME"

arch_bash_ps1="\[\e]0;\u@$RUNTIME_HOSTNAME:\w\a\]\[\e[38;5;81m\]\u\[\033[39m\]@$RUNTIME_HOSTNAME \[\033[39m\]\w \[\033[0m\]\\$ "

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

    if [ -n "$TERMIX_HOST_TTY" ] && [ -e "$TERMIX_HOST_TTY" ] && exec 0<>"$TERMIX_HOST_TTY" 1>&0 2>&0 && tty >/dev/null 2>&1; then
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

    if [ -z "$TERMIX_SHELL" ]; then
        TERMIX_SHELL=/bin/sh
    fi

    shell_name=$(basename "$TERMIX_SHELL")

    if [ "$shell_name" = "bash" ]; then
        if [ -z "$TERM" ] || [ "$TERM" = "dumb" ]; then
            export TERM=xterm-256color
        fi
        export HOST="$RUNTIME_HOSTNAME"
        export HOSTNAME="$RUNTIME_HOSTNAME"
        export PS1="$arch_bash_ps1"
    fi

    if [ -n "$TERMIX_SHELL" ] && [ -x "$TERMIX_SHELL" ]; then
        reattach_tty_streams || true
        case "$shell_name" in
            bash)
                exec "$TERMIX_SHELL" -i
                ;;
            zsh|ksh)
                exec "$TERMIX_SHELL" -il
                ;;
            sh|ash|dash)
                exec "$TERMIX_SHELL" -i
                ;;
            *)
                exec "$TERMIX_SHELL"
                ;;
        esac
        echo "[Termix] Failed to exec TERMIX_SHELL=$TERMIX_SHELL, falling back..."
    fi

    exec /bin/sh -i
}

if [ "$#" -eq 0 ]; then
    cd "$HOME" || cd /
    launch_interactive_shell
else
    exec "$@"
fi
