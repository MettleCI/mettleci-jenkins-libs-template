def call(
    def ENVIRONMENTNAME,
    def USE_SSH_KEY = false,
    def USE_SSH_PASSPHRASE = false
) {
    def SSHPassPhrase = "${((USE_SSH_PASSPHRASE.toBoolean()) == true)?" -passphrase \"%SSHKEYPHRASE%\"":""}"

    bat label: 'Configure Properties', 
        script: '''
            %AGENTMETTLECMD% properties config ^
            -baseDir datastage ^
            -filePattern "cleanup_unittest.sh" ^
            -properties varfiles/var.%ENVID% ^
            -outDir config
        '''

    if (USE_SSH_KEY.toBoolean() == true) {
        bat label: 'Cleanup', 
            script: """
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -script "config/cleanup_unittest.sh"
            """

        bat label: 'Upload unit test specs', 
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
        bat label: 'Cleanup', 
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% -password %MCIPASSWORD% ^
                -script "config/cleanup_unittest.sh"
            '''

        bat label: 'Upload unit test specs', 
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
        bat label: 'Clean reports directory', 
            script: """
                if exist "unittest-reports" rmdir "unittest-reports" /S /Q
                mkdir "unittest-reports"
            """

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

        if (findFiles(glob: "unittest-reports/${env.DATASTAGE_PROJECT}/**/*.xml").length > 0) {
            junit testResults: "unittest-reports/${env.DATASTAGE_PROJECT}/**/*.xml", 
                    allowEmptyResults: true,
                    skipPublishingChecks: true
        }
    }
}

