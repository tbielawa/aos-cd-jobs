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
                    commonlib.ocpVersionParam('BUILD_VERSION', '4'),
                    [
                        name: 'RHCOS_MIRROR_PREFIX',
                        description: 'Where to place this release under https://mirror.openshift.com/pub/openshift-v4/dependencies/rhcos/',
                        $class: 'hudson.model.ChoiceParameterDefinition',
                        choices: (['pre-release'] + commonlib.ocp4Versions),
                    ],
                    [
                        name: 'RHCOS_BUILD',
                        description: 'ID of the RHCOS build to sync. e.g.: 42.80.20190828.2',
                        $class: 'hudson.model.StringParameterDefinition',
                        defaultValue: "",
                    ],
                    [
                        name: 'NOOP',
                        description: 'Run commands with their dry-run options enabled',
                        $class: 'BooleanParameterDefinition',
                        defaultValue: false,
                    ],
                    commonlib.suppressEmailParam(),
                    commonlib.mockParam(),
                ],
            ]
        ]
    )

    commonlib.checkMock()
    echo("Initializing RHCOS-${params.RHCOS_MIRROR_PREFIX} sync: #${currentBuild.number}")
    build.initialize()

    stage("Version dumps") {
        buildlib.doozer "--version"
        sh "which doozer"
        sh "oc version -o yaml"
    }

    try {
	stage("Gen AMI docs") { build.rhcosSyncGenDocs() }
    } catch ( err ) {
        commonlib.email(
            to: "aos-art-automation+failed-rhcos-sync@redhat.com",
            from: "aos-art-automation@redhat.com",
            replyTo: "aos-team-art@redhat.com",
            subject: "Error during OCP ${params.RHCOS_MIRROR_PREFIX} build sync",
            body: """
There was an issue running build-sync for OCP ${params.RHCOS_MIRROR_PREFIX}:

    ${err}
""")
        throw ( err )
    } finally {
        commonlib.safeArchiveArtifacts(build.artifacts)
    }
}
