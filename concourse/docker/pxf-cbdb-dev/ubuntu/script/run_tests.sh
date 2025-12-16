#!/bin/bash
set -euo pipefail

# Run automation tests only (assumes build/env already prepared)

# Use a unique var name to avoid clobbering by sourced env scripts
RUN_TESTS_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
# Repo root is five levels up from script dir
REPO_ROOT="$(cd "${RUN_TESTS_DIR}/../../../../.." && pwd)"
cd "${REPO_ROOT}/automation"

# Load centralized env (sets JAVA_BUILD/HADOOP, GPHD_ROOT, PGPORT, etc.)
source "${RUN_TESTS_DIR}/pxf-env.sh"
source "${RUN_TESTS_DIR}/utils.sh"

# Test-related defaults (kept close to test runner)
export GROUP=${GROUP:-smoke}
export RUN_TESTS=${RUN_TESTS:-true}
export PXF_SKIP_TINC=${PXF_SKIP_TINC:-false}
export EXCLUDED_GROUPS=${EXCLUDED_GROUPS:-}
# Keep test data on HDFS between classes to avoid missing inputs
export PXF_TEST_KEEP_DATA=${PXF_TEST_KEEP_DATA:-true}

# Hadoop/Hive/HBase env
export JAVA_HOME="${JAVA_HADOOP}"
export PATH="$JAVA_HOME/bin:$PATH"
source "${GPHD_ROOT}/bin/gphd-env.sh"

# Force local PostgreSQL to IPv4 to avoid ::1 pg_hba misses in proxy tests
export PGHOST=127.0.0.1
# Match historical float string output used by expected files
export PGOPTIONS=${PGOPTIONS:-"-c extra_float_digits=0"}

# Ensure Cloudberry env if present
[ -f "/usr/local/cloudberry-db/cloudberry-env.sh" ] && source /usr/local/cloudberry-db/cloudberry-env.sh
[ -f "/home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh" ] && source /home/gpadmin/workspace/cloudberry/gpAux/gpdemo/gpdemo-env.sh

