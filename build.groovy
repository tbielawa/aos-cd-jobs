buildlib = load("pipeline-scripts/buildlib.groovy")
commonlib = buildlib.commonlib

// We'll update this later
elliottOpts = ""
advisoryOpt = "--use-default-advisory rpm"
errataList = ""
puddleURL = "http://download.lab.bos.redhat.com/rcm-guest/puddles/RHAOS/AtomicOpenShift-signed/${params.BUILD_VERSION}"
workdir = "puddleWorking"

def initialize(advisory) {
    buildlib.cleanWorkdir(workdir)
    elliottOpts += "--group=openshift-${params.BUILD_VERSION}"
    echo "${currentBuild.displayName}: https://errata.devel.redhat.com/advisory/${advisory}"
    errataList += buildlib.elliott("${elliottOpts} puddle-advisories", [capture: true]).trim().replace(' ', '')
    currentBuild.description = "Signed puddle for advisory https://errata.devel.redhat.com/advisory/${advisory}"
    currentBuild.description += "\nErrata whitelist: ${errataList}"
}

def signedComposeStateNewFiles() {
    buildlib.elliott("${elliottOpts} change-state --state NEW_FILES ${advisoryOpt}")
}

def signedComposeAttachBuilds() {
    def cmd = ''

    if ( params.BUILDS != '' ) {
	// You provided builds
	if ( params.SKIP_ADDING_BUILDS ) {
	    // But you accidentally clicked 'skip'
	    echo("Skip adding builds was requested but will be ignored because builds were provided manually")
	} else {
	    // Attach builds manually
	    cmd = "${elliottOpts} find-builds --kind rpm ${advisoryOpt} -b ${params.BUILDS}"
	}
    } else if ( params.SKIP_ADDING_BUILDS ) {
	// You didn't provide builds, and you want to skip them
	echo("Not updating builds")
    } else {
	// You didn't provide builds, and you didn't say to skip adding them
	cmd = "${elliottOpts} find-builds --kind rpm ${advisoryOpt}"
    }

    if ( cmd != '') {
	try {
	    def attachResult = buildlib.elliott(cmd, [capture: true]).trim().split('\n')[-1]

	} catch (err) {
	    echo("Problem running elliott")
	    error("Could not process attach builds command")
	}
    }
}

def signedComposeRpmdiffsRan(advisory) {
    commonlib.shell(
	// script auto-waits 60 seconds and re-retries until completed
	script: "./rpmdiff.py check-ran ${advisory}",
    )
}

def signedComposeRpmdiffsResolved(advisory) {
    echo "Action may be required: Complete any pending RPM Diff waivers to continue. Pending diffs will be printed."

    def result = commonlib.shell(
	script: "./rpmdiff.py check-resolved ${advisory}",
	returnAll: true
    )

    if (result.returnStatus != 0) {
	currentBuild.description += "\nRPM Diff resolution required"
	mailForResolution(result.stdout)
	// email people to have somebody take care of business
	def resp = input message: "Action required: Complete any pending RPM Diff waivers to continue",
        parameters: [
	    [
                $class: 'hudson.model.ChoiceParameterDefinition',
                choices: "CONTINUE\nABORT",
                name: 'action',
                description: 'CONTINUE if all RPM diffs have been waived. ABORT (terminate the pipeline) to stop this job.'
	    ]
        ]

        switch (resp) {
	    case "CONTINUE":
                echo("RPM Diffs are resolved. Continuing to make signed puddle")
                return true
	    default:
                error("User chose to abort pipeline after reviewing RPM Diff waivers")
        }
    }
}

def signedComposeStateQE() {
    buildlib.elliott("${elliottOpts} change-state --state QE ${advisoryOpt}")
}

def signedComposeRpmsSigned() {
    buildlib.elliott("${elliottOpts} poll-signed ${advisoryOpt}")
}

def signedComposeNewComposeEl7() {
    if ( params.DRY_RUN ) {
	currentBuild.description += "\nDry-run: EL7 Compose not actually built"
	echo("Skipping running puddle. Would have used whitelist: ${errataList}")
    } else {
	def cmd = "ssh ocp-build@rcm-guest.app.eng.bos.redhat.com sh -s ${buildlib.args_to_string(params.BUILD_VERSION, errataList)} < ${env.WORKSPACE}/build-scripts/rcm-guest/call_puddle_advisory.sh"
	def result = commonlib.shell(
	    script: cmd,
	    returnAll: true,
	)

	if ( result.returnStatus == 0 ) {
	    echo("View the package list here: ${puddleURL}")
	    echo("WE NEED TO CONSOLIDATE EMAILING OUT SUCCESS MESSAGES. ONLY 1 PLEASE")
	    // mailForSuccess()
	} else {
	    mailForFailure(result.combined)
	    error("Error running puddle command")
	}
    }
}

