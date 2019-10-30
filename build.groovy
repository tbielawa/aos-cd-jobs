buildlib = load("pipeline-scripts/buildlib.groovy")
commonlib = buildlib.commonlib
logLevel = ""
dryRun = ""
artifacts = []

def initialize() {
    // buildlib.cleanWorkdir(mirrorWorking)
    if ( params.NOOP) {
	dryRun = "--dry-run=true"
	currentBuild.displayName += " [NOOP]"
    }

    if ( params.DEBUG ) {
	logLevel = " --loglevel=5 "
    }
}



return this
