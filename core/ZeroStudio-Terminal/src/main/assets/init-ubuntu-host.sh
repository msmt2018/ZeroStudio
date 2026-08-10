#!/system/bin/sh
set -e

ROOTFS_ID=${TERMIX_ROOTFS_ID:-ubuntu-24.04-arm64}
ROOTFS_DIR=$PREFIX/files/LinuxSystem/$ROOTFS_ID
ROOTFS_ARCHIVE=$PREFIX/files/${TERMIX_ROOTFS_ARCHIVE:-$ROOTFS_ID.tar.gz}
PROOT_BIN=$PREFIX/local/bin/proot
LIB_DIR=$PREFIX/local/lib
LINKER=${LINKER:-/system/bin/linker}
[ -f /system/bin/linker64 ] && LINKER=/system/bin/linker64
export PROOT_TMP_DIR=${PROOT_TMP_DIR:-$PREFIX/tmp}

mkdir -p "$ROOTFS_DIR" "$PREFIX/local/bin" "$LIB_DIR" "$PREFIX/tmp"
[ ! -e "$PROOT_BIN" ] && cp "$PREFIX/files/proot" "$PROOT_BIN"
chmod +x "$PROOT_BIN" 2>/dev/null || true

for sofile in "$PREFIX/files/"*.so.2; do
  [ -e "$sofile" ] || continue
  dest="$LIB_DIR/$(basename "$sofile")"
  [ ! -e "$dest" ] && cp "$sofile" "$dest"
done

ROOTFS_READY_MARKER=$ROOTFS_DIR/.zerostudio-rootfs-ready

ensure_usrmerge_link() {
  link_path=$1
  target_path=$2

  if [ -L "$ROOTFS_DIR/$link_path" ]; then
    return 0
  fi

  if [ -e "$ROOTFS_DIR/$link_path" ]; then
    return 0
  fi

  if [ -d "$ROOTFS_DIR/$target_path" ]; then
    ln -s "$target_path" "$ROOTFS_DIR/$link_path" 2>/dev/null || true
  fi
}

repair_usrmerge_links() {
  # Ubuntu base images are usr-merged. Android tar implementations normally
  # restore these symlinks, but interrupted extraction or limited tar builds can
  # leave them missing. Recreate them idempotently before validation.
  ensure_usrmerge_link bin usr/bin
  ensure_usrmerge_link sbin usr/sbin
  ensure_usrmerge_link lib usr/lib
  ensure_usrmerge_link lib64 usr/lib64
}

probe_guest_bins() {
  GUEST_SH=/bin/sh
  [ -x "$ROOTFS_DIR$GUEST_SH" ] || GUEST_SH=/usr/bin/sh
  GUEST_BASH=/bin/bash
  [ -x "$ROOTFS_DIR$GUEST_BASH" ] || GUEST_BASH=/usr/bin/bash
  GUEST_ENV=/usr/bin/env
  [ -x "$ROOTFS_DIR$GUEST_ENV" ] || GUEST_ENV=/bin/env
}

rootfs_is_complete() {
  [ -d "$ROOTFS_DIR/etc" ] || return 1
  [ -d "$ROOTFS_DIR/usr/bin" ] || return 1

  repair_usrmerge_links
  probe_guest_bins

  [ -x "$ROOTFS_DIR$GUEST_SH" ] || return 1
  [ -x "$ROOTFS_DIR$GUEST_ENV" ] || return 1
  return 0
}

extract_ubuntu_rootfs() {
  if [ ! -f "$ROOTFS_ARCHIVE" ]; then
    echo "Ubuntu rootfs archive not found: $ROOTFS_ARCHIVE"
    exit 1
  fi

  rm -rf "$ROOTFS_DIR"
  mkdir -p "$ROOTFS_DIR"

  echo "Extracting Ubuntu rootfs into $ROOTFS_DIR"
  # Run tar under proot --link2symlink. Ubuntu rootfs archives contain many
  # hardlinks (for example coreutils applets); Android app-private storage can
  # reject hardlink creation with EACCES, so proot converts link(2) calls into
  # symlinks during extraction. -p preserves executable bits where tar supports it.
  EXTRACT_ARGS="--kill-on-exit -0 --link2symlink -r / -w /"
  EXTRACT_ARGS="$EXTRACT_ARGS -b /dev -b /proc -b /sys -b /data -b $PREFIX"
  "$LINKER" "$PROOT_BIN" $EXTRACT_ARGS /system/bin/sh -c "cd '$ROOTFS_DIR' && tar -xpf '$ROOTFS_ARCHIVE'"
  repair_usrmerge_links
}

