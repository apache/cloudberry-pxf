#!/usr/bin/env bash

set -e

: "${SOURCE_BUCKET:?SOURCE_BUCKET must be set}"
: "${TARGET_BUCKET:?TARGET_BUCKET must be set}"
: "${VERSIONS_BEFORE_LATEST:?VERSIONS_BEFORE_LATEST is required}" # zero-based index
: "${GPDB_VERSION:?GPDB_VERSION is required}" # major version number, e.g. "6"
: "${PLATFORMS:?PLATFORMS is required}" # a list of platforms separated by pipes, e.g. "el7|el8|el9"
: "${PKG_TYPES:?PKG_TYPES is required}" # a list of package types separated by pipes, e.g. "rpm|deb"
: "${GOOGLE_CREDENTIALS:?GOOGLE_CREDENTIALS must be set}"

source_folder="gs://${SOURCE_BUCKET}/server/released/gpdb${GPDB_VERSION}"
echo "Source GCS folder:${source_folder}"

target_folder="gs://${TARGET_BUCKET}/latest-${VERSIONS_BEFORE_LATEST}_gpdb${GPDB_VERSION}"
echo "Target GCS folder: ${target_folder}"

echo "Authenticating with Google service account..."
gcloud auth activate-service-account --key-file=<(echo "${GOOGLE_CREDENTIALS}") >/dev/null 2>&1

# beta versions, release candidates, special releases, unsupported platforms will be ignored
package_regex="greenplum-db-${GPDB_VERSION}\.[0-9]+\.[0-9]+-(${PLATFORMS})-(.*).(${PKG_TYPES})$"
gpdb_artifact_output=$(gsutil ls "${source_folder}/" | grep -E "${package_regex}")

# sort reversely (-r), with unique items (-u), and version sort (-V)
versions=$(echo "${gpdb_artifact_output}" | grep -oE 'greenplum-db-[0-9]+\.[0-9]+\.[0-9]+' | awk -F'-' '{print $3}' | sort -ruV)
# remove the dummy x.99.99 from the version list
versions=$(echo "${versions}" | grep -v "${GPDB_VERSION}\.99\.99")

# count the number of available versions
num_gpdb_versions=$(echo "${versions}" | wc -l)
echo "Number of versions: ${num_gpdb_versions}"

	if [[ -z "${id}" ]]; then
		echo "Did not find '${file}' in product files for GPDB '${gpdb_version}'"

		case "${file}" in
			*rhel7*) existing_file="$(find ${product_dirs[$i]}/ -name *rhel7*.rpm)" ;;
			*rhel8*) existing_file="$(find ${product_dirs[$i]}/ -name *rhel8*.rpm)" ;;
			*ubuntu18*) existing_file="$(find ${product_dirs[$i]}/ -name *ubuntu18*.deb)" ;;
			*)
				echo "Unexpected file: ${file}"
				exit 1;;
		esac

		echo "Keeping existing file: ${existing_file}"
		continue
	fi
	echo "Cleaning ${product_dirs[$i]} and downloading ${file} with id ${id} to ${product_dirs[$i]}..."
	rm -f "${product_dirs[$i]}"/*.{rpm,deb}
	pivnet download-product-files \
		"--download-dir=${product_dirs[$i]}" \
		"--product-slug=${PRODUCT_SLUG}" \
		"--release-version=${gpdb_version}" \
		"--product-file-id=${id}" >/dev/null 2>&1 &
	pids+=($!)
done

# get the `VERSIONS_BEFORE_LATEST`-th (zero-based index) latest version
target_version=$(echo "${versions}" | awk -v i=${VERSIONS_BEFORE_LATEST} 'NR == i+1')
echo "The ${VERSIONS_BEFORE_LATEST}-th latest version is: ${target_version}"

# escape the dots in the version number for regex
version_regex="${target_version//./\\.}"

# find the packages of the version
versioned_package_regex="greenplum-db-${version_regex}-(${PLATFORMS})-(.*).(${PKG_TYPES})$"
packages_to_copy=$(echo "${gpdb_artifact_output}" | grep -E "${versioned_package_regex}")

while IFS= read -r package_path; do
  package_name=$(basename "${package_path}")
  target_package_path="${target_folder}/${package_name}"

  echo "Copying GPDB release package from ${package_path} to ${target_package_path}..."
  # gsutil automatically performs hash validation when uploading or downloading files
  # Reference: https://cloud.google.com/storage/docs/gsutil/commands/hash
  gsutil cp "${package_path}" "${target_package_path}"
done <<< "${packages_to_copy}"
