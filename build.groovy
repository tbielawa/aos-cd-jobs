buildlib = load("pipeline-scripts/buildlib.groovy")
commonlib = buildlib.commonlib
logLevel = ""
dryRun = ""
artifacts = []
meta = "https://releases-rhcos-art.cloud.privileged.psi.redhat.com/storage/releases/rhcos-4.2/VERSION/meta.json"

def initialize() {
    meta = meta.replace("VERSION", params.RHCOS_BUILD)
    currentBuild.description += " Meta JSON: ${meta}"
    // buildlib.cleanWorkdir(mirrorWorking)
    if ( params.NOOP) {
	dryRun = "--dry-run=true"
	currentBuild.displayName += " [NOOP]"
    }

    sh("wget ${meta}")
}

def rhcosSyncGatherMeta() {


def rhcosSyncGenDocs() {
    sh("sh gen-docs.sh < meta.json > rhcos-${params.RHCOS_BUILD}.adoc")
}


return this
