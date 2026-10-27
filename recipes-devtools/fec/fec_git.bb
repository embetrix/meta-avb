SUMMARY = "AOSP forward error correction tool for dm-verity"
DESCRIPTION = "fec writes and applies the Reed-Solomon parity that lets dm-verity \
repair a corrupted block instead of failing the read. avbtool shells out to it \
whenever it adds a hashtree with FEC, which avb_sign does unless told --no-fec."
HOMEPAGE = "https://android.googlesource.com/platform/system/extras/+/refs/heads/main/verity/fec/"
SECTION = "devel"

# Apache-2.0 for the AOSP code, LGPL for Phil Karn's Reed-Solomon library
# under it, GPL for the squashfs superblock header the squashfs reader includes.
LICENSE = "Apache-2.0 & LGPL-2.1-or-later & GPL-2.0-or-later"
LIC_FILES_CHKSUM = " \
    file://extras/libfec/NOTICE;md5=b433a746dd6fe8862028b1d7fc412a4d \
    file://libbase/NOTICE;md5=e832036c59491b20e1665b084eaee6cf \
    file://core/libcutils/NOTICE;md5=9645f39e9db895a4aa6e02cb57294595 \
    file://logging/liblog/NOTICE;md5=2754aa537525e0fed3d297e8379b6d81 \
    file://fec_rs/NOTICE;md5=0448d3676bc0de00406af227d341a4d1 \
    file://squashfs/COPYING;md5=b234ee4d69f5fce4486a80fdaf4a4263 \
"

# AOSP builds fec with Soong out of these repositories; the tool has no release
# of its own, so each is pinned at the revision the build was proven with. The
# CMakeLists.txt and the GCC logging shim are ours: Soong is not an option here.
SRC_URI = " \
    git://android.googlesource.com/platform/system/extras;protocol=https;nobranch=1;name=extras;destsuffix=${BP}/extras \
    git://android.googlesource.com/platform/external/fec;protocol=https;nobranch=1;name=fecrs;destsuffix=${BP}/fec_rs \
    git://android.googlesource.com/platform/system/libbase;protocol=https;nobranch=1;name=libbase;destsuffix=${BP}/libbase \
    git://android.googlesource.com/platform/system/core;protocol=https;nobranch=1;name=core;destsuffix=${BP}/core \
    git://android.googlesource.com/platform/system/logging;protocol=https;nobranch=1;name=logging;destsuffix=${BP}/logging \
    git://android.googlesource.com/platform/external/squashfs-tools;protocol=https;nobranch=1;name=squashfs;destsuffix=${BP}/squashfs \
    file://CMakeLists.txt;subdir=${BP} \
    file://logging_shim.cpp;subdir=${BP} \
"
SRCREV_extras = "fc2494a2abd7ab21774d03deb09c1362bbb0bba8"
SRCREV_fecrs = "90857deb7973c0ca24c79b9c1809fc9667f32c4f"
SRCREV_libbase = "6d19b5c690fa4220c10e42c2326150bbd4d4b7bb"
SRCREV_core = "a3b721a32242006b59cb12bd62c9133632af3a2d"
SRCREV_logging = "bcac7c30d88a3773a7c0bc9f5617a23a886331fd"
SRCREV_squashfs = "33469ab3363ab75399f05818a70f00a5abae7a78"
SRCREV_FORMAT = "extras_fecrs_libbase_core_logging_squashfs"

PV = "1.0+git"

# libavb is the one the verifier links too, so the parity writer and avb_verify
# agree on the descriptor format.
DEPENDS = "libavb openssl zlib"

inherit cmake pkgconfig

# Prove fec works rather than only that it linked: encode parity for a random
# image, corrupt a copy, and require the repaired result back byte-for-byte.
# Only where the binary can run.
addtask check after do_compile before do_install
do_check() {
    :
}
do_check:class-native() {
    rm -rf ${B}/check
    mkdir ${B}/check
    cd ${B}/check
    dd if=/dev/urandom of=check.img bs=4096 count=64 status=none
    ${B}/fec --encode --roots 2 check.img check.fec
    cp check.img check_bad.img
    dd if=/dev/zero of=check_bad.img bs=1 seek=8192 count=256 conv=notrunc status=none
    ${B}/fec --decode check_bad.img check.fec check_fixed.img
    cmp check.img check_fixed.img || bbfatal "fec could not repair a corrupted image"
}

BBCLASSEXTEND = "native nativesdk"
