/**
 * This workflow is used to deploy DataStage assets to a DataStage server. It is designed to be called
 * from other workflows, such as the main 'JenkinsFile_*' perovided with this library.
 * The workflow uses the MettleCI CLI to perform the deployment and configuration of DataStage assets and
 * accepts several inputs, including the DataStage project name, environment name, and other parameters.
 * This workflow also includes steps to fetch and merge DSParams files, configure properties, and execute
 * arbitrary user-specified deployment scripts.
 * Finally, the workflow includes steps to publish compilation test results, if specified.
 * The workflow is designed to be flexible and reusable, allowing for easy integration into different
 * deployment workflows.
 */

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

    /**
      * This step downloads the DataStage DSParams template file from the server to the machine running the Jenkins job.
      * A subsequent step (using the 'properties config' command) will merge the downloaded DSParams file with values
      * sourced from a relevant 'var' file to create a new DSParams file suitable for the destination environment.
      * This optional step is invoked if the UPGRADE_DSPARAMS parameter is set to true.If executed, the relevant MettleCI
      * CLI command is executed conditionally based on the value of the USE_SSH_KEY parameter.
      * If USE_SSH_KEY is true, the command is executed using SSH keys for authentication.
      * Otherwise, the command is executed using a username and password for authentication.
      * See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/716636187/Remote+Download+Command
      */
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

        // Create the artifacts directory if it doesn't exist
        bat label: "Create artifacts dir",
            script: "if not exist artifacts mkdir artifacts"

        dir('artifacts'){
            unstash "Diffs"
        }

        // This step merges the DSParams file from the server with the local project
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/458556064/DSParams+Merge+Command
        bat label: "Merge DSParams",
            script: '''   
                %AGENTMETTLECMD% dsparams merge ^
                -before templates/DSParams ^
                -diff artifacts/DSParams.diff ^
                -after datastage/DSParams
            '''
    }

    // This step replaces variable values in one or more specified files using replacement values from a properties file
    // It is used to update the DSParams file with the merged values from the previous step
    // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/718962693/Properties+Config+Command
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

    /**
     * This section runs three commands:
     * 1. Cleanup temporary files from previous builds
     * 2. Transfer DataStage config and filesystem assets to the DataStage server
     * 3. Deploy DataStage config and file system assets to the DataStage server
     *
     * The commands - executed using the MettleCI CLI - are executed conditionally based on the value of the
     * USE_SSH_KEY parameter. If USE_SSH_KEY is true, the commands are executed using SSH keys for authentication. 
     * Otherwise, the commands are executed using a username and password for authentication.
     */
    if (USE_SSH_KEY.toBoolean() == true) {
        // This step executes a user-defined script to perform any additional configuration or setup required
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/784367633/Remote+Execute+Command
        bat label: 'Cleanup temporary files from previous builds',
            script: """
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -privateKey \"%SSHKEYPATH%\" ^
                ${SSHPassPhrase} ^
                -script \"config/cleanup.sh\"
            """

        // This step uploads the configuration files to the DataStage server
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/716603405/Remote+Upload+Command
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

        // This step executes a user-defined script ('deploy.sh') to perform any additional deployment processes required
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/784367633/Remote+Execute+Command
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
        // This step executes a user-defined script ('cleanup.sh') to perform any additional environmental 
        // clean up required. 
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/784367633/Remote+Execute+Command
        bat label: 'Cleanup temporary files from previous builds',
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -script \"config/cleanup.sh\"
            '''
        // This step uploads the configuration files to the DataStage server
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/716603405/Remote+Upload+Command
        bat label: 'Transfer DataStage config and filesystem assets',
            script: '''
                %AGENTMETTLECMD% remote upload ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -transferPattern \"filesystem/**/*,config/*\" ^
                -destination \"%DATASTAGE_PROJECT%\"
            '''
        // This step executes a user-defined script ('deploy.sh') to perform any additional deployment processes required
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/784367633/Remote+Execute+Command
        bat label: 'Deploy DataStage config and file system assets',
            script: '''
                %AGENTMETTLECMD% remote execute ^
                -host %IISENGINENAME% ^
                -username %MCIUSERNAME% ^
                -password %MCIPASSWORD% ^
                -script \"config/deploy.sh\"
            '''
    }

    // Re-create the artifacts directory to ensure it's empty before the next step
    bat label: "Clean logs",
        script: """
            if exist "log" rmdir "log" /S /Q
            mkdir "log"
        """
    /**
      * This step deploys the DataStage required assets to the DataStage server using the MettleCI CLI.
      * The relevant command is executed conditionally based on the value of the BINARY_DEPLOYMENT parameter.
      * If BINARY_DEPLOYMENT is true, the 'isx import' command is used to import binaries into the DataStage project.
      * Otherwise, it uses the 'datastage deloy' command to deploy the DataStage project.
      *
      * See the relevant documentation here:
      * https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/1948385285/Deploying+DataStage+Binaries  
      */
    if (BINARY_DEPLOYMENT.toBoolean() == true) {
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/409894937/ISX+Import+Command
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
        // See https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/423952410/DataStage+Deploy+Command
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

    // Publish JUnit XML compilation test output files to Jenkins
    if ((PUBLISHCOMPILATIONRESULTS.toBoolean()) == true && findFiles(glob: "log/**/mettleci_compilation.xml").length > 0) {
        junit testResults: 'log/**/mettleci_compilation.xml',
            allowEmptyResults: true,
            skipPublishingChecks: true
    }
}
