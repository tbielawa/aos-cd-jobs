#!/bin/bash

# RHCOS version, like 42.80.20190828.2
VERSION=${1}
# Where to put this on the mirror, such as'4.2' or 'pre-release'
RHCOS_MIRROR_PREFIX=${2}
# Plain text file with items to download
SYNC=${3}

# Put the items into this directory, we'll have to make it
DESTDIR="/srv/pub/openshift-v4/dependencies/rhcos/${RHCOS_MIRROR_PREFIX}/${VERSION}"
