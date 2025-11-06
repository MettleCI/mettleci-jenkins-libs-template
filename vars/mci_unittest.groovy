/**
 * ███╗   ███╗███████╗████████╗████████╗██╗     ███████╗ ██████╗██╗
 * ████╗ ████║██╔════╝╚══██╔══╝╚══██╔══╝██║     ██╔════╝██╔════╝██║
 * ██╔████╔██║█████╗     ██║      ██║   ██║     █████╗  ██║     ██║
 * ██║╚██╔╝██║██╔══╝     ██║      ██║   ██║     ██╔══╝  ██║     ██║
 * ██║ ╚═╝ ██║███████╗   ██║      ██║   ███████╗███████╗╚██████╗██║
 * ╚═╝     ╚═╝╚══════╝   ╚═╝      ╚═╝   ╚══════╝╚══════╝ ╚═════╝╚═╝
 * MettleCI DevOps for DataStage       (C) 2021-2025 Data Migrators
 *
 * This workflow is used to deploy and execute DataStage unit tests to a DataStage server.
 * It is designed to be called from other workflows, such as the main 'JenkinsFile_*' 
 * provided with this library.  The workflow uses the MettleCI CLI to perform the deployment
 * and configuration of DataStage unit test assets and accepts several inputs, including the 
 * DataStage project name, environment name, and other parameters.
 *
 * This workflow also includes steps to fetch and merge DSParams files, configure properties, 
 * and execute arbitrary user-specified deployment scripts.
 *
 * Finally, the workflow includes steps to publish compilation test results, if specified.
 * The workflow is designed to be flexible and reusable, allowing for easy integration into
 * different deployment workflows.
 *
 * The steps in this workflow include:
 * 1. Configure properties for the DataStage project.
 * 2. Upload unit test specifications to the DataStage server.
 * 3. Execute the unit tests on the DataStage server.
 * 4. Download the unit test reports from the DataStage server.
 * 5. Publish the unit test reports to Jenkins.
 * 6. Clean up the reports directory.
 */

def call(
    def ENVIRONMENTNAME,
    def USE_SSH_KEY = false,
    def USE_SSH_PASSPHRASE = false
) {
    def SSHPassPhrase = "${((USE_SSH_PASSPHRASE.toBoolean()) == true)?" -passphrase \"%SSHKEYPHRASE%\"":""}"

    // Configure properties for the unit test execution.
    // See https://docs.mettleci.io/properties-config-command
    bat label: 'Configure Properties', 
        script: '''
            %AGENTMETTLECMD% properties config ^
            -baseDir datastage ^
            -filePattern "cleanup_unittest.sh" ^
            -properties varfiles/var.%ENVID% ^
            -outDir config
        '''

    if (USE_SSH_KEY.toBoolean() == true) {
        // Clean up any previous unit test artifacts by invoking the 'cleanup_unittest.sh' script
        // (using SSH authentication)
        // See https://docs.mettleci.io/remote-execute-command
        bat label: 'Cleanup', 
            script: """
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -script "config/cleanup_unittest.sh"
            """

        // Uploads unit test specifications and their associated data to the specified engine
        // (using SSH authentication)
        // See https://docs.mettleci.io/remote-upload-command
        bat label: 'Upload unit test specs and data', 
            script: """
                %AGENTMETTLECMD% remote upload ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -source unittest ^
                -transferPattern "**/*" ^
                -destination %ENGINEUNITTESTBASEDIR%/specs/%DATASTAGE_PROJECT%
            """
    } else {
        // Clean up any previous unit test artifacts by invoking the 'cleanup_unittest.sh' script
        // (using username/password authentication)
        // See https://docs.mettleci.io/remote-execute-command
        bat label: 'Cleanup', 
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -password %MCIPASSWORD% ^
                -script "config/cleanup_unittest.sh"
            '''

        // Uploads unit test specifications and their associated data to the specified engine
        // (using username/password authentication)
        // See https://docs.mettleci.io/remote-upload-command
        bat label: 'Upload unit test specs and data', 
            script: '''
                %AGENTMETTLECMD% remote upload ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -password %MCIPASSWORD% ^
                -source unittest ^
                -transferPattern "**/*" ^
                -destination %ENGINEUNITTESTBASEDIR%/specs/%DATASTAGE_PROJECT%
            '''
    }

    try {
        // Creates (or re-creates) a directory for unit test reports.
        // This is done to ensure that the directory is clean before running the tests.
        bat label: 'Clean reports directory',
            script: """
                if exist "unittest-reports" rmdir "unittest-reports" /S /Q
                mkdir "unittest-reports"
            """

        // Run the unit tests using the MettleCI CLI
        // See https://docs.mettleci.io/unittest-test-command
        bat label: 'Run Unit Tests', 
            script: """
                %AGENTMETTLECMD% unittest test ^
                -domain %IISDOMAINNAME% ^
                -server %IISENGINENAME% ^
                -username %IISUSERNAME% -password %IISPASSWORD% ^
                -project %DATASTAGE_PROJECT% ^
                -specs unittest ^
                -reports unittest-reports/%DATASTAGE_PROJECT% ^
                -project-cache \"%AGENTMETTLEHOME%\\cache\\%IISENGINENAME%\\%DATASTAGE_PROJECT%\" ^
                -test-suite \"MettleCI Unit Tests - ${ENVIRONMENTNAME}\"
            """
    }
    finally {
        // Download unit test results from the specified engine (using SSH authentication)
        // See https://docs.mettleci.io/remote-download-command
        if (USE_SSH_KEY.toBoolean() == true) {
            bat label: 'Download unit test reports', 
                script: """
                    %AGENTMETTLECMD% remote download ^
                    -host %IISENGINENAME% ^
                    -source %ENGINEUNITTESTBASEDIR%/reports ^
                    -destination unittest-reports ^
                    -transferPattern "%DATASTAGE_PROJECT%/**/*.xml" ^
                    -username %MCIUSERNAME% -privateKey \"%SSHKEYPATH%\" ^
                    ${SSHPassPhrase}
                """
        } else {
            // Download unit test results from the specified engine (using username/password authentication)
            // See https://docs.mettleci.io/remote-download-command
            bat label: 'Download unit test reports', 
                script: '''
                    %AGENTMETTLECMD% remote download ^
                    -host %IISENGINENAME% ^
                    -source %ENGINEUNITTESTBASEDIR%/reports ^
                    -destination unittest-reports ^
                    -transferPattern "%DATASTAGE_PROJECT%/**/*.xml" ^
                    -username %MCIUSERNAME% -password %MCIPASSWORD%
                '''
        }

        // Publish JUnit XML unit test output files to Jenkins (if enabled by the relevant parameter)
        if (findFiles(glob: "unittest-reports/${env.DATASTAGE_PROJECT}/**/*.xml").length > 0) {
            junit testResults: "unittest-reports/${env.DATASTAGE_PROJECT}/**/*.xml", 
                    allowEmptyResults: true,
                    skipPublishingChecks: true
        }
    }
}
