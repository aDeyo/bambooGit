[#-- @ftlvariable name="buildConfiguration" type="com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration" --]
[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[@ui.bambooSection]
    [@ww.textfield labelKey='repository.git.repositoryUrl' name='repository.git.repositoryUrl' longField=true required=true helpKey='git.fields' /]

    [@ww.textfield labelKey='repository.git.branch' name='repository.git.branch' /]
    [#if buildConfiguration.getBoolean('repository.git.sharedCredentials.deleted')]
        [#if fn.hasRestrictedAdminPermission()]
            [@ui.messageBox type="error" titleKey="repository.git.sharedCredentials.deleted.title"]
                [@ww.text name="repository.git.sharedCredentials.deleted.edit"/]
                <br/>
                [@ww.text name="repository.git.sharedCredentials.deleted.update" ]
                    [@ww.param][@ww.url action="configureSharedCredentials" namespace="/admin" /][/@ww.param]
                [/@ww.text]
            [/@ui.messageBox]
        [#else]
            [@ui.messageBox type="error" titleKey="repository.git.sharedCredentials.deleted.title"]
                [@ww.text name="repository.git.sharedCredentials.deleted.edit"/]
            [/@ui.messageBox]
        [/#if]
    [#else]
        [#assign defaultSelected='repository.git.authenticationType' /]
    [/#if]
    [@ww.select
        labelKey='repository.git.authenticationType'
        name='repository.git.authenticationType'
        toggle=true
        list=repository.authenticationTypes
        listKey='name'
        listValue='label'
        value=defaultSelected! ]
    [/@ww.select]

    [@ui.bambooSection dependsOn='repository.git.authenticationType' showOn='PASSWORD']
        [@ww.textfield labelKey='repository.git.username' name='repository.git.username' /]

        [#if buildConfiguration.getString('repository.git.password')?has_content]
            [@ww.checkbox labelKey='repository.password.change' toggle=true name='temporary.git.password.change' /]
            [@ui.bambooSection dependsOn='temporary.git.password.change']
                [@ww.password labelKey='repository.git.password' name='temporary.git.password' required='false' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.password.change' value='true' /]
            [@ww.password labelKey='repository.git.password' name='temporary.git.password' /]
        [/#if]
    [/@ui.bambooSection]
    [@ui.bambooSection dependsOn='repository.git.authenticationType' showOn='SSH_KEYPAIR']
        [#if buildConfiguration.getString('repository.git.ssh.key')?has_content]
            [@ww.checkbox labelKey='repository.git.ssh.key.change' toggle=true name='temporary.git.ssh.key.change' /]
            [@ui.bambooSection dependsOn='temporary.git.ssh.key.change']
                [@ww.file labelKey='repository.git.ssh.key' name='temporary.git.ssh.keyfile' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.ssh.key.change' value='true'/]
            [@ww.file labelKey='repository.git.ssh.key' name='temporary.git.ssh.keyfile' /]
        [/#if]

        [#if buildConfiguration.getString('repository.git.ssh.passphrase')?has_content]
            [@ww.checkbox labelKey='repository.passphrase.change' toggle=true name='temporary.git.ssh.passphrase.change' /]
            [@ui.bambooSection dependsOn='temporary.git.ssh.passphrase.change']
                [@ww.password labelKey='repository.git.ssh.passphrase' name='temporary.git.ssh.passphrase' /]
            [/@ui.bambooSection]
        [#else]
            [@ww.hidden name='temporary.git.ssh.passphrase.change' value='true' /]
            [@ww.password labelKey='repository.git.ssh.passphrase' name='temporary.git.ssh.passphrase' /]
        [/#if]
    [/@ui.bambooSection]
    
    [@ui.bambooSection dependsOn='repository.git.authenticationType' showOn='SHARED_CREDENTIALS']
        [@ww.select
	        labelKey='repository.git.sharedCrendentials'
	        name='repository.git.sharedCrendentials'
	        toggle=true
	        list=repository.sharedCredentials
	        listKey='name'
	        listValue='label']
	    [/@ww.select]
    [/@ui.bambooSection]

[/@ui.bambooSection]