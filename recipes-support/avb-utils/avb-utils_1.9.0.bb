SUMMARY = "A toolkit that brings Android Verified Boot (AVB) to embedded Linux"
HOMEPAGE = "https://github.com/embetrix/avb-utils"
LICENSE = "GPL-2.0-only"
LIC_FILES_CHKSUM = "file://LICENSE;md5=570a9b3749dd0463a1778803b12a6dce"

# None of the submodules: libavb comes from the libavb recipe, avbtool from
# python3-avbtool and fec from the fec recipe. Without the avb submodule cmake
# also installs no second avbtool, which would collide with python3-avbtool's.
SRC_URI = " \
    git://github.com/embetrix/avb-utils.git;name=avb-utils;protocol=https;branch=master \
    "
# Tag 1.9.0
SRCREV = "5216bd6d16f54ca7c88805e5f55d2c38a7c866ff"

DEPENDS += "libavb python3-avbtool-native"

inherit cmake pkgconfig

PACKAGES =+ "${PN}-python"

# INSTALL_AVB_SIGN defaults BUILD_FEC on, which wants the fec/ submodules; the
# fec recipe builds that tool instead.
EXTRA_OECMAKE:class-native = "-DINSTALL_AVB_SIGN=ON -DBUILD_FEC=OFF"

RDEPENDS:${PN}:class-target = "avb-keys"
RDEPENDS:${PN}-python = "python3-core python3-avbtool openssl"
# avbtool shells out to fec for the dm-verity parity avb_sign writes by default.
RDEPENDS:${PN}-python:append:class-native = " fec-native"

FILES:${PN}-python = "${bindir}/avb_sign"

BBCLASSEXTEND = "native nativesdk"
