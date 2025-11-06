/**
 * ███╗   ███╗███████╗████████╗████████╗██╗     ███████╗ ██████╗██╗
 * ████╗ ████║██╔════╝╚══██╔══╝╚══██╔══╝██║     ██╔════╝██╔════╝██║
 * ██╔████╔██║█████╗     ██║      ██║   ██║     █████╗  ██║     ██║
 * ██║╚██╔╝██║██╔══╝     ██║      ██║   ██║     ██╔══╝  ██║     ██║
 * ██║ ╚═╝ ██║███████╗   ██║      ██║   ███████╗███████╗╚██████╗██║
 * ╚═╝     ╚═╝╚══════╝   ╚═╝      ╚═╝   ╚══════╝╚══════╝ ╚═════╝╚═╝
 * MettleCI DevOps for DataStage       (C) 2021-2025 Data Migrators
 *
 * This workflow is a template for running compliance tests in a DataStage environment using the MettleCI CLI.
 * It is designed to be called from other workflows and requires specific inputs to function correctly.
 * See https://docs.mettleci.io/?q=compliance-namespace
 */

def call(
    def COMPLIANCE_REPO_CREDENTIALS,
    def COMPLIANCE_REPO_URL,
    def COMPLIANCE_REPO_BRANCH,
    def INCLUDETAGS,
    def EXCLUDETAGS,
    def TESTSUITENAME,
    def CONTINUEONFAIL
) {
    def includeTagOption = "${((INCLUDETAGS.length()) > 0)?" -include-tags ${INCLUDETAGS}":""}"
    def excludeTagOption = "-exclude-tags example${((EXCLUDETAGS.length()) > 0)?",${EXCLUDETAGS}":""}"

    // Perform a 'git checkout' of a remote repository which is NOT the repository from
    // which this pipeline code was sourced.
    checkout(
        changelog: false,
        poll: false,
        scm: [  
            $class: 'GitSCM', 
            branches: [[name: "${COMPLIANCE_REPO_BRANCH}"]],
            doGenerateSubmoduleConfigurations: false, 
            extensions: [[$class: 'RelativeTargetDirectory', relativeTargetDir: 'compliance']],
            submoduleCfg: [], 
            userRemoteConfigs: [[ credentialsId: "${COMPLIANCE_REPO_CREDENTIALS}", url:  "${COMPLIANCE_REPO_URL}" ]]
        ])

    /**
     * Executes the 'mettleci compliance test' command to test your repository's DataStage
     * jobs against your Compliance rules.
     * Note that 'mettleci compliance test' is not the same as 'mettleci compliance query'.
     * See the documentation for more details.
     * https://docs.mettleci.io/compliance-namespace
     */
    try {
        // See https://docs.mettleci.io/compliance-test-command
        bat label: "Compliance Test - ${TESTSUITENAME}",
            script: """
                %AGENTMETTLECMD% compliance test ^
                    -assets datastage ^
                    -report \"compliance_report_${TESTSUITENAME}.xml\" ^
                    -junit -test-suite \"${TESTSUITENAME}\" ^
                    ${((CONTINUEONFAIL as boolean) == true)? '-ignore-test-failures':''}  ^
                    -rules compliance ^
                    ${includeTagOption}  ^
                    ${excludeTagOption}  ^
                    -project-cache \"%AGENTMETTLEHOME%\\cache\\%IISENGINENAME%\\%DATASTAGE_PROJECT%\"
                """
    } finally {
        // Publish JUnit XML compilation test output files to Jenkins (if enabled by the relevant parameter)
        if (findFiles(glob: "compliance_report_${TESTSUITENAME}.xml").length > 0) {
            junit testResults: "compliance_report_${TESTSUITENAME}.xml",
                allowEmptyResults: true,
                skipMarkingBuildUnstable: (CONTINUEONFAIL as boolean),
                skipPublishingChecks: true
        }
    }
}
