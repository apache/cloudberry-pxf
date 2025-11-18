# TODO: Add support for Ubuntu/Debian package builds as well
# This Makefile currently focuses on RPM-based distributions (Rocky Linux).
# Ubuntu support should be added for deb package builds.

.PHONY: help rpm-cbdb-rocky9 rpm-pxf-cbdb1-rocky9

help:
	@echo
	@echo 'Possible targets'
	@echo	'  - rpm-cbdb-rocky9 - build Cloudberry RPM package for Rocky Linux 9 and place it in downloads'
	@echo	'  - rpm-pxf-cbdb1-rocky9 - build PXF RPM package for Cloudberry on Rocky Linux 9 and place it in downloads'

#
# Cloudberry RPM build
#
rpm-cbdb-rocky9: DOCKER_TAG = rpm-cbdb-rocky9
rpm-cbdb-rocky9:
	echo "===> Building Cloudberry RPM for Rocky Linux 9 <==="
	@mkdir -p ../downloads
	@if [ -f ../downloads/apache-cloudberry-db-incubating-$${CLOUDBERRY_VERSION:-1.0.0}-$${CLOUDBERRY_RELEASE:-1}.el9.x86_64.rpm ]; then \
		echo "Skipping build: RPM already exists in downloads."; \
	else \
		docker build -f 'cbdb/rpm/Dockerfile' .. \
			--build-arg CLOUDBERRY_VERSION=$${CLOUDBERRY_VERSION:-1.0.0} \
			--build-arg CLOUDBERRY_RELEASE=$${CLOUDBERRY_RELEASE:-1} \
			--build-arg CLOUDBERRY_BRANCH=$${CLOUDBERRY_BRANCH:-main} \
			--tag ${DOCKER_TAG}:1.0 && \
		docker create --name extract-rpm-cbdb-rocky9 ${DOCKER_TAG}:1.0 && \
		docker cp extract-rpm-cbdb-rocky9:/build/. ../downloads/ && \
		docker rm extract-rpm-cbdb-rocky9; \
	fi

#
# PXF RPM build
#
rpm-pxf-cbdb1-rocky9: DOCKER_TAG = rpm-pxf-cbdb1-rocky9
rpm-pxf-cbdb1-rocky9: rpm-cbdb-rocky9
	echo "===> Building PXF RPM for Cloudberry on Rocky Linux 9 <==="
	@mkdir -p ../downloads
	@if [ -f ../downloads/pxf-cbdb1-$${PXF_VERSION:-6.0.0}-$${PXF_RELEASE:-1}.el9.x86_64.rpm ]; then \
		echo "Note: RPM already exists in downloads, but rebuilding anyway."; \
	fi
	docker build -f 'pxf/rpm/Dockerfile' .. \
		--build-arg PXF_VERSION=$${PXF_VERSION:-6.0.0} \
		--tag ${DOCKER_TAG}:1.0 && \
	docker create --name extract-rpm-pxf-cbdb1-rocky9 ${DOCKER_TAG}:1.0 && \
	docker cp extract-rpm-pxf-cbdb1-rocky9:/build/. ../downloads/ && \
	docker rm extract-rpm-pxf-cbdb1-rocky9