// Create a new signed RHEL8 puddle. This is a work-around of sorts.
//
// Due to limitations in 'puddle' (which is deprecated) we have to
// assemble a RHEL8 brew tag with signed packages MANUALLY. This
// script (ocp-tag-signed-errata-builds.py) was provided by Ryan
// Hartman from RCM. This replaces our former process (pre September
// 2019) wherein we would have to submit a ticket to RCM to have
// packages assembled into a constantly changing brew tag.
//
// Now, using this new process, we skip making a ticket. Instead, our
// build account (kerberos principal:
// ocp-build/buildvm.openshift.eng.bos.redhat.com@REDHAT.COM) has been
// given permission to add packages into the new tags for RHEL8.
def signedComposeNewComposeEl8() {
    def tagCmd = "./ocp-tag-signed-errata-builds.py --errata-group RHOSE-${params.BUILD_VERSION} --errata-group 'RHOSE ASYNC' --errata-product RHOSE --errata-product-version OSE-${params.BUILD_VERSION}-RHEL-8 --brew-pending-tag rhaos-${params.BUILD_VERSION}-rhel-8-image-build --verbose"

    if ( params.DRY_RUN ) {
	currentBuild.description += "\nDry-run: EL8 Compose not actually built"
	echo("Packages which would have been added to rhaos-${params.BUILD_VERSION}-rhel-8-image-build tag:")
	def testTagCmd = cmd + " --test"
	def tagResult = commonlib.shell(
	    script: testTagCmd,
	    returnAll: true,
	)

	if ( tagResult.returnStatus == 0 ) {
	    echo(tagResult.stdout)
	} else {
	    echo()
	    error("""Error running test tag assembly:
----------------------------------------
${tagResult.combined}
----------------------------------------
""")
	    // Dry run, don't email out
	}
    } else {
	echo("Updating RHEL8 brew tag")
	def tagResult = commonlib.shell(
	    script: tagCmd,
	    returnAll: true,
	)

	if ( tagResult.returnStatus == 0 ) {
	    echo(tagResult.stdout)
	} else {
	    mailForFailure(tagResult.combined)
	    error("""Error running tag assembly:
----------------------------------------
${tagResult.combined}
----------------------------------------
""")
	}

	echo("Building RHEL8 puddle")
	def puddleCmd = "ssh ocp-build@rcm-guest.app.eng.bos.redhat.com sh -s ${buildlib.args_to_string(params.BUILD_VERSION)} < ${env.WORKSPACE}/build-scripts/rcm-guest/call_puddle_advisory_el8.sh"
	def puddleResult = commonlib.shell(
	    script: puddleCmd,
	    returnAll: true,
	)

	if ( puddleResult.returnStatus == 0 ) {
	    echo("View the package list here: ${puddleURL}-el8")
	} else {
	    mailForFailure(puddleResult.combined)
	    error("Error running puddle command")
	}
    }
}

def mailForSuccess() {
    def puddleMetaEl7 = analyzePuddleLogs()
    def puddleMetaEl8 = analyzePuddleLogs(dist='-el8')
    def successMessage = """New signed composes created for OpenShift ${params.BUILD_VERSION}

  Errata Whitelist included advisories: ${errataList}
  EL7 Puddle URL: ${puddleMetaEl7.newPuddle}
  EL8 Puddle URL: ${puddleMetaEl8.newPuddle}
  Jenkins Console Log: ${commonlib.buildURL('console')}

Puddle Changelog EL7:
######################################################################
${puddleMetaEl7.changeLog}
######################################################################

Puddle Changelog EL8:
######################################################################
${puddleMetaEl8.changeLog}
######################################################################

"""

    echo("Mailing success message: ")
    echo(successMessage)

    commonlib.email(
        // to: "aos-art-automation@redhat.com",
        to: params.MAIL_LIST_SUCCESS,
        from: "aos-art-automation+new-signed-compose@redhat.com",
        replyTo: "aos-team-art@redhat.com",
        subject: "New signed compose for OpenShift ${params.BUILD_VERSION} job #${currentBuild.number}",
        body: successMessage,
    )
}

