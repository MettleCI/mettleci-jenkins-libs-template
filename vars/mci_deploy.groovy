def call(
    def PUBLISHCOMPILATIONRESULTS,
    def UPGRADE_DSPARAMS,
    def PS_SPECIALHANDLING = false,
    def USE_SSH_KEY = false,
    def USE_SSH_PASSPHRASE = false,
    def BINARY_DEPLOYMENT = false
) {
    def PSPropertiesConfig = "${((PS_SPECIALHANDLING.toBoolean()) == true)?" -filePattern \"Parameter Sets/*/*\"":""}"
    def PSDeploy = "${((PS_SPECIALHANDLING.toBoolean()) == true)?" -parameter-sets \"config\\Parameter Sets\"":""}"

    def SSHPassPhrase = "${((USE_SSH_PASSPHRASE.toBoolean()) == true)?" -passphrase \"%SSHKEYPHRASE%\"":""}"

    bat label: 'Create DataStage Project', 
        script: '''
            %AGENTMETTLECMD% datastage create-project ^
            -domain %IISDOMAINNAME% ^
            -server %IISENGINENAME% ^
            -project %DATASTAGE_PROJECT% ^
            -username %IISUSERNAME% ^
            -password %IISPASSWORD%
        '''

    if ((UPGRADE_DSPARAMS.toBoolean()) == true) {
        if (USE_SSH_KEY.toBoolean() == true) {
            bat label: "Fetch Template DSParams",
                script: """
                    %AGENTMETTLECMD% remote download ^
                    -host %IISENGINENAME% ^
                    -username %MCIUSERNAME% -privateKey \"%SSHKEYPATH%\" ^
                    ${SSHPassPhrase} ^
                    -source %IISPROJECTTEMPLATEDIR%/ ^
                    -destination templates/ ^
                    -transferPattern "DSParams"
                """
        } else {
            bat label: "Fetch Template DSParams",
                script: '''
                    %AGENTMETTLECMD% remote download ^
                    -host %IISENGINENAME% ^
                    -username %MCIUSERNAME% -password %MCIPASSWORD% ^
                    -source %IISPROJECTTEMPLATEDIR%/ ^
                    -destination templates/ ^
                    -transferPattern "DSParams"
                '''
        }
        
        bat label: "Create artifacts dir",
            script: "if not exist artifacts mkdir artifacts"

        dir('artifacts'){
            unstash "Diffs"
        }

        bat label: "Merge DSParams",
            script: '''   
                %AGENTMETTLECMD% dsparams merge ^
                -before templates/DSParams ^
                -diff artifacts/DSParams.diff ^
                -after datastage/DSParams
            '''
    }

    bat label: 'Substitute parameters in DataStage config', 
            script: """
                %AGENTMETTLECMD% properties config ^
                -baseDir datastage ^
                -filePattern \"*.apt\" ^
                -filePattern \"*.sh\" ^
                -filePattern \"DSParams\" ^
                ${PSPropertiesConfig} ^
                -properties varfiles/var.%ENVID% ^
                -outDir config
            """

    if (USE_SSH_KEY.toBoolean() == true) {
        bat label: 'Cleanup temporary files from previous builds', 
            script: """
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -script \"config/cleanup.sh\"
            """

        bat label: 'Transfer DataStage config and filesystem assets', 
            script: """
                %AGENTMETTLECMD% remote upload ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -transferPattern \"filesystem/**/*,config/*\" ^
                -destination \"%DATASTAGE_PROJECT%\"
            """
            
        bat label: 'Deploy DataStage config and file system assets', 
            script: """
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -script \"config/deploy.sh\"
            """
    } else {
        bat label: 'Cleanup temporary files from previous builds', 
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -script \"config/cleanup.sh\"
            '''

        bat label: 'Transfer DataStage config and filesystem assets', 
            script: '''
                %AGENTMETTLECMD% remote upload ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -transferPattern \"filesystem/**/*,config/*\" ^
                -destination \"%DATASTAGE_PROJECT%\"
            '''
            
        bat label: 'Deploy DataStage config and file system assets', 
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -script \"config/deploy.sh\"
            '''
    }

    bat label: "Clean logs",
        script: """
            if exist "log" rmdir "log" /S /Q
            mkdir "log"
        """

    if (BINARY_DEPLOYMENT.toBoolean() == true) {
        bat label: "Import binaries into Datastage Project",
            script: """
                %AGENTMETTLECMD% isx import ^
                -domain %IISDOMAINNAME% ^
                -server %IISENGINENAME% ^
                -project %DATASTAGE_PROJECT% ^
                -username %IISUSERNAME% ^
                -password %IISPASSWORD% ^
                -location datastage  ^
                ${PSDeploy} ^
                -project-cache \"%AGENTMETTLEHOME%\\cache\\%IISENGINENAME%\\%DATASTAGE_PROJECT%\\%ENVID%-binaries\"
            """
    } else {
        bat label: 'Deploy DataStage project', 
            script: """
                %AGENTMETTLECMD% datastage deploy ^
                -domain %IISDOMAINNAME% ^
                -server %IISENGINENAME% ^
                -project %DATASTAGE_PROJECT% ^
                -username %IISUSERNAME% ^
                -password %IISPASSWORD% ^
                -assets datastage ^
                ${PSDeploy} ^
                -threads 1 ^
                -project-cache \"%AGENTMETTLEHOME%\\cache\\%IISENGINENAME%\\%DATASTAGE_PROJECT%\"
            """
    }

    if ((PUBLISHCOMPILATIONRESULTS.toBoolean()) == true && findFiles(glob: "log/**/mettleci_compilation.xml").length > 0) {
        junit testResults: 'log/**/mettleci_compilation.xml', 
            allowEmptyResults: true,
            skipPublishingChecks: true
    }
}
