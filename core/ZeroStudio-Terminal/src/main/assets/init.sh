set -e  # Exit immediately on Failure

export PATH=/bin:/sbin:/usr/bin:/usr/sbin:/usr/share/bin:/usr/share/sbin:/usr/local/bin:/usr/local/sbin:/system/bin:/system/xbin
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

    printf '%s' "alpine"
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

    printf '%s\n' "alpine"
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

if [ ! -s /etc/resolv.conf ]; then
    echo "nameserver 8.8.8.8" > /etc/resolv.conf
fi

# Set terminal title using OSC escape sequence
# OSC 0 sets both icon name and window title
printf '\033]0;%s\007' "$RUNTIME_HOSTNAME"

# PS1 with terminal title update: \[\e]0;title\a\] sets title on each prompt
export PS1="\[\e]0;\u@$RUNTIME_HOSTNAME:\w\a\]\[\e[38;5;46m\]\u\[\033[39m\]@$RUNTIME_HOSTNAME \[\033[39m\]\w \[\033[0m\]\\$ "
# shellcheck disable=SC2034
export PIP_BREAK_SYSTEM_PACKAGES=1
required_packages="bash gcompat glib nano"
missing_packages=""
for pkg in $required_packages; do
    if ! apk info -e $pkg >/dev/null 2>&1; then
        missing_packages="$missing_packages $pkg"
    fi
done
if [ -n "$missing_packages" ]; then
    echo -e "\e[34;1m[*] \e[0mInstalling Important packages\e[0m"
    apk update && apk upgrade
    apk add $missing_packages
    if [ $? -eq 0 ]; then
        echo -e "\e[32;1m[+] \e[0mSuccessfully Installed\e[0m"
    fi
    echo -e "\e[34m[*] \e[0mUse \e[32mapk\e[0m to install new packages\e[0m"
fi

#fix linker warning
if [[ ! -f /linkerconfig/ld.config.txt ]];then
    mkdir -p /linkerconfig
    touch /linkerconfig/ld.config.txt
fi

if [ "$#" -eq 0 ]; then
    source /etc/profile
    install_hostname_wrapper
    export HOST="$RUNTIME_HOSTNAME"
    export HOSTNAME="$RUNTIME_HOSTNAME"
    export PS1="\[\e]0;\u@$RUNTIME_HOSTNAME:\w\a\]\[\e[38;5;46m\]\u\[\033[39m\]@$RUNTIME_HOSTNAME \[\033[39m\]\w \[\033[0m\]\\$ "
    cd $HOME
    # Use shell from TERMIX_SHELL env var, fallback to bash > ash
    if [ -n "$TERMIX_SHELL" ] && [ -x "$TERMIX_SHELL" ]; then
        exec "$TERMIX_SHELL"
    elif [ -x /bin/bash ]; then
        exec /bin/bash
    else
        /bin/ash
    fi
else
    exec "$@"
fi
