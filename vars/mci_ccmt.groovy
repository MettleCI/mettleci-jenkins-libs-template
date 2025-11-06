/**
 * ███╗   ███╗███████╗████████╗████████╗██╗     ███████╗ ██████╗██╗
 * ████╗ ████║██╔════╝╚══██╔══╝╚══██╔══╝██║     ██╔════╝██╔════╝██║
 * ██╔████╔██║█████╗     ██║      ██║   ██║     █████╗  ██║     ██║
 * ██║╚██╔╝██║██╔══╝     ██║      ██║   ██║     ██╔══╝  ██║     ██║
 * ██║ ╚═╝ ██║███████╗   ██║      ██║   ███████╗███████╗╚██████╗██║
 * ╚═╝     ╚═╝╚══════╝   ╚═╝      ╚═╝   ╚══════╝╚══════╝ ╚═════╝╚═╝
 * MettleCI DevOps for DataStage       (C) 2021-2025 Data Migrators
 *
 * This workflow is a template for running the IBM Connector Migration Toolkit.
 * It is designed to be called from other workflows and requires specific inputs to function correctly.
 * See https://www.ibm.com/docs/en/iis/11.7.0?topic=connectivity-connector-migration-tool
 */
 
def call(
    def PUBLISHCOMPILATIONRESULTS,
    def UPGRADEORACLEVARIANT = false,
    def UPGRADEDORACLEVERSION = "11"
) {
    def variantParams = "${((UPGRADEORACLEVARIANT.toBoolean()) == true)?" -param \" -T \" -param \" -V OracleConnector=${UPGRADEDORACLEVERSION},OracleConnectorPX=${UPGRADEDORACLEVERSION} \"":""}"

    // See https://docs.mettleci.io/?q=datastage-connector-migration-command
    bat label: "Run CCMT - Upgrade Stages", 
        script: """
            %AGENTMETTLECMD% datastage ccmt ^
            -domain %IISDOMAINNAME% ^
            -server %IISENGINENAME% ^
            -username %IISUSERNAME% -password %IISPASSWORD% ^
            -project %DATASTAGE_PROJECT% ^
            -project-cache \"%AGENTMETTLEHOME%\\cache\\%IISENGINENAME%\\%DATASTAGE_PROJECT%\" ^
            -isxdirectory datastage -logfile \\log\\ccmt\\log\\cc_migrate.log.txt -threads 4 ^
            -param \" -M \" ${variantParams}   
        """

    // Publish JUnit XML compilation test output files to Jenkins (if enabled by the relevant parameter)
    if ((PUBLISHCOMPILATIONRESULTS.toBoolean()) == true && findFiles(glob: "log/ccmt/**/mettleci_compilation.xml").length > 0) {
        junit testResults: 'log/ccmt/**/mettleci_compilation.xml', 
                allowEmptyResults: true,
                skipPublishingChecks: true
    }
}
