ORIG_PACKAGES := "${PACKAGES}"

require recipes/glibc/glibc-package-adjusted.inc

INHIBIT_DEFAULT_DEPS = "1"

# License applies to this recipe code, not the toolchain itself
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COREBASE}/meta/COPYING.MIT;md5=3da9cfbcb788c80a0384361b4de20420"

DEPENDS += "${@base_conditional('PREFERRED_PROVIDER_linux-libc-headers', PN, '', 'linux-libc-headers', d)}"
PROVIDES = "\
	virtual/${TARGET_PREFIX}gcc \
	virtual/${TARGET_PREFIX}g++ \
	virtual/${TARGET_PREFIX}gcc-initial \
	virtual/${TARGET_PREFIX}gcc-intermediate \
	virtual/${TARGET_PREFIX}binutils \
	virtual/${TARGET_PREFIX}compilerlibs \
	virtual/${TARGET_PREFIX}libc-initial \
        ${@base_conditional('PREFERRED_PROVIDER_linux-libc-headers', PN, 'linux-libc-headers', '', d)} \
        ${@base_conditional('PREFERRED_PROVIDER_virtual/libc', PN, 'virtual/libc virtual/libiconv virtual/libintl virtual/${TARGET_PREFIX}libc-for-gcc ${TCLIBC}', '', d)} \
	libgcc \
"
PV = "${CSL_VER_MAIN}"
PR = "r22"

#SRC_URI = "http://www.codesourcery.com/public/gnu_toolchain/${CSL_TARGET_SYS}/arm-${PV}-${TARGET_PREFIX}i686-pc-linux-gnu.tar.bz2"

SRC_URI = "file://SUPPORTED"

