# meta-avb

Yocto layer for Android Verified Boot (AVB) and dm-verity on embedded Linux.

## Overview

Traditional `dm-verity` implementations require the root hash to be known at build time
and embedded into the initramfs or kernel cmdline before the rootfs image is finalized.
This introduces circular dependencies between the initramfs and rootfs build tasks and
can require workarounds like unconditional rebuilds to avoid stale root hashes.

`meta-avb` takes a different approach: the root hash is stamped into an AVB footer on the
rootfs image after signing and `avb_verify` extracts it from the partition at boot.
The initramfs and rootfs are independently buildable with no cross-dependency.

## Architecture

For a detailed description of the AVB tooling and verification flow:
see [avb-utils](https://github.com/embetrix/avb-utils).


### Design

- **AVB footer as single source of truth** `avbtool` stamps the hashtree, root hash
  and signature directly into the filesystem image footer. No metadata is scattered
  across separate build artifacts.
- **Runtime hash extraction** The initramfs calls `avb_verify` at boot to read the
  root hash from the AVB footer on disk, verified against a public key. The initramfs
  and rootfs are independently buildable with no circular dependency.
- **Inline signature on kernel cmdline**  For the initramfs-free path, a kernel patch
  adds `root_hash_sig_hex` to dm-verity, allowing the PKCS#7 signature to be passed
  as a hex-encoded DER blob via `dm-mod.create`. The `cmdline.verity` file is generated
  strictly after signing  no build cycle.

### Boot Paths

The layer supports two verification paths:

| Path | Mechanism | Pros |
|------|-----------|------|
| **Initramfs** | `avb_verify --dm-table` at boot sets up dm-verity device | More flexibility |
| **Kernel cmdline** | `dm-mod.create` with `root_hash_sig_hex` | No initramfs needed |

### Security Considerations

dm-verity only protects the rootfs integrity. A complete chain of trust requires
**Secure Boot** to authenticate the bootloader, kernel and initramfs before they
execute. Without it an attacker can replace the kernel or initramfs and bypass
dm-verity entirely.

For the **initramfs path**, the initramfs must be authenticated. This can be achieved by:
- Bundling the initramfs into the kernel image (`INITRAMFS_IMAGE_BUNDLE = "1"`), so
  Secure Boot verification of the kernel implicitly covers the initramfs
- Using a signed FIT image (`KERNEL_IMAGETYPE = "fitImage"`) where the bootloader
  verifies the kernel, DTB, and initramfs signatures before booting

For the **kernel cmdline path**, the bootloader must be trusted to pass the correct
`dm-mod.create` parameters. UEFI Secure Boot or U-Boot verified boot ensures the
bootloader configuration (e.g., GRUB config) cannot be tampered with.

### Using a vendor kernel

The `linux-yocto_%.bbappend` applies dm-verity kernel config fragments and patches
automatically. When using a vendor kernel instead, the following kernel configs must
be enabled manually:

Required for dm-verity (both boot paths):

```
CONFIG_MD=y
CONFIG_BLK_DEV_DM=y
CONFIG_DM_INIT=y
CONFIG_DM_VERITY=y
```

required for root hash signature verification (`AVB_ROOT_HASH_SIGN = "1"`):

```
CONFIG_DM_VERITY_VERIFY_ROOTHASH_SIG=y
CONFIG_DM_VERITY_REQUIRE_ROOTHASH_SIG=y
CONFIG_ASYMMETRIC_KEY_TYPE=y
CONFIG_ASYMMETRIC_PUBLIC_KEY_SUBTYPE=y
CONFIG_X509_CERTIFICATE_PARSER=y
CONFIG_PKCS7_MESSAGE_PARSER=y
CONFIG_SYSTEM_TRUSTED_KEYS="trusted_keys.pem"
```

The kernel cmdline path also requires the two patches from
`recipes-kernel/linux/linux-yocto/v6.6/` to add the `root_hash_sig_hex` dm-verity
parameter and raise the charp parameter length limit. These patches must be
applied to the vendor kernel tree manually or via a bbappend.

The AVB X.509 certificate must be embedded in the kernel trusted keyring using
`kernel-trusted-keys.bbclass` or by adding it to `CONFIG_SYSTEM_TRUSTED_KEYS`
directly.

### Components

| Component | Description |
|-----------|-------------|
| `avb-verity.bbclass` | IMAGE_TYPES conversion handler, signs rootfs and emits `cmdline.verity` |
| `avb-verity-keys.bbclass` | Auto-generates ephemeral dev signing keys on first build |
| `rootfs-avb-verity` WIC plugin | Builds and signs the rootfs partition during image creation |
| `avb-verity-image-initramfs` | Minimal initramfs with avb_verify, devmapper, and udev |
| `avb-utils` | CMake-based toolkit wrapping Android AVB for embedded Linux |
| `libavb` | Shared library for AVB verification primitives |
| `fec` | AOSP forward error correction tool avbtool uses to write dm-verity parity |
| `kernel-trusted-keys.bbclass` | Bundles X.509 certificates into the kernel trusted keyring |

## Dependencies

This layer depends on:

- [openembedded-core (core)](https://git.openembedded.org/openembedded-core/log/?h=wrynose)
- [meta-oe (openembedded-layer)](https://github.com/openembedded/meta-openembedded/tree/wrynose)

Compatible with Yocto Project **wrynose** release.

## Configuration

Add the following to your `local.conf` or KAS configuration:

```
IMAGE_CLASSES += "avb-verity"
IMAGE_FEATURES += "read-only-rootfs"
INHERIT += "avb-verity-keys"
AVB_ROOT_HASH_SIGN = "1"
```

| Variable | Default | Description |
|----------|---------|-------------|
| `AVB_KEYS_DIR` | `${TMPDIR}/avb-keys` | Directory for signing keys; ephemeral keys are generated here if missing |
| `AVB_SIGN_KEY` | `${AVB_KEYS_DIR}/privkey_avb.pem` | Path to private key (PEM) for AVB signing |
| `AVB_X509` | `${AVB_KEYS_DIR}/x509_avb.pem` | Path to X.509 certificate for root hash signature |
| `AVB_ALGORITHM` | `SHA256_RSA4096` | AVB signing algorithm |
| `AVB_HASH_ALGORITHM` | `sha256` | Hash algorithm for dm-verity |
| `AVB_PARTITION_NAME` | `rootfs` | Partition name embedded in AVB footer |
| `AVB_PARTITION_SIZE` | `0` (auto) | Partition size in bytes, 0 = fit to image |
| `AVB_DATA_DEV` | `/dev/mmcblk0p2` | Block device for dm-verity data and hash (kernel cmdline path only) |
| `AVB_ROOT_HASH_SIGN` | `0` | Enable PKCS#7 root hash signing and kernel signature verification |
| `AVB_FEC_ROOTS` | `2` | Reed-Solomon roots for dm-verity forward error correction, 2 to 24 |

When `AVB_ROOT_HASH_SIGN` is set to `1`, the root hash is signed with the AVB key and
the X.509 certificate is embedded in the kernel trusted keyring. The kernel patches for
`root_hash_sig_hex` are also applied. Set to `0` to disable root has signature verification.

Every signed image carries forward error correction data after its hash tree, and
the dm-verity table names it, so the kernel repairs a bounded number of corrupt
blocks instead of failing the read. `AVB_FEC_ROOTS` sets how many: each 255-byte
codeword corrects up to `roots / 2` bad bytes, costing `roots / (255 - roots)` of
the covered size, so `2` repairs one corrupt 4 KiB block per interleave group for
0.8 % and `24` repairs twelve for 10.4 %. Parity is read only after verification
has already failed, so the choice affects space, not read speed. Needs
`CONFIG_DM_VERITY_FEC`, which `dm-verity.cfg` enables.

## Tested Machines

| Machine | Boot method | Notes |
|---------|-------------|-------|
| `qemux86-64` | EFI (GRUB) | Default machine, QEMU emulation |
| `beaglebone-yocto` | U-Boot | Initramfs bundled into kernel (`INITRAMFS_IMAGE_BUNDLE = "1"`) |

## Build

### qemux86-64

```
kas build kas-avb.yml
```

### Beaglebone

```
KAS_MACHINE=beaglebone-yocto kas build kas-avb.yml
```

## Emulation with QEMU

Boot with initramfs (WIC image, GRUB loads kernel + initramfs):

```
KAS_MACHINE=qemux86-64 kas shell kas-avb.yml \
    -c 'runqemu wic ovmf kvm serialstdio nographic snapshot qemuparams="-m 1024"'
```

Boot with kernel cmdline (no initramfs, dm-mod.create with optional inline signature):

```
KAS_MACHINE=qemux86-64 kas shell kas-avb.yml \
    -c 'runqemu kvm serialstdio nographic snapshot qemuparams="-m 1024" \
        bootparams="$(cat tmp/deploy/images/qemux86-64/cmdline.verity)"'
```

## Flashing Beaglebone

Write the WIC image to an SD card:

```
bmaptool copy build/tmp/deploy/images/beaglebone-yocto/core-image-minimal-beaglebone-yocto.wic /dev/sdX
```
