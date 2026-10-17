SUMMARY = "AOSP fec tool, for generating dm-verity forward error correction data"
DESCRIPTION = "avbtool shells out to a binary called 'fec' to build the FEC \
data that dm-verity uses to repair corrupted blocks. Nothing else packages it, \
so it is assembled here from the AOSP sources that make it up. Build-time only: \
the target verifies FEC in the kernel and needs nothing installed."
HOMEPAGE = "https://android.googlesource.com/platform/system/extras/+/refs/heads/main/libfec/"
SECTION = "devel"

# external/fec is Phil Karn's Reed-Solomon library, which is LGPL. Native-only
# because nothing on target needs it yet, not because the licence forbids it --
# shipping it is fine alongside the GPL already in the image. Making it a target
# recipe means dropping `inherit native` for BBCLASSEXTEND.
LICENSE = "Apache-2.0 & LGPL-2.1-or-later"
LIC_FILES_CHKSUM = "\
    file://extras/libfec/NOTICE;md5=b433a746dd6fe8862028b1d7fc412a4d \
    file://fec_rs/NOTICE;md5=0448d3676bc0de00406af227d341a4d1 \
    file://avb/LICENSE;md5=b8228f2369d92593f53f0a0685ebd3c0 \
"

SRC_URI = "\
    git://android.googlesource.com/platform/system/extras;protocol=https;branch=main;name=extras;destsuffix=${BP}/extras \
    git://android.googlesource.com/platform/external/fec;protocol=https;branch=main;name=fecrs;destsuffix=${BP}/fec_rs \
    git://android.googlesource.com/platform/system/libbase;protocol=https;branch=main;name=libbase;destsuffix=${BP}/libbase \
    git://android.googlesource.com/platform/system/core;protocol=https;branch=main;name=core;destsuffix=${BP}/core \
    git://android.googlesource.com/platform/system/logging;protocol=https;branch=main;name=logging;destsuffix=${BP}/logging \
    git://android.googlesource.com/platform/external/squashfs-tools;protocol=https;branch=main;name=squashfs;destsuffix=${BP}/squashfs \
    git://android.googlesource.com/platform/external/avb;protocol=https;nobranch=1;name=avb;destsuffix=${BP}/avb \
    file://logging-shim.cpp \
"

SRCREV_extras = "fc2494a2abd7ab21774d03deb09c1362bbb0bba8"
SRCREV_fecrs = "90857deb7973c0ca24c79b9c1809fc9667f32c4f"
SRCREV_libbase = "6d19b5c690fa4220c10e42c2326150bbd4d4b7bb"
SRCREV_core = "a3b721a32242006b59cb12bd62c9133632af3a2d"
SRCREV_logging = "bcac7c30d88a3773a7c0bc9f5617a23a886331fd"
SRCREV_squashfs = "33469ab3363ab75399f05818a70f00a5abae7a78"
# Same revision as avb.inc, so the FEC data and the verifier agree on format.
SRCREV_avb = "4a4e2c8a6592b88cf18b10fe5406f53a2a5d26cf"
SRCREV_FORMAT = "extras_fecrs_libbase_core_logging_squashfs_avb"

PV = "1.0+git"

# Keep objects out of S: the link globs *.o, and S is seven checked-out AOSP trees.
B = "${WORKDIR}/build"

# libavb is built in statically, as AOSP does, at the revision avb.inc pins so
# the parity writer and the verifier agree on the descriptor format.
DEPENDS = "openssl-native zlib-native"

inherit native

# utils/Compat.h is in libutils, not libcutils, despite what the include path
# suggests. cutils/fs.h is only reachable through include_outside_system.
FEC_INCLUDES = "\
    -I${S}/extras/libfec/include \
    -I${S}/extras/ext4_utils/include \
    -I${S}/extras/squashfs_utils \
    -I${S}/fec_rs \
    -I${S}/libbase/include \
    -I${S}/core/libsparse/include \
    -I${S}/core/libcrypto_utils/include \
    -I${S}/core/libcutils/include \
    -I${S}/core/libcutils/include_outside_system \
    -I${S}/core/libutils/include \
    -I${S}/logging/liblog/include \
    -I${S}/squashfs/squashfs-tools \
    -I${S}/avb \
    -I${STAGING_INCDIR_NATIVE} \
"

