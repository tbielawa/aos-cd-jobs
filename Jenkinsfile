#!/usr/bin/env groovy

node {
    checkout scm
    def build = load("build.groovy")
    def buildlib = build.buildlib
    def commonlib = build.commonlib

    properties(
        [
            buildDiscarder(
                logRotator(
                    artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '',
                    numToKeepStr: ''
                )
            ),
            [
                $class : 'ParametersDefinitionProperty',
                parameterDefinitions: [
                    commonlib.suppressEmailParam(),
                    [
                        name: 'MAIL_LIST_SUCCESS',
                        description: '(Optional) Success Mailing List',
                        $class: 'hudson.model.StringParameterDefinition',
                        defaultValue: "tbielawa@redhat.com",
                    ],
                    [
                        name: 'MAIL_LIST_FAILURE',
                        description: 'Failure Mailing List',
                        $class: 'hudson.model.StringParameterDefinition',
                        defaultValue: "tbielawa@redhat.com",
                        // 'aos-art-automation+failed-signed-puddle@redhat.com'
                    ],
                    [
                        name: 'BUILDS',
                        description: 'Optional: Only attach these brew builds (accepts numeric id or NVR)\nComma separated list\nOverrides SKIP_ADDING_BUILDS',
                        $class: 'hudson.model.StringParameterDefinition',
                        defaultValue: ""
                    ],
                    [
                        name: 'SKIP_ADDING_BUILDS',
                        description: 'Do not bother adding more builds\nfor example: if you are already satisfied with what is already attached and just need to run the rpmdiff/signing process',
                        $class: 'BooleanParameterDefinition',
                        defaultValue: false
                    ],
                    // [
                    //     name: 'ONLY_RUN_PUDDLE',
                    //     description: 'Do not run any stages other than the puddle stage',
                    //     $class: 'BooleanParameterDefinition',
                    //     defaultValue: false
                    // ],
                    [
                        name: 'DRY_RUN',
                        description: 'Do not update the puddle. Just show what would have happened',
                        $class: 'BooleanParameterDefinition',
                        defaultValue: false
                    ],
                    commonlib.mockParam(),
                    commonlib.ocpVersionParam('BUILD_VERSION'),
                ]
            ],
            disableConcurrentBuilds(),
        ]
    )

    commonlib.checkMock()
    def advisory = buildlib.elliott("--group=openshift-${params.BUILD_VERSION} get --use-default-advisory rpm --id-only", [capture: true]).trim()
    // Don't run majority of steps, such as if no builds to attach
    def skipBusiness = false

    stage("Initialize") {
	buildlib.elliott "--version"
	buildlib.kinit()
	currentBuild.displayName = "#${currentBuild.number} OCP ${params.BUILD_VERSION}"
	build.initialize(advisory)
    }

    if ( build.thereAreBuildsToAttach() ) {
	echo("Builds to attach, must run all steps")
	skipBusiness = false
    } else {
	echo("Nothing to attach, will skip steps")
	skipBusiness = true
    }

    try {
	sshagent(["openshift-bot"]) {
	    if ( !skipBusiness ) {
		stage("Advisory is NEW_FILES") { build.signedComposeStateNewFiles() }
		stage("Attach builds") { build.signedComposeAttachBuilds() }
		stage("RPM diffs ran") { build.signedComposeRpmdiffsRan(advisory) }
		stage("RPM diffs resolved") { build.signedComposeRpmdiffsResolved(advisory) }
		stage("Advisory is QE") { build.signedComposeStateQE() }
		stage("Signing completing") { build.signedComposeRpmsSigned() }
	    }
	    // stage("New el7 compose") { build.signedComposeNewComposeEl7() }
	    // Ensure the rhel8 tag script can read the required cert
	    withEnv(['REQUESTS_CA_BUNDLE=/etc/pki/tls/certs/ca-bundle.crt']) {
		stage("New el8 compose") { build.signedComposeNewComposeEl8() }
	    }
	}
	build.mailForSuccess()
    } catch (err) {
        currentBuild.description += "\n-----------------\n\n${err}\n-----------------\n"
	currentBuild.result = "FAILURE"

	if (params.MAIL_LIST_FAILURE.trim()) {
	    commonlib.email(
		to: params.MAIL_LIST_FAILURE,
		from: "aos-art-automation+failed-signed-compose@redhat.com",
		replyTo: "aos-team-art@redhat.com",
		subject: "Error building OCP Signed Puddle ${params.BUILD_VERSION}",
		body:
		    """\
Pipeline build "${currentBuild.displayName}" encountered an error:
${currentBuild.description}
View the build artifacts and console output on Jenkins:
    - Jenkins job: ${commonlib.buildURL()}
    - Console output: ${commonlib.buildURL('console')}
"""
	    )
	}
	throw err  // gets us a stack trace FWIW
    } finally {
	commonlib.safeArchiveArtifacts([
		'email/*',
		'shell/*',
		"${build.workdir}/changelog*.log",
		"${build.workdir}/puddle*.log",
	    ]
	)
    }


    // ######################################################################
    // Email results

    //
    // ######################################################################
}
