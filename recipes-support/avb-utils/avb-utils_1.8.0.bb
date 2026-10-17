SUMMARY = "A toolkit that brings Android Verified Boot (AVB) to embedded Linux"
HOMEPAGE = "https://github.com/embetrix/avb-utils"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=570a9b3749dd0463a1778803b12a6dce"

SRC_URI = " \
    git://github.com/cwcagilent/avb-utils.git;name=avb-utils;protocol=https;branch=master \
    "
# 1.8.0 (d1fa2933) plus the FEC series, from the fork carrying it until
# upstream takes the series. Not yet tagged.
SRCREV = "3b962b3b8406cc384d596ba992d3f10c1bf9fd82"

DEPENDS += "libavb python3-avbtool-native"
# avbtool shells out to fec for the dm-verity parity avb_sign now always writes.
# This reaches only avb-utils-native's own sysroot; image recipes get it through
# avb-verity.bbclass, because OE stages direct DEPENDS only.
DEPENDS:append:class-native = " fec-native"

inherit cmake pkgconfig

PACKAGES =+ "${PN}-python"

EXTRA_OECMAKE:class-native = "-DINSTALL_AVB_SIGN=ON"

RDEPENDS:${PN}:class-target = "avb-keys"
RDEPENDS:${PN}-python = "python3-core python3-avbtool openssl"

FILES:${PN}-python = "${bindir}/avb_sign"

BBCLASSEXTEND = "native nativesdk"
