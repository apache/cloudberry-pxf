include common.mk

PXF_MODULES = external-table fdw cli server
export PXF_MODULES

PXF_VERSION ?= $(shell cat version)
export PXF_VERSION

export SKIP_EXTERNAL_TABLE_BUILD_REASON
export SKIP_FDW_BUILD_REASON
export SKIP_EXTERNAL_TABLE_PACKAGE_REASON
export SKIP_FDW_PACKAGE_REASON

SOURCE_EXTENSION_DIR = external-table
TARGET_EXTENSION_DIR = gpextable

# License expression for the BINARY packages.  PXF itself is Apache-2.0, but the
# packages bundle the PXF server's whole runtime classpath, so the package-level
# tag has to cover every license in package/licensing/bundled-components.tsv.
# generate-binary-license.py --check verifies this stays in sync.
LICENSE ?= Apache-2.0 AND BSD-2-Clause AND BSD-3-Clause AND MIT AND EDL-1.0 AND CC0-1.0 AND LicenseRef-Public-Domain AND (EPL-2.0 OR GPL-2.0-with-classpath-exception)
VENDOR  ?= Apache Cloudberry (Incubating)
RELEASE ?= 1

# Apache compliance files shipped inside the binary packages.  The -binary
# variants describe what is actually installed under GPHOME (the PXF server
# application JAR bundles ~144 third-party components); the plain LICENSE and
# NOTICE at the repository root describe the source release instead.  They are
# installed under their plain names so the package carries a LICENSE, a NOTICE
# and a licenses/ directory that describe the package itself.
BINARY_COMPLIANCE_FILES = LICENSE-binary:LICENSE NOTICE-binary:NOTICE DISCLAIMER:DISCLAIMER
BINARY_LICENSES_DIR     = licenses-binary

