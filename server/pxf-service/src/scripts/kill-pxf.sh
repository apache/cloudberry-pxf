#!/bin/bash

function close_inherited_fds() {
    local fd
    for fd in /dev/fd/[0-9]*; do
        fd=${fd##*/}
        [[ $fd =~ ^[0-9]+$ && $fd -gt 2 ]] || continue
        eval "exec ${fd}>&-"
    done
}

function log() {
    echo "=====> $(date) $* <======" >> "${PXF_LOGDIR}/pxf-oom.log"
}

function _main() {
    sleep 1
    log 'Stopping PXF'
    "${PXF_HOME}/bin/pxf" stop && return
    # "insurance policy":
    local PID=$1
    if [[ -n $PID ]]; then
        ps "$PID" >/dev/null && kill -9 "$PID"
    fi
}

# Close inherited JVM FDs before launching any child commands.
close_inherited_fds

log 'PXF Out of memory detected'
_main "$1" &
log 'PXF shutdown scheduled'
