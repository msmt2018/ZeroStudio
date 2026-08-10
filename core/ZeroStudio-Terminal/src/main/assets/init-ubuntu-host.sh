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

# Ubuntu base images are usr-merged. Try to recreate the compatibility links,
# but do not depend on them: Android devices can differ in how symlink creation
# behaves in app-private storage. The launcher below probes both /bin/* and
# /usr/bin/* paths before entering proot.
[ -e "$ROOTFS_DIR/bin" ] || [ -L "$ROOTFS_DIR/bin" ] || [ ! -d "$ROOTFS_DIR/usr/bin" ] || ln -s usr/bin "$ROOTFS_DIR/bin" 2>/dev/null || true
[ -e "$ROOTFS_DIR/sbin" ] || [ -L "$ROOTFS_DIR/sbin" ] || [ ! -d "$ROOTFS_DIR/usr/sbin" ] || ln -s usr/sbin "$ROOTFS_DIR/sbin" 2>/dev/null || true
[ -e "$ROOTFS_DIR/lib" ] || [ -L "$ROOTFS_DIR/lib" ] || [ ! -d "$ROOTFS_DIR/usr/lib" ] || ln -s usr/lib "$ROOTFS_DIR/lib" 2>/dev/null || true
[ -e "$ROOTFS_DIR/lib64" ] || [ -L "$ROOTFS_DIR/lib64" ] || [ ! -d "$ROOTFS_DIR/usr/lib64" ] || ln -s usr/lib64 "$ROOTFS_DIR/lib64" 2>/dev/null || true

GUEST_SH=/bin/sh
[ -x "$ROOTFS_DIR$GUEST_SH" ] || GUEST_SH=/usr/bin/sh
GUEST_BASH=/bin/bash
[ -x "$ROOTFS_DIR$GUEST_BASH" ] || GUEST_BASH=/usr/bin/bash
GUEST_ENV=/usr/bin/env
[ -x "$ROOTFS_DIR$GUEST_ENV" ] || GUEST_ENV=/bin/env

if [ ! -x "$ROOTFS_DIR$GUEST_SH" ]; then
  echo "Ubuntu rootfs is invalid: missing /bin/sh and /usr/bin/sh"
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
