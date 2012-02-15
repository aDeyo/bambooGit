[#-- @ftlvariable name="plan" type="com.atlassian.bamboo.plan.Plan" --]
[#-- @ftlvariable name="repository" type="com.atlassian.bamboo.plugins.git.GitRepository" --]
[@ww.label labelKey='repository.git.repositoryUrl' value=repository.repositoryUrl?html /]
[@ww.label labelKey='repository.git.branch' value=repository.branch?html hideOnNull=true /]
[@ww.label labelKey='repository.git.authenticationType' value=repository.authTypeName /]
[@ww.label labelKey='repository.git.useShallowClones' value=repository.useShallowClones?string hideOnNull=true /]
[@ww.label labelKey='repository.git.useSubmodules' value=repository.useSubmodules?string hideOnNull=true /]
[@ww.label labelKey='repository.git.commandTimeout' value=repository.commandTimeout! hideOnNull=true /]
[@ww.label labelKey='repository.git.verbose.logs' value=repository.verboseLogs?string hideOnNull=true /]
[@ww.label labelKey='repository.git.cacheDirectory' value=repository.cacheDirectory/]

[#if plan?? && fn.hasGlobalAdminPermission() && repository.cacheDirectory?? && repository.cacheDirectory.exists()]
    [@ui.messageBox type='info']
        [@ww.text name='repository.git.cacheDirectory.cleanMessage'/]
        <a class="requireConfirmation"
           title="[@ww.text name='repository.git.cacheDirectory.cleanTitle' /]"
           href="[@ww.url action='deleteGitCacheDirectory' namespace='/build/admin' buildKey=plan.key/]">[@ww.text name='global.buttons.delete' /]</a>
    [/@ui.messageBox]
[/#if]