# $(call install-compliance-files,<destination directory>)
define install-compliance-files
	set -e ;\
	for mapping in $(BINARY_COMPLIANCE_FILES); do \
		src=$${mapping%%:*} ; dst=$${mapping##*:} ;\
		if [[ ! -f "$${src}" ]]; then \
			echo "Error: required compliance file $${src} not found; run 'python3 package/licensing/generate-binary-license.py --generate'" >&2 ;\
			exit 1 ;\
		fi ;\
		cp -a "$${src}" "$(1)/$${dst}" ;\
	done ;\
	if [[ ! -d "$(BINARY_LICENSES_DIR)" ]]; then \
		echo "Error: required directory $(BINARY_LICENSES_DIR) not found" >&2 ;\
		exit 1 ;\
	fi ;\
	rm -rf "$(1)/licenses" ;\
	cp -a "$(BINARY_LICENSES_DIR)" "$(1)/licenses"
endef

default: all

.PHONY: all extensions external-table fdw cli server install install-server stage tar rpm rpm-tar deb deb-tar clean test it help

all: extensions cli server
	@echo "===> PXF compilation is complete <==="

extensions: external-table fdw

external-table:
ifeq ($(SKIP_EXTERNAL_TABLE_BUILD_REASON),)
	@echo "===> Compiling [$@] module <==="
	make -C $@
else
	@echo "Skipping building external-table extension because $(SKIP_EXTERNAL_TABLE_BUILD_REASON)"
endif

ifeq ($(SKIP_FDW_BUILD_REASON),)
	@echo "===> Compiling [$@] module <==="
	make -C fdw
else
	@echo "Skipping building FDW extension because $(SKIP_FDW_BUILD_REASON)"
endif

cli server:
	@echo "===> Compiling [$@] module <==="
	make -C $@

clean:
	rm -rf build
	make -C $(SOURCE_EXTENSION_DIR) clean-all
	make -C cli clean
	make -C server clean
	make -C fdw clean

test:
ifeq ($(SKIP_FDW_BUILD_REASON),)
	make -C fdw installcheck
else
	@echo "Skipping testing FDW extension because $(SKIP_FDW_BUILD_REASON)"
endif
	make -C cli test
	make -C server test

it:
	make -C automation TEST=$(TEST)

install:
ifneq ($(SKIP_EXTERNAL_TABLE_BUILD_REASON),)
	@echo "Skipping installing external-table extension because $(SKIP_EXTERNAL_TABLE_BUILD_REASON)"
	$(eval PXF_MODULES := $(filter-out external-table,$(PXF_MODULES)))
endif
ifneq ($(SKIP_FDW_BUILD_REASON),)
	@echo "Skipping installing FDW extension because $(SKIP_FDW_BUILD_REASON)"
	$(eval PXF_MODULES := $(filter-out fdw,$(PXF_MODULES)))
endif
	set -e ;\
	for module in $${PXF_MODULES[@]}; do \
		echo "===> Installing [$${module}] module <===" ;\
		make -C $${module} install ;\
	done ;\
	echo "===> PXF installation is complete <==="

install-server:
	make -C server install-server

stage:
	rm -rf build/stage
	make -C $(SOURCE_EXTENSION_DIR) stage
ifeq ($(SKIP_FDW_PACKAGE_REASON),)
	make -C fdw stage
else
	@echo "Skipping staging FDW extension because $(SKIP_FDW_PACKAGE_REASON)"
endif
	make -C cli stage  
	make -C server stage
ifneq ($(SKIP_EXTERNAL_TABLE_PACKAGE_REASON),)
	@echo "Skipping staging external-table extension because $(SKIP_EXTERNAL_TABLE_PACKAGE_REASON)"
	$(eval PXF_MODULES := $(filter-out external-table,$(PXF_MODULES)))
endif
ifneq ($(SKIP_FDW_PACKAGE_REASON),)
	@echo "Skipping staging FDW extension because $(SKIP_FDW_PACKAGE_REASON)"
	$(eval PXF_MODULES := $(filter-out fdw,$(PXF_MODULES)))
endif
	set -e ;\
	GP_MAJOR_VERSION=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/gp_major_version) ;\
	GP_BUILD_ARCH=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/build_arch) ;\
	PXF_PACKAGE_NAME=pxf-cloudberry$${GP_MAJOR_VERSION}-$${PXF_VERSION}-$${GP_BUILD_ARCH} ;\
	mkdir -p build/stage/$${PXF_PACKAGE_NAME} ;\
	cp -a $(SOURCE_EXTENSION_DIR)/build/stage/* build/stage/$${PXF_PACKAGE_NAME} ;\
	if [[ -z "$${SKIP_FDW_PACKAGE_REASON:-}" ]]; then \
		cp -a fdw/build/stage/* build/stage/$${PXF_PACKAGE_NAME} ;\
	fi ;\
	cp -a cli/build/stage/* build/stage/$${PXF_PACKAGE_NAME} ;\
	cp -a server/build/stage/* build/stage/$${PXF_PACKAGE_NAME} ;\
	echo $$(git rev-parse --verify HEAD) > build/stage/$${PXF_PACKAGE_NAME}/commit.sha ;\
	cp package/install_binary build/stage/$${PXF_PACKAGE_NAME}/install_component ;\
	$(call install-compliance-files,build/stage/$${PXF_PACKAGE_NAME}) ;\
	echo "===> PXF staging is complete <==="

tar: stage
	rm -rf build/dist
	mkdir -p build/dist
	tar -czf build/dist/$(PXF_PACKAGE_NAME).tar.gz -C build/stage $(PXF_PACKAGE_NAME)
	echo "===> PXF TAR file with binaries creation is complete <==="

gppkg-rpm: rpm
	rm -rf gppkg
	mkdir -p gppkg/deps
	GP_MAJOR_VERSION=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/gp_major_version)
	cat package/gppkg_spec.yml.in | sed "s,#arch,`arch`," | sed "s,#os,$(TEST_OS)," | sed "s,#gppkgver,1.0," | sed "s,#gpver,1," > gppkg/gppkg_spec.yml
	find build/rpmbuild/RPMS -name pxf-cloudberry$(GP_MAJOR_VERSION)-*.rpm -exec cp {} gppkg/ \;
	source $(GPHOME)/greenplum_path.sh || source $(GPHOME)/cloudberry-env.sh && gppkg --build gppkg

rpm: stage
	set -e ;\
	GP_MAJOR_VERSION=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/gp_major_version) ;\
	GP_BUILD_ARCH=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/build_arch) ;\
	PXF_PACKAGE_NAME=pxf-cloudberry$${GP_MAJOR_VERSION}-${PXF_VERSION}-$${GP_BUILD_ARCH} ;\
	PXF_FULL_VERSION=${PXF_VERSION} ;\
	PXF_MAIN_VERSION=$$(echo $${PXF_FULL_VERSION} | sed -E 's/(-SNAPSHOT|-rc[0-9]+)$$//') ;\
	if [[ $${PXF_FULL_VERSION} == *"-SNAPSHOT" ]]; then \
		PXF_RELEASE=SNAPSHOT; \
	elif [[ $${PXF_FULL_VERSION} =~ -rc([0-9]+)$$ ]]; then \
		PXF_RELEASE="rc$${BASH_REMATCH[1]}"; \
	else \
		PXF_RELEASE=1; \
	fi ;\
	rm -rf build/rpmbuild ;\
	mkdir -p build/rpmbuild/{BUILD,RPMS,SOURCES,SPECS} ;\
	cp -a build/stage/$${PXF_PACKAGE_NAME}/* build/rpmbuild/SOURCES ;\
	cp package/*.spec build/rpmbuild/SPECS/ ;\
	rpmbuild \
	--define "_topdir $${PWD}/build/rpmbuild" \
	--define "pxf_version $${PXF_MAIN_VERSION}" \
	--define "pxf_release $${PXF_RELEASE}" \
	--define "license ${LICENSE}" \
	--define "vendor ${VENDOR}" \
	-bb $${PWD}/build/rpmbuild/SPECS/cloudberry-pxf.spec

rpm-tar: rpm
	rm -rf build/{stagerpm,distrpm}
	mkdir -p build/{stagerpm,distrpm}
	set -e ;\
	GP_MAJOR_VERSION=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/gp_major_version) ;\
	PXF_RPM_FILE=$$(find build/rpmbuild/RPMS -name apache-cloudberry-pxf-incubating-*.rpm) ;\
	PXF_RPM_BASE_NAME=$$(basename $${PXF_RPM_FILE%*.rpm}) ;\
	PXF_PACKAGE_NAME=$${PXF_RPM_BASE_NAME%.*} ;\
	mkdir -p build/stagerpm/$${PXF_PACKAGE_NAME} ;\
	cp $${PXF_RPM_FILE} build/stagerpm/$${PXF_PACKAGE_NAME} ;\
	cp package/install_rpm build/stagerpm/$${PXF_PACKAGE_NAME}/install_component ;\
	tar -czf build/distrpm/$${PXF_PACKAGE_NAME}.tar.gz -C build/stagerpm $${PXF_PACKAGE_NAME} ;\
	echo "===> PXF TAR file with RPM package creation is complete <==="

deb: stage
	rm -rf build/debbuild
	set -e ;\
	PXF_MAIN_VERSION=$${PXF_VERSION//-SNAPSHOT/} ;\
	if [[ $${PXF_VERSION} == *"-SNAPSHOT" ]]; then PXF_RELEASE=SNAPSHOT; else PXF_RELEASE=1; fi ;\
	rm -rf build/debbuild ;\
	mkdir -p build/debbuild/usr/local/cloudberry-pxf/$(TARGET_EXTENSION_DIR) ;\
	cp -a $(SOURCE_EXTENSION_DIR)/build/stage/* build/debbuild/usr/local/cloudberry-pxf/ ;\
	if [[ -z "$${SKIP_FDW_PACKAGE_REASON:-}" ]] && [[ -d fdw/build/stage ]]; then \
		cp -a fdw/build/stage/* build/debbuild/usr/local/cloudberry-pxf/ ;\
	fi ;\
	cp -a cli/build/stage/* build/debbuild/usr/local/cloudberry-pxf ;\
	cp -a server/build/stage/* build/debbuild/usr/local/cloudberry-pxf ;\
	$(call install-compliance-files,build/debbuild/usr/local/cloudberry-pxf) ;\
	echo $$(git rev-parse --verify HEAD) > build/debbuild/usr/local/cloudberry-pxf/commit.sha ;\
	mkdir build/debbuild/DEBIAN ;\
	cp -a package/DEBIAN/* build/debbuild/DEBIAN/ ;\
	sed -i -e "s/%VERSION%/$${PXF_MAIN_VERSION}-$${PXF_RELEASE}/" -e "s/%MAINTAINER%/${VENDOR}/" -e "s/%ARCH%/$$(dpkg --print-architecture)/" build/debbuild/DEBIAN/control ;\
	dpkg-deb --build build/debbuild ;\
	mv build/debbuild.deb build/apache-cloudberry-pxf-incubating-$${PXF_MAIN_VERSION}-$${PXF_RELEASE}-$$(lsb_release -si | tr '[:upper:]' '[:lower:]')$$(lsb_release -sr)-$$(dpkg --print-architecture).deb

deb-tar: deb
	rm -rf build/{stagedeb,distdeb}
	mkdir -p build/{stagedeb,distdeb}
	set -e ;\
	GP_MAJOR_VERSION=$$(cat $(SOURCE_EXTENSION_DIR)/build/metadata/gp_major_version) ;\
	PXF_DEB_FILE=$$(find build/ -name apache-cloudberry-pxf-incubating*.deb) ;\
	PXF_PACKAGE_NAME=$$(dpkg-deb --field $${PXF_DEB_FILE} Package)-$$(dpkg-deb --field $${PXF_DEB_FILE} Version)-$$(lsb_release -si | tr '[:upper:]' '[:lower:]')$$(lsb_release -rs) ;\
	mkdir -p build/stagedeb/$${PXF_PACKAGE_NAME} ;\
	cp $${PXF_DEB_FILE} build/stagedeb/$${PXF_PACKAGE_NAME} ;\
	cp package/install_deb build/stagedeb/$${PXF_PACKAGE_NAME}/install_component ;\
	tar -czf build/distdeb/$${PXF_PACKAGE_NAME}.tar.gz -C build/stagedeb $${PXF_PACKAGE_NAME} ;\
	echo "===> PXF TAR file with DEB package creation is complete <==="


help:
	@echo
	@echo 'Possible targets'
	@echo	'  - all - build extensions, cli, and server modules'
	@echo	'  - extensions - build Cloudberry external table and foreign data wrapper extensions'
	@echo	'  - external-table - build Cloudberry external table extension'
	@echo	'  - fdw - build Cloudberry foreign data wrapper extension'
	@echo	'  - cli - install Go CLI dependencies and build Go CLI'
	@echo	'  - server - install server dependencies and build server module'
	@echo	'  - clean - clean up external-table, fdw, CLI and server binaries'
	@echo	'  - test - runs tests for Go CLI and server'
	@echo	'  - install - install external table and foreign data wrapper extensions, CLI and server binaries'
	@echo	'  - install-server - install server binaries only without running tests'
	@echo	'  - stage - install external table and foreign data wrapper extensions, CLI, and server binaries into build/stage/pxf directory'
	@echo	'  - tar - bundle external table and foreign data wrapper extensions, CLI, and server into a single tarball'
	@echo	'  - rpm - create PXF RPM package'
	@echo	'  - rpm-tar - bundle PXF RPM package along with helper scripts into a single tarball'
	@echo	'  - deb - create PXF DEB package'
	@echo	'  - deb-tar - bundle PXF DEB package along with helper scripts into a single tarball'
