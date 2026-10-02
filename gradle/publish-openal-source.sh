#!/usr/bin/env bash

set -Eeuo pipefail

readonly OPENAL_VERSION="1.24.1"
readonly LWJGL_VERSION="3.3.6"
readonly SOURCE_REVISION="90191edd20bb877c5cbddfdac7ec0fe49ad93727"
readonly SOURCE_VERSION="${OPENAL_VERSION}-lwjgl-${LWJGL_VERSION}"
readonly ARTIFACT_NAME="openal-soft-source"
readonly FILE_NAME="${ARTIFACT_NAME}-${SOURCE_VERSION}.tar.gz"
readonly REPOSITORY_URL="https://repo.repsy.io/mvn/njoh/public"
readonly ARTIFACT_PATH="no/njoh/thirdparty/${ARTIFACT_NAME}/${SOURCE_VERSION}"
readonly SOURCE_URL="https://github.com/LWJGL-CI/openal-soft/archive/${SOURCE_REVISION}.tar.gz"

usage() {
    echo "Usage: $0 [--dry-run]"
    echo
    echo "Credentials are read from REPSY_USERNAME and REPSY_PASSWORD."
    echo "Missing values are requested interactively."
}

DRY_RUN=false
case "${1-}" in
    "") ;;
    --dry-run) DRY_RUN=true ;;
    -h|--help)
        usage
        exit 0
        ;;
    *)
        usage >&2
        exit 2
        ;;
esac

readonly TARGET_URL="${REPOSITORY_URL}/${ARTIFACT_PATH}/${FILE_NAME}"

if [[ "$DRY_RUN" == true ]]; then
    echo "Source: ${SOURCE_URL}"
    echo "Target: ${TARGET_URL}"
    exit 0
fi

for command in awk curl sha1sum sha256sum mktemp; do
    if ! command -v "$command" >/dev/null 2>&1; then
        echo "Required command not found: $command" >&2
        exit 1
    fi
done

REPSY_USERNAME="${REPSY_USERNAME-}"
REPSY_PASSWORD="${REPSY_PASSWORD-}"

if [[ -z "$REPSY_USERNAME" ]]; then
    read -r -p "Repsy username: " REPSY_USERNAME
fi

if [[ -z "$REPSY_PASSWORD" ]]; then
    read -r -s -p "Repsy password or deploy token: " REPSY_PASSWORD
    echo
fi

if [[ -z "$REPSY_USERNAME" || -z "$REPSY_PASSWORD" ]]; then
    echo "Repsy username and password or deploy token are required." >&2
    exit 1
fi

TEMPORARY_DIRECTORY="$(mktemp -d "${TMPDIR:-/tmp}/pulseengine-openal.XXXXXX")"
trap 'rm -rf -- "$TEMPORARY_DIRECTORY"' EXIT

readonly ARCHIVE_PATH="${TEMPORARY_DIRECTORY}/${FILE_NAME}"
readonly POM_NAME="${ARTIFACT_NAME}-${SOURCE_VERSION}.pom"
readonly POM_PATH="${TEMPORARY_DIRECTORY}/${POM_NAME}"

upload_file() {
    local path="$1"
    local name="$2"
    local content_type="$3"

    echo "Uploading ${name}"
    curl \
        --fail \
        --silent \
        --show-error \
        --user "${REPSY_USERNAME}:${REPSY_PASSWORD}" \
        --header "Content-Type: ${content_type}" \
        --upload-file "$path" \
        "${REPOSITORY_URL}/${ARTIFACT_PATH}/${name}"
}

echo "Downloading exact OpenAL Soft source revision ${SOURCE_REVISION}"
curl \
    --fail \
    --location \
    --silent \
    --show-error \
    --output "$ARCHIVE_PATH" \
    "$SOURCE_URL"

cat > "$POM_PATH" <<EOF
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>no.njoh.thirdparty</groupId>
  <artifactId>${ARTIFACT_NAME}</artifactId>
  <version>${SOURCE_VERSION}</version>
  <packaging>tar.gz</packaging>
  <name>OpenAL Soft source used by PulseEngine</name>
  <description>Corresponding source for the OpenAL Soft native binaries bundled by LWJGL ${LWJGL_VERSION}</description>
  <url>https://github.com/LWJGL-CI/openal-soft/commit/${SOURCE_REVISION}</url>
  <licenses>
    <license>
      <name>GNU Library General Public License version 2 or later</name>
      <url>https://www.gnu.org/licenses/old-licenses/lgpl-2.0.html</url>
      <distribution>repo</distribution>
    </license>
  </licenses>
  <scm>
    <url>https://github.com/LWJGL-CI/openal-soft</url>
    <tag>${SOURCE_REVISION}</tag>
  </scm>
</project>
EOF

sha1sum "$ARCHIVE_PATH" | awk '{print $1}' > "${ARCHIVE_PATH}.sha1"
sha256sum "$ARCHIVE_PATH" | awk '{print $1}' > "${ARCHIVE_PATH}.sha256"
sha1sum "$POM_PATH" | awk '{print $1}' > "${POM_PATH}.sha1"
sha256sum "$POM_PATH" | awk '{print $1}' > "${POM_PATH}.sha256"

upload_file "$ARCHIVE_PATH" "$FILE_NAME" "application/gzip"
upload_file "${ARCHIVE_PATH}.sha1" "${FILE_NAME}.sha1" "text/plain"
upload_file "${ARCHIVE_PATH}.sha256" "${FILE_NAME}.sha256" "text/plain"
upload_file "$POM_PATH" "$POM_NAME" "application/xml"
upload_file "${POM_PATH}.sha1" "${POM_NAME}.sha1" "text/plain"
upload_file "${POM_PATH}.sha256" "${POM_NAME}.sha256" "text/plain"

echo "Published ${TARGET_URL}"