if [ ! -f "$ROOTFS_READY_MARKER" ] || ! rootfs_is_complete; then
  extract_ubuntu_rootfs
  if ! rootfs_is_complete; then
    echo "Ubuntu rootfs extraction failed: missing required symlinks or executables (/usr/bin/env, /bin/sh)."
    exit 1
  fi
  : > "$ROOTFS_READY_MARKER"
else
  probe_guest_bins
fi

mkdir -p "$ROOTFS_DIR/tmp" "$ROOTFS_DIR/root" "$ROOTFS_DIR/proc" "$ROOTFS_DIR/sys" "$ROOTFS_DIR/dev" "$PREFIX/tmp" 2>/dev/null || true
chmod 1777 "$ROOTFS_DIR/tmp" 2>/dev/null || true

cat > "$ROOTFS_DIR/etc/resolv.conf" <<DNS
nameserver 1.1.1.1
nameserver 8.8.8.8
DNS
printf '%s\n' "ZeroStudio" > "$ROOTFS_DIR/etc/hostname" 2>/dev/null || true
cat > "$ROOTFS_DIR/etc/hosts" <<HOSTS
127.0.0.1 localhost.localdomain localhost ZeroStudio
::1 localhost.localdomain localhost ip6-localhost ip6-loopback
HOSTS

ARGS="--kill-on-exit"
ARGS="$ARGS -w /root"

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
ARGS="$ARGS -b /dev/urandom:/dev/random"
ARGS="$ARGS -b /proc"
ARGS="$ARGS -b /sys"
ARGS="$ARGS -b $PREFIX"
ARGS="$ARGS -b $ROOTFS_DIR/tmp:/dev/shm"

if [ -e "/proc/self/fd" ]; then ARGS="$ARGS -b /proc/self/fd:/dev/fd"; fi
if [ -e "/proc/self/fd/0" ]; then ARGS="$ARGS -b /proc/self/fd/0:/dev/stdin"; fi
if [ -e "/proc/self/fd/1" ]; then ARGS="$ARGS -b /proc/self/fd/1:/dev/stdout"; fi
if [ -e "/proc/self/fd/2" ]; then ARGS="$ARGS -b /proc/self/fd/2:/dev/stderr"; fi

ARGS="$ARGS -r $ROOTFS_DIR"
ARGS="$ARGS -0"
ARGS="$ARGS --link2symlink"
ARGS="$ARGS --sysvipc"
ARGS="$ARGS -L"

export LD_LIBRARY_PATH="$LIB_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export HOME=/root
export TERM=${TERM:-xterm-256color}
export LANG=C.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

if [ -x "$ROOTFS_DIR$GUEST_BASH" ]; then
  GUEST_LOGIN_SHELL=$GUEST_BASH
  GUEST_LOGIN_ARG=--login
else
  GUEST_LOGIN_SHELL=$GUEST_SH
  GUEST_LOGIN_ARG=-l
fi

if [ -x "$ROOTFS_DIR$GUEST_ENV" ]; then
  exec "$LINKER" "$PROOT_BIN" $ARGS "$GUEST_ENV" -i HOME=/root TERM="$TERM" LANG="$LANG" PATH="$PATH" "$GUEST_LOGIN_SHELL" "$GUEST_LOGIN_ARG"
fi

# Fallback for damaged/minimal rootfs images that really do not contain env.
# PATH and the rest of the baseline environment are exported by the guest shell.
exec "$LINKER" "$PROOT_BIN" $ARGS "$GUEST_SH" -lc 'export HOME=/root TERM="${TERM:-xterm-256color}" LANG="${LANG:-C.UTF-8}" PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; cd "$HOME" 2>/dev/null || cd /; if [ -x /bin/bash ]; then exec /bin/bash -l; elif [ -x /usr/bin/bash ]; then exec /usr/bin/bash -l; else exec /bin/sh -l; fi'
