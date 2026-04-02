// SPDX-FileCopyrightText: 2025
// SPDX-License-Identifier: GPL-3.0-only

//! Gadget state backup and restore for bootloop protection.
//!
//! Before modifying configfs, the daemon saves the current gadget state to
//! `/data/adb/Usbmanagement/gadget_backup/` (one plain-text file per attribute).
//! A dirty flag marks that changes are in flight. On startup, if the dirty flag
//! exists, the daemon restores the saved state before doing anything else.
//!
//! The shell scripts in `common.sh` can also read this backup to restore state
//! during early boot (post-fs-data) or uninstall, without needing the daemon.

use std::{
    fs,
    path::PathBuf,
};

use anyhow::{Context, Result};
use tracing::{debug, info, warn};

use crate::usb::UsbGadget;

const BACKUP_DIR: &str = "/data/adb/Usbmanagement/gadget_backup";
const DIRTY_FLAG: &str = "dirty";
const CONFIGS_FILE: &str = "configs_list";
const UDC_FILE: &str = "udc";
const STRINGS_LANG_FILE: &str = "strings_lang";

/// Gadget-level attributes to back up and restore.
const GADGET_ATTRS: &[&str] = &[
    "idVendor",
    "idProduct",
    "bcdUSB",
    "bDeviceClass",
    "bDeviceSubClass",
    "bDeviceProtocol",
    "bcdDevice",
];

/// String descriptors to back up and restore.
const STRING_ATTRS: &[&str] = &["manufacturer", "product", "serialnumber"];

fn backup_path() -> PathBuf {
    PathBuf::from(BACKUP_DIR)
}

fn dirty_path() -> PathBuf {
    backup_path().join(DIRTY_FLAG)
}

/// Check whether a previous operation was interrupted.
pub fn is_dirty() -> bool {
    dirty_path().exists()
}

/// Create backup directory if it doesn't already exist.
///
/// Note: post-fs-data.sh creates and chowns this directory to system:system
/// during early boot (as init context, which has unrestricted SELinux access).
/// We only do mkdir here as a fallback — no chown, since that triggers an
/// SELinux setattr denial on system_data_file from the msd_daemon context.
pub fn ensure_backup_dir() -> Result<()> {
    let dir = backup_path();
    fs::create_dir_all(&dir)
        .with_context(|| format!("Failed to create backup dir: {dir:?}"))?;
    Ok(())
}

/// Remove the dirty flag after successful restore.
pub fn clear_dirty_flag() -> Result<()> {
    let path = dirty_path();
    if path.exists() {
        fs::remove_file(&path).with_context(|| format!("Failed to remove dirty flag: {path:?}"))?;
    }
    Ok(())
}

/// Save current gadget state to backup directory.
///
/// Writes one file per attribute, then creates the dirty flag last.
/// If dirty flag already exists (previous crash), this is a no-op —
/// caller should check `is_dirty()` first.
pub fn save_gadget_state(gadget: &UsbGadget) -> Result<()> {
    let dir = backup_path();
    // Try creating dir in case ensure_backup_dir failed at startup.
    let _ = fs::create_dir_all(&dir);

    // Save gadget-level attributes
    for attr in GADGET_ATTRS {
        match gadget.read_attribute(attr) {
            Ok(val) => {
                let path = dir.join(attr);
                fs::write(&path, &val)
                    .with_context(|| format!("Failed to write backup: {path:?}"))?;
                debug!("Saved {attr}={val}");
            }
            Err(e) => warn!("Could not read {attr}, skipping: {e}"),
        }
    }

    // Discover and save the actual strings language directory name
    let strings_lang = match gadget.strings_lang() {
        Ok(lang) => {
            let path = dir.join(STRINGS_LANG_FILE);
            fs::write(&path, &lang)
                .with_context(|| format!("Failed to write backup: {path:?}"))?;
            debug!("Saved strings_lang={lang}");
            Some(lang)
        }
        Err(e) => {
            warn!("Could not discover strings language: {e}");
            None
        }
    };

    // Save string descriptors
    if let Some(ref lang) = strings_lang {
        for attr in STRING_ATTRS {
            match gadget.read_string(lang, attr) {
                Ok(val) => {
                    let path = dir.join(attr);
                    fs::write(&path, &val)
                        .with_context(|| format!("Failed to write backup: {path:?}"))?;
                    debug!("Saved string {attr}={val}");
                }
                Err(e) => warn!("Could not read string {attr}, skipping: {e}"),
            }
        }
    }

    // Save UDC controller name
    match gadget.read_udc() {
        Ok(val) => {
            let path = dir.join(UDC_FILE);
            fs::write(&path, &val)
                .with_context(|| format!("Failed to write backup: {path:?}"))?;
            debug!("Saved UDC={val}");
        }
        Err(e) => warn!("Could not read UDC, skipping: {e}"),
    }

    // Save active config entries: "config_name|function_name" per line
    match gadget.configs() {
        Ok(configs) => {
            let lines: Vec<String> = configs
                .iter()
                .filter_map(|(config, function)| {
                    let c = config.to_str()?;
                    let f = function.to_str()?;
                    Some(format!("{c}|{f}"))
                })
                .collect();
            let path = dir.join(CONFIGS_FILE);
            fs::write(&path, lines.join("\n"))
                .with_context(|| format!("Failed to write backup: {path:?}"))?;
            debug!("Saved {} config entries", lines.len());
        }
        Err(e) => warn!("Could not read configs, skipping: {e}"),
    }

    // Create dirty flag LAST — signals backup is complete, changes imminent
    fs::write(dirty_path(), "")
        .with_context(|| "Failed to create dirty flag")?;
    info!("Gadget state saved, dirty flag set");

    Ok(())
}