do_install() {
	# Use optimized files if available
	sysroot="${EXTERNAL_TOOLCHAIN_SYSROOT}"

	cp -a $sysroot${base_libdir}/. ${D}${base_libdir}
	cp -a $sysroot/sbin/. ${D}${base_sbindir}

	install -d ${D}/usr
	for usr_element in bin libexec sbin share ${base_libdir}; do
		usr_path=$sysroot/usr/$usr_element
		if [ -e "$usr_path" ]; then
			cp -a $usr_path ${D}/usr/
		fi
	done
	if [ "${base_libdir}" != "/lib" ]; then
		if [ -d $sysroot/usr/lib/locale ]; then
			install -d ${D}/usr/lib
			cp -a $sysroot/usr/lib/locale ${D}/usr/lib/
		fi
	fi

	for datadir_element in man info; do
		datadir_path=$sysroot/usr/$datadir_element
		if [ -e $datadir_path ]; then
			cp -a $datadir_path ${D}${datadir}/
		fi
	done

	# Some toolchains have headers under the core specific area
	if [ -e $sysroot/usr/include ]; then
		cp -a $sysroot/usr/include/. ${D}${includedir}
	else
		cp -a $sysroot/../usr/include/. ${D}${includedir}
	fi

        ${@base_conditional('PREFERRED_PROVIDER_linux-libc-headers', PN, 'sed -i -e "s/__packed/__attribute__ ((packed))/" ${D}${includedir}/mtd/ubi-user.h', 'rm -rf ${D}${includedir}/linux ${D}${includedir}/asm* ${D}${includedir}/drm ${D}${includedir}/video ${D}${includedir}/sound ${D}${includedir}/mtd ${D}${includedir}/rdma', d)}
	rm -rf ${D}${datadir}/zoneinfo

	if [ -e ${D}${libdir}/bin ]; then
		cp -a ${D}${libdir}/bin/. ${D}${bindir}/
		rm -r ${D}${libdir}/bin
		install -d ${D}${libdir}/bin
		ln -s ../../bin/gdbserver ${D}${libdir}/bin/sysroot-gdbserver
	fi

	if [ ! -e ${D}${libdir}/libc.so ]; then
		bbfatal "Unable to locate installed libc.so file (${D}${libdir}/libc.so)." \
                        "This may mean that your external toolchain uses a different" \
                        "multi-lib setup than your machine configuration"
	fi

	sed -i -e "s# ${base_libdir}# ../..${base_libdir}#g" -e "s# ${libdir}# .#g" ${D}${libdir}/libc.so
	sed -i -e "s# ${base_libdir}# ../..${base_libdir}#g" -e "s# ${libdir}# .#g" ${D}${libdir}/libpthread.so

	create_multilib_link ${D}

	rm -f ${D}${bindir}/sysroot-*

	# libuuid alone is of limited use, we'll end up building e2fsprogs anyway
	rm -rf ${D}${libdir}/libuuid* ${D}${libdir}/.debug/libuuid* ${D}${includedir}/uuid

	${@base_conditional('PREFERRED_PROVIDER_oprofile', PN, '', 'rm -rf ${D}${bindir}/op* ${D}${datadir}/oprofile ${D}${libdir}/oprofile ${D}${datadir}/stl.pat ${D}${mandir}/man1/oprofile* ${D}${docdir}/oprofile ${D}${bindir}/.debug/op* ${D}${includedir}/opagent.h', d)}
	${@base_conditional('PREFERRED_PROVIDER_popt', PN, '', 'rm -rf ${D}${libdir}/libpopt.* ${D}${includedir}/popt.h', d)}
	${@base_conditional('PREFERRED_PROVIDER_liburcu', PN, '', 'rm -rf ${D}${libdir}/liburcu*.* ${D}${includedir}/urcu*', d)}
	${@base_conditional('PREFERRED_PROVIDER_lttng-ust', PN, '', 'rm -rf ${D}${libdir}/liblttng-ust*.* ${D}${libdir}/libmet* ${D}${libdir}/mettools ${D}${includedir}/lttng/bug.h ${D}${includedir}/lttng/align.h ${D}${includedir}/lttng/ust*.h ${D}${includedir}/lttng/tracepoint*.h ${D}${includedir}/lttng/ringbuffer*.h', d)}
	${@base_conditional('PREFERRED_PROVIDER_lttng-tools', PN, '', 'rm -rf ${D}${bindir}/lttng* ${D}${libdir}/liblttng-ctl.so.* ${D}${libdir}/lttng ${D}${libdir}/liblttng-ctl.so ${D}${libdir}/liblttng-ctl.a ${D}${includedir}/lttng/lttng.h ${D}${libdir}/pkgconfig ${D}${includedir}/lttng/lttng-error.h', d)}
	chown -R 0:0 ${D}

	# Remove files that are being provided by package util-linux
	rm -rf ${D}${libdir}/libmount* ${D}${libdir}/libblkid* ${D}${includedir}/libmount/libmount.h ${D}${includedir}/blkid/blkid.h ${D}${datadir}/bash-completion 	

	if [ "${PACKAGE_DEBUG_SPLIT_STYLE}" == "debug-file-directory"  ]; then

		install -d ${D}${libdir}/debug

		for dir in ${base_libdir} ${base_sbindir} ${libdir} ${bindir} ${sbindir} ${libdir}/audit ${libexecdir}/getconf; do
			install -d ${D}${libdir}/debug$dir
			mv ${D}$dir/.debug/*.debug  ${D}${libdir}/debug$dir || true
			rm -rf ${D}$dir/.debug
		done
	fi
}

# These files are picked up out of the sysroot by glibc-locale, so we don't
# need to keep them around ourselves.
do_install_locale_append() {
	rm -fr ${D}${exec_prefix}/lib/locale
}

def sysroot_multilib_suffix(d):
    PATH = d.getVar('PATH', True)
    cmd = '${CC} -print-sysroot | sed -e "s,^${STAGING_DIR_HOST},,; s,^/,,"'
    multilib_suffix = oe.path.check_output(bb.data.expand(cmd, d), shell=True, env={'PATH': PATH}).rstrip()
    if multilib_suffix:
        return '/' + multilib_suffix
    else:
        return ''

create_multilib_link () {
	dest="$1"
	sysroot_multilib_suffix="${@sysroot_multilib_suffix(d)}"
	if [ -n "$sysroot_multilib_suffix" ]; then
		rm -f $dest/$sysroot_multilib_suffix
		ln -s . $dest/$sysroot_multilib_suffix
	fi
}

SYSROOT_PREPROCESS_FUNCS += "external_toolchain_sysroot_adjust"
external_toolchain_sysroot_adjust() {
	create_multilib_link ${SYSROOT_DESTDIR}

	# If the usr/lib directory doesn't exist, the toolchain fails to even
	# try to find crti.o in a completely different directory (usr/lib64)
	install -d ${SYSROOT_DESTDIR}/usr/lib
}

TC_PACKAGES =+ "libgcc libgcc-dev"
TC_PACKAGES =+ "libgomp libgomp-dev libgomp-staticdev"
TC_PACKAGES =+ "libquadmath libquadmath-dev libquadmath-staticdev"
TC_PACKAGES =+ "libstdc++ libstdc++-dev libstdc++-staticdev"
TC_PACKAGES =+ "gdbserver gdbserver-dbg"
TC_PACKAGES =+ "libasan libasan-dev"
TC_PACKAGES =+ "libtsan libtsan-dev"
TC_PACKAGES =+ "libitm libitm-dev"
TC_PACKAGES =+ "libatomic libatomic-dev"
TC_PACKAGES =+ "libinproctrace"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_linux-libc-headers', PN, 'linux-libc-headers linux-libc-headers-dev', '', d)}"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_oprofile', PN, 'oprofile oprofile-doc', '', d)}"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_popt', PN, 'popt popt-dev', '', d)}"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_liburcu', PN, 'liburcu liburcu-dev', '', d)}"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_lttng-ust', PN, 'lttng-ust lttng-ust-dev', '', d)}"
TC_PACKAGES =+ "${@base_conditional('PREFERRED_PROVIDER_lttng-tools', PN, 'lttng-tools lttng-tools-dev', '', d)}"
PACKAGES =+ "${TC_PACKAGES}"

FILES_${PN}-dev += "${@sysroot_multilib_suffix(d)}"
FILES_${PN} += "${prefix}/libexec/*"
FILES_${PN}-dbg += "${prefix}/libexec/*/.debug"

SUMMARY_libasan = "The Address Sanitizer runtime library"
SUMMARY_libasan-dev = "${SUMMARY_libasan} - development files"
FILES_libasan = "${libdir}/libasan.so.*"
FILES_libasan-dev = "${libdir}/libasan.so"
FILES_libatomic = "${libdir}/libatomic.so.*"
FILES_libatomic-dev = "${libdir}/libatomic.so"
FILES_libinproctrace = "${libdir}/libinproctrace.so"
FILES_oprofile = "${bindir}/op* ${datadir}/oprofile ${libdir}/oprofile ${datadir}/stl.pat"
FILES_oprofile-doc = "${mandir}/man1/oprofile* ${docdir}/oprofile"
FILES_${PN}-dbg += "${bindir}/.debug/op*"

SUMMARY_libtsan = "The Thread Sanitizer runtime library"
SUMMARY_libtsan-dev = "${SUMMARY_libtsan} - development files"
FILES_libtsan = "${libdir}/libtsan.so.*"
FILES_libtsan-dev = "${libdir}/libtsan.so"

SUMMARY_libitm = "The Transactional Memory runtime library"
SUMMARY_libitm-dev = "${SUMMARY_libitm} - development files"
FILES_libitm = "${libdir}/libitm.so.*"
FILES_libitm-dev = "${libdir}/libitm.so"

FILES_popt = "${libdir}/libpopt.so.*"
FILES_popt-dev = "${libdir}/libpopt.so ${libdir}/libpopt.a ${includedir}/popt.h"
FILES_lttng-tools = "${bindir}/lttng* ${libdir}/liblttng-ctl.so.* ${libdir}/lttng"
FILES_lttng-tools-dev = "${libdir}/liblttng-ctl.so ${libdir}/liblttng-ctl.a ${includedir}/lttng/lttng.h"
FILES_liburcu = "${libdir}/liburcu*.so.*"
FILES_liburcu-dev = "${libdir}/liburcu*.so ${libdir}/liburcu.a ${includedir}/urcu*"
FILES_lttng-ust = "${libdir}/liblttng-ust*.so.* ${libdir}/libmet*.so.* \
                   ${libdir}/mettools/*.so"
FILES_lttng-ust-dev = "${libdir}/liblttng-ust*.so ${libdir}/liblttng-ust.a \
		       ${libdir}/libmet*.so ${libdir}/mettools/.debug \
                       ${includedir}/lttng/bug.h \
                       ${includedir}/lttng/align.h \
                       ${includedir}/lttng/ust*.h \
                       ${includedir}/lttng/tracepoint*.h \
                       ${includedir}/lttng/ringbuffer*.h \
                       "
FILES_${PN}-dbg += "${libdir}/mettools/.debug"

# Inhibit warnings about files being stripped, we can't do anything about it.
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"

# We support multiple toolchain versions, so an unpackaged file isn't
# necessarily a problem.
INSANE_SKIP_${PN} = "installed-vs-shipped"

# This test should be fixed to ignore .a files in .debug dirs
INSANE_SKIP_${PN}-dbg = "staticdev"

# We don't care about GNU_HASH in prebuilt binaries
INSANE_SKIP_${PN}-utils += "ldflags"
INSANE_SKIP_libgcc += "ldflags"
INSANE_SKIP_libasan += "ldflags"
INSANE_SKIP_libatomic += "ldflags"
INSANE_SKIP_libinproctrace += "ldflags"
INSANE_SKIP_libgomp += "ldflags"
INSANE_SKIP_libitm += "ldflags"
INSANE_SKIP_libquadmath += "ldflags"
INSANE_SKIP_libstdc++ += "ldflags"
INSANE_SKIP_libtsan += "ldflags"
INSANE_SKIP_gdbserver += "ldflags"

# We don't care about rpaths in third party binaries
INSANE_SKIP_${PN}-utils += "useless-rpaths"

PKGV = "${CSL_VER_LIBC}"
PKGV_libgcc = "${CSL_VER_GCC}"
PKGV_libgcc-dev = "${CSL_VER_GCC}"
PKGV_libasan = "${CSL_VER_GCC}"
PKGV_libasan-dev = "${CSL_VER_GCC}"
PKGV_libgomp = "${CSL_VER_GCC}"
PKGV_libgomp-dev = "${CSL_VER_GCC}"
PKGV_libgomp-staticdev = "${CSL_VER_GCC}"
PKGV_libitm = "${CSL_VER_GCC}"
PKGV_libitm-dev = "${CSL_VER_GCC}"
PKGV_libquadmath = "${CSL_VER_GCC}"
PKGV_libquadmath-dev = "${CSL_VER_GCC}"
PKGV_libquadmath-staticdev = "${CSL_VER_GCC}"
PKGV_libstdc++ = "${CSL_VER_GCC}"
PKGV_libstdc++-dev = "${CSL_VER_GCC}"
PKGV_libstdc++-staticdev = "${CSL_VER_GCC}"
PKGV_libtsan = "${CSL_VER_GCC}"
PKGV_libtsan-dev = "${CSL_VER_GCC}"
PKGV_libinproctrace = "${CSL_VER_GCC}"
PKGV_gdbserver = "${CSL_VER_GDB}"
PKGV_gdbserver-dbg = "${CSL_VER_GDB}"

def csl_get_gdb_license(d):
    try:
        stdout, stderr = csl_run(d, 'gdb', '-v')
    except bb.process.CmdError as c:
        return 'UNKNOWN(%s)' % c
    else:
        for line in stdout.splitlines():
            if line.startswith('License '):
                lic = line.split(':', 1)[0]
                return lic.replace('License ', '')

        return 'UNKNOWN'

CSL_LIC_GDB = "${@csl_get_gdb_license(d)}"
LICENSE_gdbserver = "${CSL_LIC_GDB}"
LICENSE_gdbserver-dbg = "${CSL_LIC_GDB}"

FILES_libgcc = "${base_libdir}/libgcc_s.so.1"
FILES_libgcc-dev = "${base_libdir}/libgcc_s.so"
FILES_libgomp = "${libdir}/libgomp.so.*"
FILES_libgomp-dev = "${libdir}/libgomp.so"
FILES_libgomp-staticdev = "${libdir}/libgomp.a"
FILES_libquadmath = "${libdir}/libquadmath.so.*"
FILES_libquadmath-dev = "${libdir}/libquadmath.so"
FILES_libquadmath-staticdev = "${libdir}/libquadmath.so"
FILES_libstdc++ = "${libdir}/libstdc++.so.*"
FILES_libstdc++-dev = "${includedir}/c++/${PV} \
	${libdir}/libstdc++.so \
	${libdir}/libstdc++.la \
	${libdir}/libsupc++.la"
FILES_libstdc++-staticdev = "${libdir}/libstdc++.a ${libdir}/libsupc++.a"
FILES_gdbserver = "${bindir}/gdbserver ${libdir}/bin/sysroot-gdbserver"
FILES_gdbserver-dbg = "${bindir}/.debug/gdbserver.debug"
FILES_gdbserver-dbg += "${libdir}/debug${bindir}/gdbserver.debug"

CSL_VER_MAIN ??= ""

python () {
    if not d.getVar("CSL_VER_MAIN"):
	raise bb.parse.SkipPackage("External CSL toolchain not configured (CSL_VER_MAIN not set).")

    pn = d.getVar('PN', True)
    if d.getVar('PREFERRED_PROVIDER_virtual/libc', True) != pn:
        d.setVar('PACKAGES', '${TC_PACKAGES}')
        d.delVar('PKG_%s' % pn)
        d.delVar('RPROVIDES_%s' % pn)
}
