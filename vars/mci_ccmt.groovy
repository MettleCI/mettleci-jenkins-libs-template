def call(
    def PUBLISHCOMPILATIONRESULTS,
    def UPGRADEORACLEVARIANT = false,
    def UPGRADEDORACLEVERSION = "11"
) {
    def variantParams = "${((UPGRADEORACLEVARIANT.toBoolean()) == true)?" -param \" -T \" -param \" -V OracleConnector=${UPGRADEDORACLEVERSION},OracleConnectorPX=${UPGRADEDORACLEVERSION} \"":""}"

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

    if ((PUBLISHCOMPILATIONRESULTS.toBoolean()) == true && findFiles(glob: "log/ccmt/**/mettleci_compilation.xml").length > 0) {
        junit testResults: 'log/ccmt/**/mettleci_compilation.xml', 
                allowEmptyResults: true,
                skipPublishingChecks: true
    }
}
