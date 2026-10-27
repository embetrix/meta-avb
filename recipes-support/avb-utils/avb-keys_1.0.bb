SUMMARY = "AVB public verification key"
DESCRIPTION = "Extracts and installs the AVB public key from AVB_SIGN_KEY \
for runtime verification tool"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/files/common-licenses/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit python3native

DEPENDS = "python3-avbtool-native"

do_install() {

    if [ -z "${AVB_SIGN_KEY}" ] || [ ! -f "${AVB_SIGN_KEY}" ]; then
        bbfatal "AVB_SIGN_KEY not found: ${AVB_SIGN_KEY}"
    fi
    avbtool extract_public_key --key "${AVB_SIGN_KEY}" --output ${T}/avb_pubkey.bin
    install -d ${D}${sysconfdir}/avb
    install -m 0644 ${T}/avb_pubkey.bin ${D}${sysconfdir}/avb/
}

do_install[vardeps] += "AVB_SIGN_KEY"