# Add Hadoop/HBase/Hive bins
export HADOOP_HOME=${HADOOP_HOME:-${GPHD_ROOT}/hadoop}
export HBASE_HOME=${HBASE_HOME:-${GPHD_ROOT}/hbase}
export HIVE_HOME=${HIVE_HOME:-${GPHD_ROOT}/hive}
export PATH="${HADOOP_HOME}/bin:${HBASE_HOME}/bin:${HIVE_HOME}/bin:${PATH}"
export HADOOP_CONF_DIR=${HADOOP_CONF_DIR:-${HADOOP_HOME}/etc/hadoop}
export YARN_CONF_DIR=${YARN_CONF_DIR:-${HADOOP_HOME}/etc/hadoop}
export HBASE_CONF_DIR=${HBASE_CONF_DIR:-${HBASE_HOME}/conf}
export HDFS_URI=${HDFS_URI:-hdfs://localhost:8020}
export HADOOP_OPTS="-Dfs.defaultFS=${HDFS_URI} ${HADOOP_OPTS:-}"
export HADOOP_CLIENT_OPTS="${HADOOP_OPTS}"
export MAVEN_OPTS="-Dfs.defaultFS=${HDFS_URI} ${MAVEN_OPTS:-}"

# Force Hive endpoints to localhost unless explicitly overridden (default sut points to cdw)
export HIVE_HOST=${HIVE_HOST:-localhost}
export HIVE_PORT=${HIVE_PORT:-10000}
export HIVE_SERVER_HOST=${HIVE_SERVER_HOST:-${HIVE_HOST}}
export HIVE_SERVER_PORT=${HIVE_SERVER_PORT:-${HIVE_PORT}}

# Run health check
health_check

# Simple wrappers per group - continue on failure to collect all results
smoke_test() {
  echo "[run_tests] Starting GROUP=smoke"
  make GROUP="smoke" || true
  save_test_reports "smoke"
  echo "[run_tests] GROUP=smoke finished"
}

hcatalog_test() {
  echo "[run_tests] Starting GROUP=hcatalog"
  make GROUP="hcatalog" || true
  save_test_reports "hcatalog"
  echo "[run_tests] GROUP=hcatalog finished"
}

hcfs_test() {
  echo "[run_tests] Starting GROUP=hcfs"
  make GROUP="hcfs" || true
  save_test_reports "hcfs"
  echo "[run_tests] GROUP=hcfs finished"
}

hdfs_test() {
  echo "[run_tests] Starting GROUP=hdfs"
  make GROUP="hdfs" || true
  save_test_reports "hdfs"
  echo "[run_tests] GROUP=hdfs finished"
}

hive_test() {
  echo "[run_tests] Starting GROUP=hive"
  make GROUP="hive" || true
  save_test_reports "hive"
  echo "[run_tests] GROUP=hive finished"
}

gpdb_test() {
  echo "[run_tests] Starting GROUP=gpdb"
  make GROUP="gpdb" || true
  save_test_reports "gpdb"
  echo "[run_tests] GROUP=gpdb finished"
}

# Save test reports for a specific group to avoid overwriting
save_test_reports() {
  local group="$1"
  local surefire_dir="${REPO_ROOT}/automation/target/surefire-reports"
  local artifacts_dir="${REPO_ROOT}/automation/test_artifacts"
  local group_dir="${artifacts_dir}/${group}"

  mkdir -p "$group_dir"

  if [ -d "$surefire_dir" ] && [ "$(ls -A "$surefire_dir" 2>/dev/null)" ]; then
    echo "[run_tests] Saving $group test reports to $group_dir"
    cp -r "$surefire_dir"/* "$group_dir/" 2>/dev/null || true
  else
    echo "[run_tests] No surefire reports found for $group"
  fi
}

# Generate test summary from surefire reports
generate_test_summary() {
  local artifacts_dir="${REPO_ROOT}/automation/test_artifacts"
  local summary_file="${artifacts_dir}/test_summary.json"

  mkdir -p "$artifacts_dir"

  echo "=== Generating Test Summary ==="

  local total_tests=0
  local total_failures=0
  local total_errors=0
  local total_skipped=0

  # Statistics by test group
  declare -A group_stats

  # Read from each test group directory
  for group_dir in "$artifacts_dir"/*; do
    [ -d "$group_dir" ] || continue
    
    local group=$(basename "$group_dir")
    # Skip if it's not a test group directory
    [[ "$group" =~ ^(smoke|hcatalog|hcfs|hdfs|hive|gpdb)$ ]] || continue

    echo "Processing $group test reports from $group_dir"
    
    local group_tests=0
    local group_failures=0
    local group_errors=0
    local group_skipped=0

    for xml in "$group_dir"/TEST-*.xml; do
      [ -f "$xml" ] || continue

      # Extract statistics from XML
      local tests=$(grep -oP 'tests="\K\d+' "$xml" | head -1 || echo "0")
      local failures=$(grep -oP 'failures="\K\d+' "$xml" | head -1 || echo "0")
      local errors=$(grep -oP 'errors="\K\d+' "$xml" | head -1 || echo "0")
      local skipped=$(grep -oP 'skipped="\K\d+' "$xml" | head -1 || echo "0")

      # Accumulate group statistics
      group_tests=$((group_tests + tests))
      group_failures=$((group_failures + failures))
      group_errors=$((group_errors + errors))
      group_skipped=$((group_skipped + skipped))
    done

    # Store group statistics
    group_stats[$group]="$group_tests,$group_failures,$group_errors,$group_skipped"

    # Accumulate totals
    total_tests=$((total_tests + group_tests))
    total_failures=$((total_failures + group_failures))
    total_errors=$((total_errors + group_errors))
    total_skipped=$((total_skipped + group_skipped))
  done

  local total_failed_cases=$((total_failures + total_errors))
  local total_passed=$((total_tests - total_failed_cases - total_skipped))

  # Generate JSON report
  echo "{" > "$summary_file"
  echo "  \"timestamp\": \"$(date -u +%Y-%m-%dT%H:%M:%SZ)\"," >> "$summary_file"
  echo "  \"overall\": {" >> "$summary_file"
  echo "    \"total\": $total_tests," >> "$summary_file"
  echo "    \"passed\": $total_passed," >> "$summary_file"
  echo "    \"failed\": $total_failed_cases," >> "$summary_file"
  echo "    \"skipped\": $total_skipped" >> "$summary_file"
  echo "  }," >> "$summary_file"
  echo "  \"groups\": {" >> "$summary_file"

  local first=true
  for group in "${!group_stats[@]}"; do
    IFS=',' read -r g_tests g_failures g_errors g_skipped <<< "${group_stats[$group]}"
    local g_failed=$((g_failures + g_errors))
    local g_passed=$((g_tests - g_failed - g_skipped))

    if [ "$first" = false ]; then
      echo "," >> "$summary_file"
    fi

    echo "    \"$group\": {" >> "$summary_file"
    echo "      \"total\": $g_tests," >> "$summary_file"
    echo "      \"passed\": $g_passed," >> "$summary_file"
    echo "      \"failed\": $g_failed," >> "$summary_file"
    echo "      \"skipped\": $g_skipped" >> "$summary_file"
    echo -n "    }" >> "$summary_file"
    first=false
  done

  echo "" >> "$summary_file"
  echo "  }" >> "$summary_file"
  echo "}" >> "$summary_file"

  # Print summary to console
  echo
  echo "=========================================="
  echo "PXF Automation Test Summary"
  echo "=========================================="
  echo "Total Tests: $total_tests"
  echo "Passed: $total_passed"
  echo "Failed: $total_failed_cases"
  echo "Skipped: $total_skipped"
  echo

  if [ ${#group_stats[@]} -gt 0 ]; then
    echo "Results by Group:"
    echo "----------------------------------------"
    printf "%-12s %6s %6s %6s %6s\n" "Group" "Total" "Pass" "Fail" "Skip"
    echo "----------------------------------------"

    for group in $(printf '%s\n' "${!group_stats[@]}" | sort); do
      IFS=',' read -r g_tests g_failures g_errors g_skipped <<< "${group_stats[$group]}"
      local g_failed=$((g_failures + g_errors))
      local g_passed=$((g_tests - g_failed - g_skipped))
      printf "%-12s %6d %6d %6d %6d\n" "$group" "$g_tests" "$g_passed" "$g_failed" "$g_skipped"
    done
    echo "----------------------------------------"
  fi

  echo "Test summary saved to: $summary_file"
  echo "=========================================="

  # Return 1 if any tests failed, 0 if all passed
  if [ $total_failed_cases -gt 0 ]; then
    echo "Found $total_failed_cases failed test cases"
    return 1
  else
    echo "All tests passed"
    return 0
  fi
}

main() {
  echo "[run_tests] Running all test groups..."

  # Run all test groups - continue on failure to collect all results
  smoke_test
  hcatalog_test
  hcfs_test
  hdfs_test
  hive_test
  gpdb_test
  echo "[run_tests] All test groups completed, generating summary..."

  # Generate test summary and return appropriate exit code
  generate_test_summary
}

main "$@"
