ROOTFS_ID=${TERMIX_ROOTFS_ID:-ubuntu-24.04}
ROOTFS_DIR=$PREFIX/files/LinuxSystem/$ROOTFS_ID
ROOTFS_ARCHIVE=$PREFIX/files/${TERMIX_ROOTFS_ARCHIVE:-ubuntu-24.04.tar.gz}
PROOT_BIN=$PREFIX/local/bin/proot
LINKER=/system/bin/linker
[ -f /system/bin/linker64 ] && LINKER=/system/bin/linker64
mkdir -p "$ROOTFS_DIR" "$PREFIX/local/bin"
[ ! -e "$PROOT_BIN" ] && cp "$PREFIX/files/proot" "$PROOT_BIN"
chmod 700 "$PROOT_BIN" 2>/dev/null || true
if [ ! -d "$ROOTFS_DIR/etc" ]; then
  if [ ! -f "$ROOTFS_ARCHIVE" ]; then
    echo "Ubuntu rootfs archive not found: $ROOTFS_ARCHIVE"
    exit 1
  fi
  echo "Extracting Ubuntu rootfs into $ROOTFS_DIR"
  tar -xf "$ROOTFS_ARCHIVE" -C "$ROOTFS_DIR"
fi
mkdir -p "$ROOTFS_DIR/tmp" "$ROOTFS_DIR/root" "$ROOTFS_DIR/proc" "$ROOTFS_DIR/sys" "$ROOTFS_DIR/dev" 2>/dev/null || true
chmod 1777 "$ROOTFS_DIR/tmp" 2>/dev/null || true
cat > "$ROOTFS_DIR/etc/resolv.conf" <<DNS
nameserver 1.1.1.1
nameserver 8.8.8.8
DNS
ARGS="-0 -w /root -b /dev -b /proc -b /sys -b /sdcard -b $ROOTFS_DIR/tmp:/dev/shm -r $ROOTFS_DIR"
export PROOT_TMP_DIR="$PREFIX/tmp"
mkdir -p "$PROOT_TMP_DIR"
exec "$LINKER" "$PROOT_BIN" $ARGS /usr/bin/env -i HOME=/root TERM="${TERM:-xterm-256color}" PATH=/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin LANG=C.UTF-8 /bin/sh -lc "if [ -x /bin/bash ]; then exec /bin/bash -l; else exec /bin/sh -l; fi"