# Only the sources the tool actually pulls in. Driven by the undefined symbols
# at link time, not by the static_libs lists in the Android.bp files -- those
# name libraries that contribute nothing here, libcrypto_utils among them.
FEC_CXX_SOURCES = "\
    ${S}/extras/verity/fec/main.cpp \
    ${S}/extras/verity/fec/image.cpp \
    ${S}/extras/libfec/fec_open.cpp \
    ${S}/extras/libfec/fec_read.cpp \
    ${S}/extras/libfec/fec_verity.cpp \
    ${S}/extras/libfec/fec_process.cpp \
    ${S}/extras/libfec/avb_utils.cpp \
    ${S}/extras/ext4_utils/ext4_sb.cpp \
    ${S}/libbase/file.cpp \
    ${S}/libbase/strings.cpp \
    ${S}/libbase/threads.cpp \
    ${S}/libbase/mapped_file.cpp \
    ${S}/libbase/stringprintf.cpp \
    ${S}/core/libcutils/klog.cpp \
    ${S}/core/libcutils/android_get_control_file.cpp \
    ${S}/core/libsparse/backed_block.cpp \
    ${S}/core/libsparse/output_file.cpp \
    ${S}/core/libsparse/sparse.cpp \
    ${S}/core/libsparse/sparse_crc32.cpp \
    ${S}/core/libsparse/sparse_err.cpp \
    ${S}/core/libsparse/sparse_read.cpp \
"

do_compile() {
    cd ${B}

    for src in ${FEC_CXX_SOURCES}; do
        ${CXX} -std=c++20 ${CXXFLAGS} ${FEC_INCLUDES} \
            -D_LARGEFILE64_SOURCE -D_GNU_SOURCE -DFEC_NO_KLOG \
            -c "${src}" -o "$(basename ${src} .cpp).o"
    done

    # POSIX strerror_r, not the GNU one: the file assigns the result to an int.
    ${CXX} -std=c++20 ${CXXFLAGS} ${FEC_INCLUDES} -U_GNU_SOURCE -D_POSIX_C_SOURCE=200809L \
        -c ${S}/libbase/posix_strerror_r.cpp -o posix_strerror_r.o

    ${CXX} -std=c++20 ${CXXFLAGS} ${FEC_INCLUDES} \
        -c ${UNPACKDIR}/logging-shim.cpp -o logging-shim.o

    for src in encode_rs_char decode_rs_char init_rs_char; do
        ${CC} ${CFLAGS} -I${S}/fec_rs -c ${S}/fec_rs/${src}.c -o ${src}.o
    done

    ${CC} ${CFLAGS} ${FEC_INCLUDES} -c ${S}/extras/squashfs_utils/squashfs_utils.c \
        -o squashfs_utils.o

    for src in ${S}/avb/libavb/*.c ${S}/avb/libavb/crypto/*.c; do
        ${CC} ${CFLAGS} -DAVB_COMPILATION -I${S}/avb -I${S}/avb/libavb -I${S}/avb/libavb/crypto \
            -c "${src}" -o "avb_$(basename ${src} .c).o"
    done

    ${CXX} ${LDFLAGS} -o fec *.o -lcrypto -lz -lpthread
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${B}/fec ${D}${bindir}/fec
}

# Prove the tool works rather than only that it linked: encode, then repair a
# deliberately corrupted copy and require it back byte-for-byte.
addtask check_fec after do_compile before do_install
do_check_fec() {
    cd ${B}
    dd if=/dev/urandom of=check.img bs=4096 count=64 status=none
    ./fec --encode --roots 2 check.img check.fec
    cp check.img check_bad.img
    dd if=/dev/zero of=check_bad.img bs=1 seek=8192 count=256 conv=notrunc status=none
    ./fec --decode check_bad.img check.fec check_fixed.img
    cmp check.img check_fixed.img || bbfatal "fec could not repair a corrupted image"
    bbnote "fec self-check passed: corrupted image repaired byte-for-byte"
}
