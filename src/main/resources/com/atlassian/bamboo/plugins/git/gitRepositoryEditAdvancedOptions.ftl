[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[@s.checkbox labelKey='repository.git.useShallowClones' toggle=true name='repository.git.useShallowClones' /]
[@ui.bambooSection dependsOn='repository.git.useShallowClones']
    [#if (plan.buildDefinition.branchIntegrationConfiguration.enabled)!false ]
        [@ui.messageBox type='info' titleKey='repository.git.messages.branchIntegration.shallowClonesWillBeDisabled' /]
    [/#if]
[/@ui.bambooSection]

[@s.checkbox labelKey='repository.git.useRemoteAgentCache' toggle=false name='repository.git.useRemoteAgentCache' /]

[@s.checkbox labelKey='repository.git.useSubmodules' name='repository.git.useSubmodules' /]
[@s.textfield labelKey='repository.git.commandTimeout' name='repository.git.commandTimeout' /]
[@s.checkbox labelKey='repository.git.verbose.logs' name='repository.git.verbose.logs' /]
[@s.checkbox labelKey='repository.git.fetch.whole.repository' name='repository.git.fetch.whole.repository' /]
