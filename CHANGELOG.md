## v5.0

### Breaking Changes
- Storage relocated from `/data/misc/usbmassstorage/` to `/data/adb/Usbmanagement/` (auto-migrated on first boot)
- memfd RAM copy architecture removed — images mount directly from disk with zero memory overhead

### New Features
- Device reboot button in the main toolbar with confirmation dialog
- Path-based mount protocol for file:// URIs — daemon opens images directly via `/proc/pid/fd/N`
- Images of any size now mount instantly regardless of available RAM
- Import external disk images into managed storage from any file picker
- Batch stat/blkid queries for image metadata (single shell call instead of 2N)

### Performance
- R8 full mode enabled — release APK shrunk ~70% via aggressive tree-shaking and class merging
- Log.d/Log.v calls stripped from release builds via ProGuard rules
- Baseline profile added for Compose runtime AOT compilation on first install
- Compose stability configuration for deterministic recomposition skipping
- All infinite animations migrated to `graphicsLayer` — draw-phase only, zero recomposition overhead
- Shell.cmd() calls removed from main thread: composition body, onClick handlers, and store operations
- File I/O in DeviceCard moved to background coroutine with LaunchedEffect
- SharedPreferences reads wrapped in `remember` to prevent redundant disk access during recomposition
- enrichWithSize and scanImages batch all shell calls into single scripts
- Regex instances hoisted to top-level compiled vals across 5 files
- Keyed LazyColumn items for stable item identity during list mutations
- `@Stable` annotation on UiState for correct recomposition skipping
- `configChanges` declared on MainActivity to prevent unnecessary Activity recreation

### Bug Fixes
- Fixed mixed mount protocol where sequential daemon calls cleared previously mounted devices
- Fixed enrichWithSize running `java.io.File().length()` as unprivileged app context — now uses root shell
- Fixed automount config corruption from unchained shell writes (newline vs `&&` join)
- Fixed restartDaemon not killing parent service.sh loop alongside daemon process
- DAC traversal fix: `/data/adb/` mode 700 blocked daemon — added `chmod o+x` in service.sh
- Tightened `system_data_file` SELinux permissions — removed stale write/create from old storage path
- Added `adb_data_file` rules to runtime SELinux patcher for storage migration
- Added `untrusted_app fd use` and `shell_data_file read` to KSU rules

### Module
- Full storage migration with backward-compatible path detection on first boot
- Old `/data/misc/usbmassstorage/` directory removed after successful migration
- Gadget backup, automount config, and boot counter relocated to `/data/adb/Usbmanagement/`
- Expanded SELinux policy for kernel→adb_data_file read access
- Uninstall script updated for new data directory cleanup

## v4.0

- One-shot create, format, and mount pipeline for streamlined disk setup
- Image manager with batch select-to-delete and clear unmounted
- Auto-mount saved images at boot without opening the app
- Visual refresh: rotating backgrounds, animated cards, modernized sheets
- Auto-increment disk names with creation timestamps
- Module banner and action script for root manager integration
- Batch image operations and improved image lifecycle management
- Bypass FUSE for configfs lun writes on Android 11+ (direct sysfs path)
- Merged SELinux policy into sepolicy.rule for Magisk compatibility
- Hardened bootloop guard with retry loops and resource leak fixes
- Socket read timeout and connection reliability for daemon client
- Fixed device removal index mismatch and coroutine lifecycle
- Escaped single quotes in automount config paths
- CI pipeline with auto-build, signing, and release publishing

## v3.1

- Full app and installer localization with 32 languages
- Auto-detect device locale during module installation
- Accent color picker (system default, almost black, white)
- In-app usage guide accessible from menu
- Material 3 companion app with pull-to-refresh and bottom sheets
- SELinux enforcing with dedicated msd_daemon domain
- Boot guard with exponential backoff daemon respawn
- Multi-ABI support (arm64, armv7, x86_64)
- Create virtual disk images from the app
- Mount up to 8 USB devices simultaneously
