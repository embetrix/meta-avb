SUMMARY = "Android Verified Boot 2.0 library"
DESCRIPTION = "libavb is the core verification library for Android Verified Boot \
(AVB). It provides functionality for verifying vbmeta images, hash and hashtree \
descriptors, and chain partitions"
SECTION = "libs"

require avb.inc

SRC_URI += "file://libavb.pc.in"

# Expanded by bitbake: a shell ${PV%%.*} sees an unset PV and yields "".
SOVERSION = "${@d.getVar('PV').split('.')[0]}"

do_compile() {
    for src in ${S}/libavb/*.c ${S}/libavb/crypto/*.c; do
        obj=$(basename ${src} .c).o
        ${CC} ${CFLAGS} -DAVB_COMPILATION -fPIC -I${S}/libavb -I${S}/libavb/crypto -c ${src} -o ${B}/${obj}
    done

    ${CC} ${LDFLAGS} -shared -Wl,-soname,libavb.so.${SOVERSION} -o ${B}/libavb.so.${PV} ${B}/*.o
    ln -sf libavb.so.${PV} ${B}/libavb.so.${SOVERSION}
    ln -sf libavb.so.${PV} ${B}/libavb.so
}

do_install() {
    install -d ${D}${libdir}
    install -m 0755 ${B}/libavb.so.${PV} ${D}${libdir}/
    ln -sf libavb.so.${PV} ${D}${libdir}/libavb.so.${SOVERSION}
    ln -sf libavb.so.${PV} ${D}${libdir}/libavb.so

    install -d ${D}${includedir}/libavb
    install -m 0644 ${S}/libavb/*.h ${D}${includedir}/libavb/

    install -d ${D}${libdir}/pkgconfig
    sed -e 's|@PREFIX@|${prefix}|g' \
        -e 's|@EXEC_PREFIX@|${exec_prefix}|g' \
        -e 's|@LIBDIR@|${libdir}|g' \
        -e 's|@INCLUDEDIR@|${includedir}|g' \
        -e 's|@VERSION@|${PV}|g' \
        ${UNPACKDIR}/libavb.pc.in > ${D}${libdir}/pkgconfig/libavb.pc
}
