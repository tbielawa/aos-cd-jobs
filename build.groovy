buildlib = load("pipeline-scripts/buildlib.groovy")
commonlib = buildlib.commonlib
rhcosWorking = "${env.WORKSPACE}/rhcos_working"
logLevel = ""
dryRun = ""
artifacts = []
baseUrl = "https://releases-rhcos-art.cloud.privileged.psi.redhat.com/storage/releases/rhcos-%OCPVERSION%/%RHCOSBUILD%"
metaUrl = ""

def initialize() {
    buildlib.cleanWorkdir(rhcosWorking)
    // Sub in those vars
    baseUrl = baseUrl.replace("%OCPVERSION%", params.BUILD_VERSION)
    baseUrl = baseUrl.replace("%RHCOSBUILD%", params.RHCOS_BUILD)
    // Actual meta.json
    metaUrl = baseUrl + "/meta.json"

    currentBuild.displayName = "${params.RHCOS_BUILD} - ${params.RHCOS_MIRROR_PREFIX}"
    currentBuild.description = "Meta JSON: ${metaUrl}"

    if ( params.NOOP ) {
	dryRun = "--dry-run=true"
	currentBuild.displayName += " [NOOP]"
    }

    dir ( rhcosWorking ) {
	sh("wget ${metaUrl}")
    }
    artifacts.add("rhcos_working/meta.json")
}

def rhcosSyncPrintArtifacts() {
    def imageUrls = []
    dir ( rhcosWorking ) {
	def meta = readJSON file: 'meta.json', text: ''
	meta.images.eachWithIndex { name, value, i ->
	    echo("${value.path}")
	}
    }
}

def rhcosSyncGenDocs() {
    dir( rhcosWorking ) {
	sh("sh ../gen-docs.sh < meta.json > rhcos-${params.RHCOS_BUILD}.adoc")
    }
    artifacts.add("rhcos_working/rhcos-${params.RHCOS_BUILD}.adoc")
}


return this
