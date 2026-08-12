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
# proot and its loader are built from source (termux/proot module) and packaged
# inside the APK. They land in applicationInfo.nativeLibraryDir at install time,
# which the host exposes as $NATIVE_LIB_DIR. Copy them into $PREFIX/local/bin
# so the init scripts can invoke them by a stable path. talloc is statically
# linked into libproot.so, so libtalloc.so.2 is no longer downloaded.
if [ -n "$NATIVE_LIB_DIR" ] && [ -e "$NATIVE_LIB_DIR/libproot.so" ]; then
  cp "$NATIVE_LIB_DIR/libproot.so" "$PROOT_BIN"
  chmod +x "$PROOT_BIN" 2>/dev/null || true
elif [ ! -e "$PROOT_BIN" ]; then
  cp "$PREFIX/files/proot" "$PROOT_BIN" 2>/dev/null || true
fi
chmod +x "$PROOT_BIN" 2>/dev/null || true

# Unbundled loader mode (PROOT_UNBUNDLE_LOADER): proot execve()s libloader.so
# directly instead of extracting an embedded loader to a temp file. Pointing
# PROOT_LOADER at the nativeLibraryDir copy avoids W^X issues on Android 10+
# and eliminates the "prooted-NNNN-XXXXXX" temp file leak into AT_EXECFN.
if [ -n "$NATIVE_LIB_DIR" ] && [ -e "$NATIVE_LIB_DIR/libloader.so" ]; then
  export PROOT_LOADER="$NATIVE_LIB_DIR/libloader.so"
  [ -e "$NATIVE_LIB_DIR/libloader32.so" ] && export PROOT_LOADER_32="$NATIVE_LIB_DIR/libloader32.so"
fi

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

normalize_l2s_hardlinks() {
  # proot --link2symlink converts hardlinks into .l2s.* symlinks. New Ubuntu
  # coreutils multicall binaries reject execution when argv[0] resolves to a
  # .l2s.* backing name, so convert those symlinks into regular file copies.
  find "$ROOTFS_DIR" -type l 2>/dev/null | while IFS= read -r link_path; do
    link_target=$(readlink "$link_path" 2>/dev/null || true)
    case "/$link_target" in
      */.l2s.*) ;;
      *) continue ;;
    esac

    link_dir=${link_path%/*}
    case "$link_target" in
      /*) target_path="$ROOTFS_DIR$link_target" ;;
      *) target_path="$link_dir/$link_target" ;;
    esac

    if [ -f "$target_path" ]; then
      tmp_path="$link_path.copy.$$"
      cp "$target_path" "$tmp_path" && chmod 755 "$tmp_path" 2>/dev/null || true
      if [ -f "$tmp_path" ]; then
        rm -f "$link_path"
        mv "$tmp_path" "$link_path"
      fi
    fi
  done
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
  normalize_l2s_hardlinks
  probe_guest_bins

  [ -x "$ROOTFS_DIR$GUEST_SH" ] || return 1
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
  normalize_l2s_hardlinks
  repair_usrmerge_links
}

if [ ! -f "$ROOTFS_READY_MARKER" ] || ! rootfs_is_complete; then
  extract_ubuntu_rootfs
  if ! rootfs_is_complete; then
    echo "Ubuntu rootfs extraction failed: missing required symlinks or shell executable (/bin/sh)."
    exit 1
  fi
  : > "$ROOTFS_READY_MARKER"
else
  # Re-run on every startup: proot --link2symlink may have left .l2s.*
  # symlinks from a prior extraction, and usrmerge symlinks can be lost
  # after Android storage cleanup. This ensures coreutils applets resolve
  # correctly for both GNU (Ubuntu 18-24) and uutils (Ubuntu 25-26).
  repair_usrmerge_links
  normalize_l2s_hardlinks
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

# Start through the guest shell instead of /usr/bin/env. Ubuntu 25.10+ may ship
# coreutils as hardlinked multicall applets; after link2symlink extraction, env can
# reject the .l2s.* backing name even though the rootfs is otherwise usable.
exec "$LINKER" "$PROOT_BIN" $ARGS "$GUEST_SH" -lc 'export HOME=/root TERM="${TERM:-xterm-256color}" LANG="${LANG:-C.UTF-8}" PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin; cd "$HOME" 2>/dev/null || cd /; if [ -x /bin/bash ]; then exec /bin/bash -l; elif [ -x /usr/bin/bash ]; then exec /usr/bin/bash -l; else exec /bin/sh -l; fi'