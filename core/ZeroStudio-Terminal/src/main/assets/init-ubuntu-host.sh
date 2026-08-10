#!/system/bin/sh
set -e

ROOTFS_ID=${TERMIX_ROOTFS_ID:-ubuntu-24.04-arm64}
ROOTFS_DIR=$PREFIX/files/LinuxSystem/$ROOTFS_ID
ROOTFS_ARCHIVE=$PREFIX/files/${TERMIX_ROOTFS_ARCHIVE:-$ROOTFS_ID.tar.gz}
PROOT_BIN=$PREFIX/local/bin/proot
LIB_DIR=$PREFIX/local/lib
LINKER=${LINKER:-/system/bin/linker}
[ -f /system/bin/linker64 ] && LINKER=/system/bin/linker64

mkdir -p "$ROOTFS_DIR" "$PREFIX/local/bin" "$LIB_DIR"
[ ! -e "$PROOT_BIN" ] && cp "$PREFIX/files/proot" "$PROOT_BIN"
chmod +x "$PROOT_BIN" 2>/dev/null || true

for sofile in "$PREFIX/files/"*.so.2; do
  [ -e "$sofile" ] || continue
  dest="$LIB_DIR/$(basename "$sofile")"
  [ ! -e "$dest" ] && cp "$sofile" "$dest"
done

if [ ! -d "$ROOTFS_DIR/etc" ]; then
  if [ ! -f "$ROOTFS_ARCHIVE" ]; then
    echo "Ubuntu rootfs archive not found: $ROOTFS_ARCHIVE"
    exit 1
  fi
  echo "Extracting Ubuntu rootfs into $ROOTFS_DIR"
  tar -xf "$ROOTFS_ARCHIVE" -C "$ROOTFS_DIR"
fi

# Ubuntu base images are usr-merged. Some Android tar/proot combinations can leave
# top-level compatibility links absent after extraction, causing paths such as
# /bin/sh or /usr/bin/env to fail when proot starts the guest process.
[ -e "$ROOTFS_DIR/bin" ] || [ ! -d "$ROOTFS_DIR/usr/bin" ] || ln -s usr/bin "$ROOTFS_DIR/bin"
[ -e "$ROOTFS_DIR/sbin" ] || [ ! -d "$ROOTFS_DIR/usr/sbin" ] || ln -s usr/sbin "$ROOTFS_DIR/sbin"
[ -e "$ROOTFS_DIR/lib" ] || [ ! -d "$ROOTFS_DIR/usr/lib" ] || ln -s usr/lib "$ROOTFS_DIR/lib"
[ -e "$ROOTFS_DIR/lib64" ] || [ ! -d "$ROOTFS_DIR/usr/lib64" ] || ln -s usr/lib64 "$ROOTFS_DIR/lib64"

if [ ! -x "$ROOTFS_DIR/bin/sh" ] && [ ! -x "$ROOTFS_DIR/usr/bin/sh" ]; then
  echo "Ubuntu rootfs is invalid: missing /bin/sh"
  exit 1
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

export PROOT_TMP_DIR=${PROOT_TMP_DIR:-$PREFIX/tmp}
export LD_LIBRARY_PATH="$LIB_DIR${LD_LIBRARY_PATH:+:$LD_LIBRARY_PATH}"
export HOME=/root
export TERM=${TERM:-xterm-256color}
export LANG=C.UTF-8
export PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin

# Start the shell directly instead of via /usr/bin/env so proot does not abort
# before the guest PATH is initialized if /usr/bin/env is absent or inaccessible.
exec "$LINKER" "$PROOT_BIN" $ARGS /bin/sh -lc 'export HOME=/root TERM="${TERM:-xterm-256color}" LANG="${LANG:-C.UTF-8}" PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; cd "$HOME" 2>/dev/null || cd /; if [ -x /bin/bash ]; then exec /bin/bash -l; else exec /bin/sh -l; fi'
