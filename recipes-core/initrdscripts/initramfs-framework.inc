FILESEXTRAPATHS:prepend := "${THISDIR}/initramfs-framework-avb:"

SRC_URI:append = "\
    file://avbverity \
"

do_install:append() {
    # avb-verity
    install -m 0755 ${S}/avbverity ${D}/init.d/80-avbverity
}

PACKAGES:append = " initramfs-module-avb-verity"

SUMMARY:initramfs-module-avb-verity = "initramfs avbverity rootfs support"
RDEPENDS:initramfs-module-avb-verity = "${PN}-base"
FILES:initramfs-module-avb-verity = "/init.d/80-avbverity"
