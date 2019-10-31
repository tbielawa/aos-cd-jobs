#!/bin/bash
set -exuo pipefail

# RHCOS version, like 42.80.20190828.2
VERSION=${1}
# Where to put this on the mirror, such as'4.2' or 'pre-release'
RHCOS_MIRROR_PREFIX=${2}
# Plain text file with items to download
SYNCLIST=${3}
FORCE=0
# Put the items into this directory, we'll have to make it
DESTDIR="/srv/pub/openshift-v4/dependencies/rhcos/${RHCOS_MIRROR_PREFIX}/${VERSION}"

function checkDestDir() {
    if [ -d "${DESTDIR}" ]; then
	# Is this forced?
	if [ "${FORCE}" == "0" ]; then
	    echo "ERROR: Destination directory already exists and --force was not given"
	    echo "ERROR: Run this script again with the --force option to continue"
	    exit 1
	else
	    echo "INFO: Destination dir exists, will overwrite contents because --force was given"
	fi
    else
	echo "INFO: Destination dir does not exist, will create it"
	mkdir -p $DESTDIR
    fi
}

function downloadImages() {
    for img in $(<${SYNCLIST}); do
	curl --retry 5 -O $img
    done
}

function genSha256() {
    sha256sum * > sha256sum.txt
}

function updateSymlinks() {
    rm latest
    ln -s $VERSION latest
}

function mirror() {
    # Run mirroring push
    /usr/local/bin/push.pub.sh openshift-v4/dependencies/rhcos/${RHCOS_MIRROR_PREFIX} -v
}


checkDestDir
pushd $DESTDIR
downloadImages
genSha256
cd ..
updateSymlinks
popd
mirror
