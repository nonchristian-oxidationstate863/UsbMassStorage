#!/system/bin/sh
# ABI detection - sourced by lifecycle scripts

if [ -n "$ARCH" ]; then
    case "$ARCH" in
        arm64) ABI=arm64-v8a ;;
        arm)   ABI=armeabi-v7a ;;
        x86_64) ABI=x86_64 ;;
        x86)   ABI=x86 ;;
        *)     ABI="" ;;
    esac
else
    case "$(uname -m)" in
        aarch64)       ABI=arm64-v8a ;;
        armv7*|armv8l) ABI=armeabi-v7a ;;
        x86_64)        ABI=x86_64 ;;
        i686|i386)     ABI=x86 ;;
        *)             ABI="" ;;
    esac
fi

[ -n "$MODDIR" ] && [ -n "$ABI" ] && BIN="$MODDIR/bin/${ABI}/daemon"

# Gadget state restore for bootloop protection.
# Reads backup files written by the daemon and writes them back to configfs.
# Safe order: disable UDC -> clear configs -> write attrs -> recreate configs -> enable UDC.
# All writes are best-effort (2>/dev/null) — partial restore is better than no restore.
restore_gadget_state() {
    GADGET="/config/usb_gadget/g1"
    BACKUP="${DATA_DIR:-/data/adb/Usbmanagement}/gadget_backup"
    [ -d "$BACKUP" ] || return 0
    [ -f "$BACKUP/dirty" ] || return 0

    echo "${TAG:-usbmassstorage}: restoring gadget state from backup" > /dev/kmsg

    # Step 1: Disable UDC
    echo "" > "$GADGET/UDC" 2>/dev/null

    # Step 2: Remove all config symlinks
    for link in "$GADGET/configs/b.1"/*; do
        [ -L "$link" ] && rm -f "$link" 2>/dev/null
    done

    # Step 3: Write saved gadget attributes
    for attr in idVendor idProduct bcdUSB bDeviceClass bDeviceSubClass bDeviceProtocol bcdDevice; do
        [ -f "$BACKUP/$attr" ] && cat "$BACKUP/$attr" > "$GADGET/$attr" 2>/dev/null
    done

    # Step 4: Write saved string descriptors (use saved language dir name)
    SLANG=""
    [ -f "$BACKUP/strings_lang" ] && SLANG=$(cat "$BACKUP/strings_lang" 2>/dev/null)
    [ -z "$SLANG" ] && SLANG=$(ls "$GADGET/strings/" 2>/dev/null | head -1)
    if [ -n "$SLANG" ]; then
        for attr in manufacturer product serialnumber; do
            [ -f "$BACKUP/$attr" ] && cat "$BACKUP/$attr" > "$GADGET/strings/$SLANG/$attr" 2>/dev/null
        done
    fi

    # Step 5: Recreate saved config symlinks
    if [ -f "$BACKUP/configs_list" ]; then
        while IFS='|' read -r cfg func; do
            [ -n "$cfg" ] && [ -n "$func" ] && \
                ln -s "$GADGET/functions/$func" "$GADGET/configs/b.1/$cfg" 2>/dev/null
        done < "$BACKUP/configs_list"
    fi

    # Step 6: Re-enable UDC
    [ -f "$BACKUP/udc" ] && cat "$BACKUP/udc" > "$GADGET/UDC" 2>/dev/null

    # Clear dirty flag only if UDC was successfully restored
    if [ -f "$BACKUP/udc" ]; then
        SAVED_UDC=$(cat "$BACKUP/udc" 2>/dev/null)
        CURRENT_UDC=$(cat "$GADGET/UDC" 2>/dev/null)
        if [ "$SAVED_UDC" = "$CURRENT_UDC" ]; then
            rm -f "$BACKUP/dirty" 2>/dev/null
            echo "${TAG:-usbmassstorage}: dirty flag cleared (UDC verified)" > /dev/kmsg
        else
            echo "${TAG:-usbmassstorage}: WARNING: UDC mismatch after restore (saved=$SAVED_UDC current=$CURRENT_UDC), keeping dirty flag" > /dev/kmsg
        fi
    else
        rm -f "$BACKUP/dirty" 2>/dev/null
    fi

    echo "${TAG:-usbmassstorage}: gadget state restored" > /dev/kmsg
}
