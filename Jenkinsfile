node {
    properties(
        [
            buildDiscarder(
                logRotator(
                    artifactDaysToKeepStr: '',
                    artifactNumToKeepStr: '',
                    daysToKeepStr: '',
                    numToKeepStr: '360'
                )
            ),
            [
                $class : 'ParametersDefinitionProperty',
                parameterDefinitions: [
                    [
                        name: 'MOCK',
                        description: 'Mock run to pickup new Jenkins parameters?',
                        $class: 'hudson.model.BooleanParameterDefinition',
                        defaultValue: false,
                    ],
                    [
                        name: 'DRY_RUN',
                        description: "Don't change anything, just detect the current enforcement state",
                        $class: 'hudson.model.BooleanParameterDefinition',
                        defaultValue: false,
                    ],
                    [
                        name: 'DISABLE',
                        description: "Temporarily lisable the firewall. The firewall is automatically enforced every 8 hours",
                        $class: 'hudson.model.BooleanParameterDefinition',
                        defaultValue: false,
                    ],
                ]
            ],
            disableResume(),
            disableConcurrentBuilds()
        ]
    )
    checkout scm
    def buildlib = load( "pipeline-scripts/buildlib.groovy" )
    def commonlib = buildlib.commonlib
    def slacklib = commonlib.slacklib
    commonlib.describeJob("enforce-firewall", """
        <h2>Automatically re-enables the firewall</h2>
        <b>Timing</b>: The scheduled job of the same name runs this twice daily.
        Checks if the firewall rules are enforcing. If they are not
        then they will be reapplied. If the rules are reapplied by
        this job then a notification will be sent over slack to the
        <code>#team-art</code> channel.

        Job supports a few parameters:
        <ul>
          <li><b>DRY_RUN</b> - Only <b>check</b> if the rules are presently enforcing</li>
          <li><b>DISABLE</b> - Temporarily turn off the firewall</li>
        </ul>
    """)
    commonlib.checkMock()
    needApplied = false
    reapplied = false
    disabled = false
    if ( !env.JOB_NAME.startsWith("aos-cd-builds/") ) {
        notifyChannel = '#team-art'
    } else {
        notifyChannel = '#art-bot-test'
    }

    // ######################################################################
    // Check if the firewall rules are presently enforcing. If they
    // are enforcing then we should not be able to query random hosts
    // not on the allowed list.
    stage ("Check state") {
        // No reason to check the state if we're just going to turn it off
        if ( !DISABLE ) {
            try {
                def extAccess = httpRequest(responseHandle: 'NONE',
                                            url: 'https://www.yahoo.com',
                                            timeout: 15)
                needApplied = true
                if ( params.DRY_RUN ) {
                    currentBuild.displayName = "[NOOP] - Needs enforcing"
                } else {
                    currentBuild.displayName = "Needs enforcing"
                }
            } catch (ex) {
                echo "Firewall is already enforcing"
                if ( params.DRY_RUN ) {
                    currentBuild.displayName = "[NOOP] - Already enforcing"
                } else {
                    currentBuild.displayName = "Already enforcing"
                }
            }
        } else {
            echo "This is a 'disable' request, skipping the enforcement check"
        }
    }

    stage ("Maybe apply/clean") {
        if ( DISABLE ) {
            if ( !params.DRY_RUN ) {
                echo "The firewall will be disabled now"
                commonlib.shell(
                    script: "sudo hacks/iptables/buildvm-scripts/canttouchthat.py --clean"
                )
                disabled = true
            } else {
                echo "The firewall rules would have been cleaned"
            }
        } else {
            if ( needApplied && !params.DRY_RUN ) {
                echo "Firewall is presently disabled, fix that now"
                commonlib.shell(
                    script: "sudo hacks/iptables/buildvm-scripts/canttouchthat.py -n hacks/iptables/buildvm-scripts/known-networks.txt --enforce"
                )
                reapplied = true
            } else {
                echo "Firewall is already enabled (or this is a dry run), nothing to do"
            }
        }
    }

    // ######################################################################
    // Notify art team if the rules had to be reapplied AND NO_SLACK is false
    stage ("Notify team of enforcement") {
        if ( DISABLE ) {
            if ( disabled && !params.DRY_RUN) {
                currentBuild.displayName = "Cleared the rules"
                slackChannel = slacklib.to(notifyChannel)
                slackChannel.say(':alert: The firewall rules have been cleared on buildvm :alert:')
            } else {
                echo "Skipping slack notification because..."
                echo "The rules would have been cleaned, however, you requested a DRY RUN"
            }
        } else {
            if ( reapplied && !params.DRY_RUN ) {
                currentBuild.displayName = "Enforced the rules"
                slackChannel = slacklib.to(notifyChannel)
                slackChannel.say(':itsfine-fire: The firewall rules have been reapplied to the buildvm :itsfine-fire:')
            } else {
                echo "Skipping slack notification because..."
                echo "The rules were already applied or this was a dry run"
            }
        }
    }
}
