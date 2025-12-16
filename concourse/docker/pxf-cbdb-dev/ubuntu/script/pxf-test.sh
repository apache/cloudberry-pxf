#!/bin/bash
set -euo pipefail

RUN_TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "${RUN_TESTS_DIR}/../../../../.." && pwd)"

# Load env
source "${RUN_TESTS_DIR}/pxf-env.sh"

# Test results tracking
declare -A TEST_RESULTS
RESULTS_FILE="${REPO_ROOT}/automation/test_artifacts/component_results.csv"

# Ensure artifacts directory
mkdir -p "${REPO_ROOT}/automation/test_artifacts"

# Initialize results file
echo "Component,Status,ExitCode" > "$RESULTS_FILE"

record_result() {
    local component=$1
    local status=$2
    local exit_code=$3
    echo "$component,$status,$exit_code" >> "$RESULTS_FILE"
    TEST_RESULTS[$component]=$status
}

test_cli() {
    echo "=== Testing PXF CLI ==="
    cd "${REPO_ROOT}/cli"
    if make test; then
        record_result "CLI" "PASS" 0
        return 0
    else
        record_result "CLI" "FAIL" $?
        return 1
    fi
}

test_fdw() {
    echo "=== Testing PXF FDW ==="
    [ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
    cd "${REPO_ROOT}/fdw"
    if make test; then
        record_result "FDW" "PASS" 0
        return 0
    else
        record_result "FDW" "FAIL" $?
        return 1
    fi
}

test_external_table() {
    echo "=== Testing PXF External Table ==="
    [ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
    cd "${REPO_ROOT}/external-table"
    if make installcheck; then
        record_result "External-Table" "PASS" 0
        return 0
    else
        record_result "External-Table" "FAIL" $?
        return 1
    fi
}

test_server() {
    echo "=== Testing PXF Server ==="
    [ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
    cd "${REPO_ROOT}/server"
    if ./gradlew test; then
        record_result "Server" "PASS" 0
        return 0
    else
        record_result "Server" "FAIL" $?
        return 1
    fi
}

test_automation() {
    echo "=== Testing PXF Automation ==="
    if "${RUN_TESTS_DIR}/run_tests.sh"; then
        record_result "Automation" "PASS" 0
        return 0
    else
        record_result "Automation" "FAIL" $?
        return 1
    fi
}

display_results() {
    echo
    echo "=========================================="
    echo "PXF Component Test Results"
    echo "=========================================="
    column -t -s',' "$RESULTS_FILE"
    echo "=========================================="
    echo
    
    # Count results
    local total=0
    local passed=0
    local failed=0
    
    for component in "${!TEST_RESULTS[@]}"; do
        ((total++))
        if [ "${TEST_RESULTS[$component]}" = "PASS" ]; then
            ((passed++))
        else
            ((failed++))
        fi
    done
    
    echo "Summary: $total components, $passed passed, $failed failed"
    echo
    
    return $failed
}

usage() {
    cat <<EOF
Usage: $0 [COMPONENT...]

Run PXF component tests. If no component specified, runs all.

Components:
  cli              Test PXF CLI
  fdw              Test PXF FDW
  external-table   Test PXF External Table
  server           Test PXF Server
  automation       Test PXF Automation (smoke tests)
  all              Run all tests (default)

Examples:
  $0 cli fdw           # Run CLI and FDW tests only
  $0 server            # Run server tests only
  $0                   # Run all tests
EOF
}

main() {
    local components=("$@")
    local exit_code=0

    # If no args, run all
    if [ ${#components[@]} -eq 0 ]; then
        components=(cli fdw external-table server automation)
    fi

    # Handle 'all' keyword
    if [ "${components[0]}" = "all" ]; then
        components=(cli fdw external-table server automation)
    fi

    # Handle help
    if [ "${components[0]}" = "-h" ] || [ "${components[0]}" = "--help" ]; then
        usage
        exit 0
    fi

    echo "Running tests for: ${components[*]}"
    echo

    # Run requested tests
    for component in "${components[@]}"; do
        case "$component" in
            cli)
                test_cli || exit_code=1
                ;;
            fdw)
                test_fdw || exit_code=1
                ;;
            external-table)
                test_external_table || exit_code=1
                ;;
            server)
                test_server || exit_code=1
                ;;
            automation)
                test_automation || exit_code=1
                ;;
            *)
                echo "Unknown component: $component"
                usage
                exit 1
                ;;
        esac
        echo
    done

    # Display results
    display_results || exit_code=$?

    exit $exit_code
}

main "$@"