// @param <String> err: Error message from failed command
def mailForFailure(String err) {
    def failureMessage = """Error creating new signed compose OpenShift ${params.BUILD_VERSION}

  Errata Whitelist included advisories: ${errataList}
  Jenkins Console Log: ${commonlib.buildURL('console')}

######################################################################
${err}
######################################################################

"""

    echo("Mailing failure message: ")
    echo(failureMessage)

    commonlib.email(
        // to: "aos-art-automation@redhat.com",
        to: params.MAIL_LIST_FAILURE,
        from: "aos-art-automation+failed-signed-compose@redhat.com",
        replyTo: "aos-team-art@redhat.com",
        subject: "Error creating new signed compose for OpenShift ${params.BUILD_VERSION} job #${currentBuild.number}",
        body: failureMessage,
    )
}

def mailForResolution(diffs) {
    def diffMessage = """
Manual RPM Diff resolution is required for the generation of an
ongoing signed-compose. Please review the RPM Diffs below and resolve
them as soon as possible.

${diffs}

After the RPM Diffs have been resolved please return to the
in-progress compose job and choose the CONTINUE option.

    - Jenkins job: ${commonlib.buildURL('input')}
"""

    commonlib.email(
        // to: "aos-art-automation@redhat.com",
        to: params.MAIL_LIST_FAILURE,
        from: "aos-art-automation+rpmdiff-resolution@redhat.com",
        replyTo: "aos-team-art@redhat.com",
        subject: "RPM Diffs require resolution for signed compose: ${currentBuild.number}",
        body: diffMessage,
    )
}

// ######################################################################
// Some utility functions

// Check if there are any builds that *need* or *can* be
// attached.
//
// XXX: DOESN'T CONSIDER MANUALLY PROVIDED BUILDS YET
def thereAreBuildsToAttach() {
    def cmd = "${elliottOpts} find-builds --kind rpm ${advisoryOpt}"
    def attachResult = buildlib.elliott(cmd, [capture: true]).trim().split('\n')[-1]
    echo("Attach result: ${attachResult}")
    if ( attachResult == "No builds needed to be attached" ) {
	return true
    } else {
	return false
    }
}

// // Check if builds are signed
// def buildsSigned(String forAdvisory) {
//     def result = commonlib.shell(
// 	script: "elliott ${elliottOpts} poll-signed --advisory ${forAdvisory}",
// 	returnAll: true,
//     )
// }

// ######################################################################

// Get data from the logs of the newly created puddle. Will download
// the puddle.log and changelog.log files for archiving. Uses the
// global `puddleURL` variable to get the initial log. This log is
// parsed to identify the unique tag (`latestTag`) of the new puddle.
//
// @params <String> dist: Prefixed with a hyphen '-', a short
// distribution name. For example: '-el8'. Absent (default) will pull
// standard puddle logs, which are el7.
//
// Return <Object> (map) with keys:
// - String changeLog: The full changelog
// - String puddleLog: The full build log
// - String latestTag: The YYYY-MM-DD.i tag of the puddle, where 'i'
//   is a monotonically increasing integer
// - String newPuddle: Full URL to the new puddle base directory
def analyzePuddleLogs(dist='') {
    dir(workdir) {
	// Get the generic 'latest', it will tell us the actual name of this new puddle
	commonlib.shell("wget ${puddleURL}${dist}/latest/logs/puddle.log -O puddle${dist}.log")
	// This the tag of our newly created puddle
	def latestTag = commonlib.shell(
	    script: "awk -n '/now points to/{print \$NF}' puddle${dist}.log",
	    returnStdout: true,
	).trim()

	currentBuild.displayName += " [${latestTag}]"
	currentBuild.description += "\nTag${dist}: ${latestTag}"

	// Form the canonical URL to our new puddle
	def newPuddle = "${puddleURL}${dist}/${latestTag}"
	// Save the changelog for emailing out
	commonlib.shell("wget ${newPuddle}/logs/changelog.log -O changelog${dist}.log")

	return [
	    changeLog: readFile("changelog${dist}.log"),
	    puddleLog: readFile("puddle${dist}.log"),
	    latestTag: latestTag,
	    newPuddle: newPuddle,
	]
    }
}


return this
