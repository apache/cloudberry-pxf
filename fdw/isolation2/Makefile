ISOLATIONCHECKS = pxf_fdw_metadata

GP_VERSION_NUM = $(shell pg_config --gp_version | cut -c 11)

isolationcheck:
ifndef ISOLATION2_ROOT
	$(error ISOLATION2_ROOT is undefined)
endif
	$(ISOLATION2_ROOT)/pg_isolation2_regress $(EXTRA_REGRESS_OPTS) \
		--inputdir=${CURDIR} \
		--outputdir=${CURDIR} \
		$(ISOLATIONCHECKS)

clean:
	rm -rf expected