/// Restore gadget state from backup.
///
/// Safe order: disable UDC -> clear configs -> write attributes -> recreate
/// configs -> enable UDC. All steps are best-effort (log and continue).
pub fn restore_gadget_state(gadget: &UsbGadget) -> Result<()> {
    let dir = backup_path();
    if !dir.exists() {
        warn!("No backup directory found, nothing to restore");
        return Ok(());
    }

    info!("Restoring gadget state from backup");

    // Step 1: Disable UDC (ignore errors — may already be disabled)
    if let Err(e) = gadget.set_controller(None) {
        warn!("Could not disable UDC during restore: {e}");
    }

    // Step 2: Clear all current config symlinks
    match gadget.configs() {
        Ok(configs) => {
            for (name, _) in &configs {
                if let Err(e) = gadget.delete_config(name) {
                    warn!("Could not delete config {name:?} during restore: {e}");
                }
            }
        }
        Err(e) => warn!("Could not read current configs during restore: {e}"),
    }

    // Step 3: Write saved gadget attributes
    for attr in GADGET_ATTRS {
        let path = dir.join(attr);
        if let Ok(val) = fs::read_to_string(&path) {
            if let Err(e) = gadget.write_attribute(attr, &val) {
                warn!("Could not restore {attr}={val}: {e}");
            } else {
                debug!("Restored {attr}={val}");
            }
        }
    }

    // Step 4: Write saved string descriptors (using saved language dir name)
    let saved_lang = fs::read_to_string(dir.join(STRINGS_LANG_FILE)).ok();
    if let Some(ref lang) = saved_lang {
        for attr in STRING_ATTRS {
            let path = dir.join(attr);
            if let Ok(val) = fs::read_to_string(&path) {
                if let Err(e) = gadget.write_string(lang, attr, &val) {
                    warn!("Could not restore string {attr}={val}: {e}");
                } else {
                    debug!("Restored string {attr}={val}");
                }
            }
        }
    } else {
        warn!("No saved strings language — skipping string descriptor restore");
    }

    // Step 5: Recreate saved config symlinks
    let configs_path = dir.join(CONFIGS_FILE);
    if let Ok(content) = fs::read_to_string(&configs_path) {
        for line in content.lines() {
            if let Some((config_name, function_name)) = line.split_once('|') {
                if config_name.is_empty() || function_name.is_empty() {
                    continue;
                }
                match gadget.create_config(config_name.as_ref(), function_name.as_ref()) {
                    Ok(_) => debug!("Restored config {config_name} -> {function_name}"),
                    Err(e) => warn!(
                        "Could not restore config {config_name} -> {function_name}: {e}"
                    ),
                }
            }
        }
    }

    // Step 6: Re-enable UDC with saved controller
    let udc_path = dir.join(UDC_FILE);
    if let Ok(controller) = fs::read_to_string(&udc_path) {
        if !controller.is_empty() {
            if let Err(e) = gadget.set_controller(Some(&controller)) {
                warn!("Could not restore UDC={controller}: {e}");
            } else {
                info!("Restored UDC={controller}");
            }
        }
    }

    // Clear dirty flag only if UDC was successfully restored
    let udc_ok = if let Ok(saved_udc) = fs::read_to_string(dir.join(UDC_FILE)) {
        match gadget.read_udc() {
            Ok(current) => current == saved_udc,
            Err(_) => false,
        }
    } else {
        true // No saved UDC — consider it OK
    };

    if udc_ok {
        clear_dirty_flag()?;
        info!("Gadget state restored successfully");
    } else {
        warn!("UDC mismatch after restore — keeping dirty flag for next attempt");
    }

    Ok(())
}
