#!/system/bin/sh
set -e

ARCH_DIR=$PREFIX/local/arch
ARCH_ROOTFS=$ARCH_DIR
ARCH_READY_MARKER=$PREFIX/local/.termix-arch-installed
PROOT_BIN=$PREFIX/local/bin/proot
LIB_DIR=$PREFIX/local/lib
INIT_BIN=$PREFIX/local/bin/init-arch

resolve_guest_hostname() {
    if [ -r "$ARCH_ROOTFS/etc/hostname" ]; then
        IFS= read -r guest_name < "$ARCH_ROOTFS/etc/hostname" || true
        guest_name=${guest_name%%[[:space:]]*}
        if [ -n "$guest_name" ]; then
            printf '%s' "$guest_name"
            return 0
        fi
    fi

    printf '%s' "arch"
}

mkdir -p "$ARCH_DIR"
mkdir -p "$PREFIX/local/bin"
mkdir -p "$LIB_DIR"

[ ! -e "$PROOT_BIN" ] && cp "$PREFIX/files/proot" "$PROOT_BIN"
chmod +x "$PROOT_BIN"
chmod +x "$INIT_BIN" 2>/dev/null || true

for sofile in "$PREFIX/files/"*.so.2; do
    dest="$LIB_DIR/$(basename "$sofile")"
    [ ! -e "$dest" ] && cp "$sofile" "$dest"
done

if [ ! -f "$ARCH_READY_MARKER" ] || { [ ! -d "$ARCH_DIR/etc" ] && [ ! -d "$ARCH_DIR/root/etc" ]; }; then
    echo "Arch rootfs is not installed. Open Termix downloader to install Arch Linux."
    exit 1
fi

if [ ! -d "$ARCH_DIR/etc" ] && [ -d "$ARCH_DIR/root/etc" ]; then
    ARCH_ROOTFS=$ARCH_DIR/root
fi

if [ ! -d "$ARCH_ROOTFS/etc" ]; then
    echo "Arch rootfs extraction failed: missing '$ARCH_ROOTFS/etc'"
    exit 1
fi

GUEST_HOSTNAME=$(resolve_guest_hostname)

ARGS="--kill-on-exit"
ARGS="$ARGS -w /"

for system_mnt in /apex /odm /product /system /system_ext /vendor \
 /linkerconfig/ld.config.txt \
 /linkerconfig/com.android.art/ld.config.txt \
 /plat_property_contexts /property_contexts; do

 if [ -e "$system_mnt" ]; then
  system_mnt=$(realpath "$system_mnt")
  ARGS="$ARGS -b ${system_mnt}"
 fi
done
unset system_mnt

ARGS="$ARGS -b /sdcard"
ARGS="$ARGS -b /storage"
ARGS="$ARGS -b /dev"
ARGS="$ARGS -b /data"
ARGS="$ARGS -b /sys"
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"

if [ -e "/proc/self/fd" ]; then
 ARGS="$ARGS -b /proc/self/fd:/dev/fd"
fi

if [ -e "/proc/self/fd/0" ]; then
 ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"
fi

if [ -e "/proc/self/fd/1" ]; then
 ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"
fi

if [ -e "/proc/self/fd/2" ]; then
 ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"
fi

if [ ! -d "$ARCH_ROOTFS/tmp" ]; then
 mkdir -p "$ARCH_ROOTFS/tmp"
 chmod 1777 "$ARCH_ROOTFS/tmp"
fi
ARGS="$ARGS -b $ARCH_ROOTFS/tmp:/dev/shm"

ARGS="$ARGS -r $ARCH_ROOTFS"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

export TERMIX_GUEST_HOSTNAME="$GUEST_HOSTNAME"
export TERMIX_PROOT_ARGS="$ARGS"

exec su -p -c "mkdir -p $PROOT_TMP_DIR && export LD_LIBRARY_PATH=$LIB_DIR && export PROOT_TMP_DIR=$PROOT_TMP_DIR && export TERM=${TERM:-xterm-256color} && export LANG=C.UTF-8 && export HOME=/root && export TERMIX_GUEST_HOSTNAME='$GUEST_HOSTNAME' && export TERMIX_PROOT_ARGS='$ARGS' && if command -v unshare >/dev/null 2>&1; then exec unshare -u /system/bin/sh -c 'if command -v hostname >/dev/null 2>&1; then hostname \"$TERMIX_GUEST_HOSTNAME\" >/dev/null 2>&1 || true; fi; exec \"$PROOT_BIN\" $TERMIX_PROOT_ARGS sh \"$INIT_BIN\"'; else exec \"$PROOT_BIN\" $TERMIX_PROOT_ARGS sh \"$INIT_BIN\"; fi"
