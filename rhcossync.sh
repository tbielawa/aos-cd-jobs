#!/bin/bash
# set -exo pipefail

# RHCOS version, like 42.80.20190828.2
VERSION=
# Where to put this on the mirror, such as'4.2' or 'pre-release'
RHCOS_MIRROR_PREFIX=
FORCE=0

function usage() {
    cat <<EOF
usage: ${0} [OPTIONS]

This script will create directories under
https://mirror.openshift.com/pub/openshift-v4/dependencies/rhcos/{--PREFIX}/{--VERSION}/
containing the items provided in --synclist as well as a sha256sum.txt
file generated from those items.

Required Options:

  --version        The RHCOS version to mirror (ex: 42.80.20190828.2)
  --prefix         The parent directory to mirror to (ex: 4.2/pre-release)
  --synclist       Path to the file of items (URLs) to mirror (whitespace separated)

Optional Options:

  --force          Overwrite existing contents if destination already exists
  --test           Test inputs, but ensure nothing can ever go out to the mirrors

Don't get tricky! --force and --test have no predictable result if you
combine them. Just don't try it.

EOF
}


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


if [ "${#}" -lt "6" ]; then
    echo "You are missing some required options"
    usage
    exit 1
else
    echo $@

fi

while [ $1 ]; do
    case "$1" in
	"--version")
	    shift
	    VERSION=$1;;
	"--prefix")
	    shift
	    RHCOS_MIRROR_PREFIX=$1;;
	"--synclist")
	    shift
	    SYNCLIST=$1;;
	"--force")
	    FORCE=1;;
	"--test")
	    TEST=1;;
	"-h" | "--help")
	    usage
	    exit 0;;
	*)
	    echo "Unrecognized option provided: '${1}', perhaps you need --help"
	    exit 1;;
    esac
    shift
done

# Put the items into this directory, we might have to make it
DESTDIR="/srv/pub/openshift-v4/dependencies/rhcos/${RHCOS_MIRROR_PREFIX}/${VERSION}"
echo $DESTDIR

echo $VERSION
echo $RHCOS_MIRROR_PREFIX
echo $SYNCLIST
echo $FORCE

exit 0


checkDestDir
pushd $DESTDIR
downloadImages
genSha256
cd ..
updateSymlinks
popd
mirror
