```
     __  __      _   _   _       ____ ___
    |  \/  | ___| |_| |_| | ___ / ___|_ _|
    | |\/| |/ _ \ __| __| |/ _ \ |    | |
    | |  | |  __/ |_| |_| |  __/ |___ | |
    |_|  |_|\___|\__|\__|_|\___|\____|___|
    MettleCI DevOps for DataStage
    (C) 2021-2024 Data Migrators
```

# Jenkins Shared Libraries

All MettleCI pipeline examples make use of re-usable pipeline components, the terminology for which varies between technologies.

Jenkins is notable in that it is the only MettleCI-supported build system which requries its re-usable pipeline components to reside in a separate repository to the main repository (i.e. this one) which utilises those re-usable components.  For this reason you need to ensure that for Jenkins-based pipelines you do the following:

- Deploy the jenkins-mci-shared-libraries to a separate Git repository, alongside the repository holding your DataStage assets (i.e. this repository). 
- Ensure that the first line of your Jenkins pipeline definition refers to the name of the repository you created to hold your shared libraries. e.g. `@Library('jenkins-mci-shared-libraries') _`

Files provided in this repository are:			

- `mci_ccmt.groovy`
- `mci_compliance.groovy`
- `mci_deploy.groovy`
- `mci_unittest.groovy`

See [this page](https://datamigrators.atlassian.net/wiki/spaces/MCIDOC/pages/2234810369/Reusable+Pipeline+Templates+in+Jenkins) for explanations of these.

