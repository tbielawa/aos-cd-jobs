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
    # Downloading
    #
    # Switch into DESTDIR
    #
    # run wget --tries=20 (normally it considers 'conn refused' and 'not
    # found' as unrecoverable), also use --waitretry=15 in case the remote
    # host is being flakey. Basically, give this every possible
    # opportunity to pass if something is flaking out.
    #
    # cat $SYNCLIST | xargs wget --options
    :
}


function genSha256() {
    # Gotta make that sha!
    :
}


function updateSymlinks() {
    :
    # Update symlinks
    #
    # go to parent dir. rm `latest`. ln -s $VERSION latest
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
