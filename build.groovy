buildlib = load("pipeline-scripts/buildlib.groovy")
commonlib = buildlib.commonlib
rhcosWorking = "${env.WORKSPACE}/rhcos_working"
logLevel = ""
dryRun = ""
artifacts = []
meta = "https://releases-rhcos-art.cloud.privileged.psi.redhat.com/storage/releases/rhcos-%OCPVERSION%/%RHCOSVERSION%/meta.json"

def initialize() {
    meta = meta.replace("%OCPVERSION%", params.RHCOS_MIRROR_PREFIX)
    meta = meta.replace("%RHCOSVERSION%", params.RHCOS_BUILD)

    currentBuild.description += " Meta JSON: ${meta}"
    buildlib.cleanWorkdir(rhcosWorking)
    if ( params.NOOP) {
	dryRun = "--dry-run=true"
	currentBuild.displayName += " [NOOP]"
    }

    dir ( rhcosWorking ) {
	sh("wget ${meta}")
    }
}


def rhcosSyncGenDocs() {
    dir( rhcosWorking ) {
	sh("sh ../gen-docs.sh < meta.json > rhcos-${params.RHCOS_BUILD}.adoc")
    }
    artifacts.add("rhcos_working/rhcos-${params.RHCOS_BUILD}.adoc")
}


return this
