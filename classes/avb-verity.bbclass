# SPDX-License-Identifier: MIT
# Copyright (C) 2026 Embetrix Embedded Systems Solutions <ayoub.zaki@embetrix.com>
#
# AVB/DM-Verity support:
#   IMAGE_FSTYPES conversion that appends an AVB hashtree footer to a
#   filesystem image using avbtool.  The footer is placed right after
#   the filesystem (--partition_size 0) so that avb_verify can scan
#   for it and set up dm-verity via dmsetup at runtime.
inherit image_types

# avb-utils-native carries avb_sign and avbtool, and pulls in fec-native for
# the fec tool avbtool shells out to.
DEPENDS += "avb-utils-native"
CONVERSIONTYPES += "avbverity"

WICVARS:append = " AVB_SIGN_KEY AVB_ALGORITHM AVB_HASH_ALGORITHM AVB_ROOT_HASH_SIGN AVB_X509 AVB_FEC_ROOTS"

# Default AVB settings
AVB_ALGORITHM ?= "SHA256_RSA4096"
AVB_HASH_ALGORITHM ?= "sha256"
AVB_PARTITION_NAME ?= "rootfs"

AVB_ROOT_HASH_SIGN ?= "0"

# Reed-Solomon roots for the dm-verity forward error correction data that is
# written after the hash tree. dm-verity repairs up to roots/2 corrupt blocks
# per 255-block interleave group, at roots/(255-roots) extra space, and accepts
# 2 to 24. Parity is only read once verification has already failed, so this
# costs space, not read speed.
AVB_FEC_ROOTS ?= "2"

# Partition size for avbtool (bytes):  Default 0 = auto-size the partition
# to fit the image + hashtree + footer exactly.  Override to set an explicit
# fixed partition size (must be larger than the image)
AVB_PARTITION_SIZE ?= "0"

# Block device paths used in the generated dm-verity command line.
# Override per-machine when the rootfs lives on a different device.
AVB_DATA_DEV ?= "/dev/mmcblk0p2"

# dm-verity uses 4096-byte data blocks; the filesystem block size
# must match or the kernel will refuse to mount.
EXTRA_IMAGECMD:ext4:append = " -b 4096"

avbverity_setup() {

    IMAGE_IN=$1
    IMAGE_OUT=$2
    CERT_ARGS=""
    SIG_ARGS=""

    if [ ! -f "${AVB_SIGN_KEY}" ] ; then
        bbfatal "AVB sign key not found: ${AVB_SIGN_KEY}"
    fi

    if [ "${AVB_ROOT_HASH_SIGN}" = "1" ]; then
        if [ ! -f "${AVB_X509}" ]; then
            bbfatal "AVB X.509 cert not found: ${AVB_X509}"
        fi
        CERT_ARGS="--cert ${AVB_X509}"
    fi

    avb_sign \
        --image  "${IMAGE_IN}" \
        --output "${IMAGE_OUT}" \
        --key    "${AVB_SIGN_KEY}" \
        ${CERT_ARGS} \
        --partition-name "${AVB_PARTITION_NAME}" \
        --algorithm "${AVB_ALGORITHM}" \
        --fec-roots "${AVB_FEC_ROOTS}"

    generate_verity_cmdline "${IMAGE_OUT}"

}

sign_root_hash() {

    DM_TABLE=$1
    SIGN_KEY=$2
    X509_CERT=$3

    ROOT_HASH=$(echo "${DM_TABLE}" | awk '{print $12}')
    # Per-hash names for the same reason as PUBKEY above: parallel conversions.
    ROOTHASH_FILE="${T}/roothash.${ROOT_HASH}.txt"
    ROOTHASH_SIG="${ROOTHASH_FILE}.signed"
    printf '%s' "${ROOT_HASH}" > "${ROOTHASH_FILE}"

    openssl cms -sign -nocerts -noattr -binary \
        -in "${ROOTHASH_FILE}" -inkey "${SIGN_KEY}" -signer "${X509_CERT}" \
        -outform der -out "${ROOTHASH_SIG}"

    od -An -tx1 "${ROOTHASH_SIG}" | tr -d ' \n'
}

generate_verity_cmdline() {

    IMAGE=$1
    SIG_ARGS=""

    # Per-image name: every do_image_<type> conversion shares one T and the
    # tasks run in parallel, so a fixed filename here is a read/write race.
    PUBKEY="${T}/avb_pubkey.$(basename "${IMAGE}").bin"
    avbtool extract_public_key --key "${AVB_SIGN_KEY}" --output "${PUBKEY}"

    DM_TABLE=$(avb_verify -n -t -d "${IMAGE}" -k "${PUBKEY}")

    if [ "${AVB_ROOT_HASH_SIGN}" = "1" ]; then
        SIG_HEX=$(sign_root_hash "${DM_TABLE}" "${AVB_SIGN_KEY}" "${AVB_X509}")
        SIG_ARGS="root_hash_sig_hex ${SIG_HEX}"
    fi

    # dm table from avb_verify: 0 <sectors> verity <ver> <dev> <dev> <dbs> <hbs> <nblk> <hstart> <alg> <root_hash> <salt> [<nopt> <opt>...]
    # dm-mod.create format: <name>,<uuid>,<minor>,<flags>,<start> <size> <target_type> <target_args>
    # Of the optional arguments, the FEC group is kept and pointed at the data
    # device; root_hash_sig_key_desc is dropped because on the cmdline path the
    # signature travels inline as root_hash_sig_hex instead. The count is
    # recomputed from what is actually emitted.
    CMDLINE=$(echo "${DM_TABLE}" | awk -v ddev="${AVB_DATA_DEV}" -v hdev="${AVB_DATA_DEV}" -v sigargs="${SIG_ARGS}" '{
        opt = ""; nopt = 0
        for (i = 15; i <= NF; i++) {
            if ($i == "root_hash_sig_key_desc") { i++; continue }
            if ($i == "use_fec_from_device") { opt = opt " " $i " " ddev; i++; nopt += 2; continue }
            opt = opt " " $i; nopt++
        }
        if (sigargs != "") { opt = opt " " sigargs; nopt += 2 }
        if (nopt > 0) opt = " " nopt opt
        printf "dm-mod.create=\"verity,,0,ro,0 %s verity %s %s %s %s %s %s %s %s %s %s%s\" root=/dev/dm-0 ro\n",
            $2, $4, ddev, hdev, $7, $8, $9, $10, $11, $12, $13, opt
    }')

    install -d "${DEPLOY_DIR_IMAGE}"
    printf '%s\n' "${CMDLINE}" > "${DEPLOY_DIR_IMAGE}/cmdline.verity"
    bbnote "Generated ${DEPLOY_DIR_IMAGE}/cmdline.verity"
}

CONVERSION_CMD:avbverity = "avbverity_setup ${IMAGE_NAME}.${type} ${IMAGE_NAME}.${type}.avbverity"
CONVERSION_DEPENDS_avbverity = "avb-utils-native"
