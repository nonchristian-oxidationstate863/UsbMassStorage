#!/system/bin/sh
# UsbMassStorage post-fs-data stage

MODDIR="${0%/*}"
TAG="usbmassstorage"
DATA_DIR="/data/adb/Usbmanagement"
COUNT_FILE="$DATA_DIR/count.sh"

[ -f "$MODDIR/disable" ] && exit 0

for bb_path in /data/adb/ksu/bin/busybox /data/adb/ap/bin/busybox /data/adb/magisk/busybox; do
    [ -x "$bb_path" ] && export PATH="${bb_path%/*}:$PATH"
done

echo "${TAG}: post-fs-data started" > /dev/kmsg

. "$MODDIR/common.sh"

# Apply runtime SELinux rules early (before any configfs or data access)
if [ -f "$MODDIR/ksu_rules.txt" ]; then
    ksud sepolicy apply "$MODDIR/ksu_rules.txt" 2>/dev/null
    echo "${TAG}: sepolicy rules applied" > /dev/kmsg
fi

# Create storage directories
mkdir -p "$DATA_DIR/gadget_backup"
mkdir -p "$DATA_DIR/images"
chown system:system "$DATA_DIR" 2>/dev/null
chown system:system "$DATA_DIR/gadget_backup" 2>/dev/null
chown system:system "$DATA_DIR/images" 2>/dev/null
chmod 771 "$DATA_DIR/images" 2>/dev/null

# Migrate from old storage locations
if [ -d /data/misc/usbmassstorage/images ] && [ "$(ls -A /data/misc/usbmassstorage/images 2>/dev/null)" ]; then
    cp -a /data/misc/usbmassstorage/images/* "$DATA_DIR/images/" 2>/dev/null && \
        rm -rf /data/misc/usbmassstorage 2>/dev/null
    echo "${TAG}: migrated images to $DATA_DIR" > /dev/kmsg
fi
if [ -d /data/misc/gadget_backup ] && [ -f /data/misc/gadget_backup/dirty ]; then
    cp -a /data/misc/gadget_backup/* "$DATA_DIR/gadget_backup/" 2>/dev/null && \
        rm -rf /data/misc/gadget_backup 2>/dev/null
    echo "${TAG}: migrated gadget_backup to $DATA_DIR" > /dev/kmsg
fi
if [ -f /data/adb/usbmassstorage/automount.conf ]; then
    cp -a /data/adb/usbmassstorage/automount.conf "$DATA_DIR/" 2>/dev/null && \
        rm -f /data/adb/usbmassstorage/automount.conf 2>/dev/null
    echo "${TAG}: migrated automount.conf to $DATA_DIR" > /dev/kmsg
fi

# Bootloop guard: disable module after 3 consecutive failed boots
COUNT=0
if [ -f "$COUNT_FILE" ]; then
    . "$COUNT_FILE"
fi

if [ "$COUNT" -ge 3 ]; then
    echo "${TAG}: boot count >= 3, disabling module" > /dev/kmsg
    restore_gadget_state
    touch "$MODDIR/disable"
    exit 1
fi

COUNT=$((COUNT + 1))
echo "COUNT=$COUNT" > "$COUNT_FILE"
echo "${TAG}: boot count incremented to $COUNT" > /dev/kmsg

restore_gadget_state

echo "${TAG}: post-fs-data complete" > /dev/kmsg
