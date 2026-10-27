# The AOSP `fec` tool, which avbtool shells out to for the dm-verity forward
# error correction data. AOSP builds it with Soong from several repositories;
# this assembles the same binary from those repositories, fetched side by side
# by the recipe, taking only the sources the tool actually pulls in -- the set
# is driven by the undefined symbols at link time, not by the static_libs lists
# in the Android.bp files, which name libraries that contribute nothing here.
#
# libavb comes from the system, so the parity writer and avb_verify share one
# copy of the descriptor code.
cmake_minimum_required(VERSION 3.10)
project(fec C CXX)

include(GNUInstallDirs)

find_package(OpenSSL REQUIRED)
find_package(ZLIB REQUIRED)
find_package(Threads REQUIRED)
find_package(PkgConfig REQUIRED)
pkg_check_modules(LIBAVB REQUIRED IMPORTED_TARGET libavb)

set(EXTRAS   ${CMAKE_CURRENT_SOURCE_DIR}/extras)
set(FEC_RS   ${CMAKE_CURRENT_SOURCE_DIR}/fec_rs)
set(LIBBASE  ${CMAKE_CURRENT_SOURCE_DIR}/libbase)
set(CORE     ${CMAKE_CURRENT_SOURCE_DIR}/core)
set(LOGGING  ${CMAKE_CURRENT_SOURCE_DIR}/logging)
set(SQUASHFS ${CMAKE_CURRENT_SOURCE_DIR}/squashfs)

add_executable(fec
    ${EXTRAS}/verity/fec/main.cpp
    ${EXTRAS}/verity/fec/image.cpp
    ${EXTRAS}/libfec/fec_open.cpp
    ${EXTRAS}/libfec/fec_read.cpp
    ${EXTRAS}/libfec/fec_verity.cpp
    ${EXTRAS}/libfec/fec_process.cpp
    ${EXTRAS}/libfec/avb_utils.cpp
    ${EXTRAS}/ext4_utils/ext4_sb.cpp
    ${EXTRAS}/squashfs_utils/squashfs_utils.c
    ${LIBBASE}/file.cpp
    ${LIBBASE}/strings.cpp
    ${LIBBASE}/threads.cpp
    ${LIBBASE}/mapped_file.cpp
    ${LIBBASE}/stringprintf.cpp
    ${LIBBASE}/posix_strerror_r.cpp
    ${CORE}/libcutils/klog.cpp
    ${CORE}/libcutils/android_get_control_file.cpp
    ${CORE}/libsparse/backed_block.cpp
    ${CORE}/libsparse/output_file.cpp
    ${CORE}/libsparse/sparse.cpp
    ${CORE}/libsparse/sparse_crc32.cpp
    ${CORE}/libsparse/sparse_err.cpp
    ${CORE}/libsparse/sparse_read.cpp
    ${FEC_RS}/encode_rs_char.c
    ${FEC_RS}/decode_rs_char.c
    ${FEC_RS}/init_rs_char.c
    logging_shim.cpp
)

target_compile_features(fec PRIVATE cxx_std_20)

# utils/Compat.h is in libutils, not libcutils, despite what the include path
# suggests. cutils/fs.h is only reachable through include_outside_system.
target_include_directories(fec PRIVATE
    ${EXTRAS}/libfec/include
    ${EXTRAS}/ext4_utils/include
    ${EXTRAS}/squashfs_utils
    ${FEC_RS}
    ${LIBBASE}/include
    ${CORE}/libsparse/include
    ${CORE}/libcrypto_utils/include
    ${CORE}/libcutils/include
    ${CORE}/libcutils/include_outside_system
    ${CORE}/libutils/include
    ${LOGGING}/liblog/include
    ${SQUASHFS}/squashfs-tools
)

target_compile_definitions(fec PRIVATE
    _GNU_SOURCE
    FEC_NO_KLOG
)

# POSIX strerror_r, not the GNU one: the file assigns the result to an int.
# Source options come after the target's, so this undoes the _GNU_SOURCE
# above and the one g++ itself predefines.
set_source_files_properties(${LIBBASE}/posix_strerror_r.cpp PROPERTIES
    COMPILE_OPTIONS "-U_GNU_SOURCE;-D_POSIX_C_SOURCE=200809L")

target_link_libraries(fec PRIVATE
    PkgConfig::LIBAVB OpenSSL::Crypto ZLIB::ZLIB Threads::Threads)

install(TARGETS fec RUNTIME DESTINATION ${CMAKE_INSTALL_BINDIR})
