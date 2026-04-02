#!/system/bin/sh
TAG="usbmassstorage"
PKG="com.enginex0.usbmassstorage"
DATA_DIR="/data/adb/Usbmanagement"
LOCK_FILE="/dev/usbms_svc_lock"

log() { echo "${TAG}: $1" > /dev/kmsg; }

log "uninstall started"

for pid in $(pidof daemon 2>/dev/null); do
    if grep -q "msdd\|usbmassstorage" /proc/$pid/cmdline 2>/dev/null; then
        kill "$pid" 2>/dev/null
        log "killed daemon pid $pid"
    fi
done

if pm list packages 2>/dev/null | grep -q "$PKG"; then
    pm uninstall --user 0 "$PKG" >/dev/null 2>&1
    pm uninstall "$PKG" >/dev/null 2>&1
    log "uninstalled $PKG"
fi

# Restore gadget state before cleanup
MODDIR="${0%/*}"
. "$MODDIR/common.sh" 2>/dev/null
restore_gadget_state

rm -rf "$DATA_DIR"
rm -rf /data/misc/usbmassstorage /data/misc/gadget_backup /data/adb/usbmassstorage 2>/dev/null
rm -f "$LOCK_FILE"

log "uninstall complete"
