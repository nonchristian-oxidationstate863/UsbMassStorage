#!/system/bin/sh
MODDIR="${0%/*}"
TAG="usbmassstorage"
DATA_DIR="/data/adb/Usbmanagement"
LOCK_FILE="/dev/usbms_svc_lock"

[ -f "$MODDIR/disable" ] && exit 0

set -C
if ! : > "$LOCK_FILE" 2>/dev/null; then
    echo "${TAG}: another instance already running, exiting" > /dev/kmsg
    exit 0
fi
set +C
trap 'rm -f "$LOCK_FILE"' EXIT
trap 'exit 0' INT TERM

for bb_path in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do
    [ -x "$bb_path" ] && export PATH="${bb_path%/*}:$PATH"
done

echo "${TAG}: service started" > /dev/kmsg

. "$MODDIR/common.sh"

if [ -z "$ABI" ]; then
    echo "${TAG}: ERROR - could not detect ABI" > /dev/kmsg
    exit 1
fi

if [ ! -f "$BIN" ]; then
    echo "${TAG}: ERROR - binary not found: $BIN" > /dev/kmsg
    exit 1
fi

# Ensure daemon (system user) can traverse /data/adb/ to reach our directory.
# /data/adb/ is mode 700 root:root by default; o+x adds traverse without read/write.
# Done here (not post-fs-data) because KSU may reset /data/adb perms between stages.
chmod o+x /data/adb 2>/dev/null

BACKOFF=1
while true; do
    echo "${TAG}: launching daemon (ABI=$ABI)" > /dev/kmsg
    /system/bin/runcon u:r:msd_daemon:s0 "$BIN" daemon \
        --log-target logcat --log-level debug \
        --automount-config "$DATA_DIR/automount.conf" &
    DAEMON_PID=$!

    # If daemon survives 5s, it bound the socket and is healthy
    sleep 5
    if kill -0 "$DAEMON_PID" 2>/dev/null; then
        echo "COUNT=0" > "$DATA_DIR/count.sh" 2>/dev/null
        echo "${TAG}: daemon alive (pid=$DAEMON_PID), boot counter reset" > /dev/kmsg
        wait "$DAEMON_PID"
        rc=$?
    else
        wait "$DAEMON_PID"
        rc=$?
    fi

    [ $rc -eq 0 ] && break
    echo "${TAG}: daemon exited ($rc), respawning in ${BACKOFF}s" > /dev/kmsg
    sleep "$BACKOFF"
    BACKOFF=$((BACKOFF * 2))
    [ "$BACKOFF" -gt 30 ] && BACKOFF=30
done
