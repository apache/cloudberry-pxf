EXTENSION = pxf
DATA = pxf--2.0.sql pxf--1.0--2.0.sql pxf--1.0.sql
MODULE_big = pxf
OBJS       = src/pxfprotocol.o src/pxfbridge.o src/pxfuriparser.o src/libchurl.o src/pxfutils.o src/pxfheaders.o src/gpdbwritableformatter.o src/pxffilters.o
REGRESS    = setup pxf pxfinvalid

PG_CONFIG = pg_config
PGXS := $(shell $(PG_CONFIG) --pgxs)

PXF_API_VERSION := $(shell cat ../api_version)
PG_CPPFLAGS := -DPXF_API_VERSION=\"$(PXF_API_VERSION)\"

ifndef PGXS
    $(error Make sure the Greenplum installation binaries are in your PATH. i.e. export PATH=<path to your greenplum installation>/bin:$$PATH)
endif

include $(PGXS)

SHLIB_LINK += -lcurl

.PHONY: stage
stage: pxf.so
	mkdir -p build/metadata
	mkdir -p build/stage
	install -c -m 755 pxf.so build/stage/pxf.so
	install -c -m 644 pxf.control build/stage/
	install -c -m 644 pxf--1.0.sql build/stage/
	install -c -m 644 pxf--2.0.sql build/stage/
	install -c -m 644 pxf--1.0--2.0.sql build/stage/
	@echo "gpdb.version=$(GP_VERSION)" > build/stage/metadata
	@echo "gpdb.major-version=$(GP_MAJORVERSION)" > build/stage/metadata
	@echo $(GP_MAJORVERSION) > build/metadata/gp_major_version
	@echo $(BLD_ARCH) | sed 's/_/-/' > build/metadata/build_arch

.PHONY: clean-all
clean-all: clean
	rm -rf build
