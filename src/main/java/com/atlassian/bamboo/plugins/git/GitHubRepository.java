package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.branch.BranchIntegrationHelper;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.BranchDetectionCapableRepository;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.CacheId;
import com.atlassian.bamboo.repository.CachingAwareRepository;
import com.atlassian.bamboo.repository.PushCapableRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.template.TemplateRenderer;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.sal.api.message.I18nResolver;
import com.opensymphony.webwork.ServletActionContext;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.util.List;

public class GitHubRepository extends AbstractStandaloneRepository implements CustomSourceDirectoryAwareRepository,
                                                                              AdvancedConfigurationAwareRepository,
                                                                              BranchDetectionCapableRepository,
                                                                              CachingAwareRepository,
                                                                              PushCapableRepository,
                                                                              BranchMergingAwareRepository

{
    @SuppressWarnings("unused")
    private static final Logger log = Logger.getLogger(GitHubRepository.class);
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GITHUB_USERNAME = "repository.github.username";
    private static final String REPOSITORY_GITHUB_PASSWORD = "repository.github.password";
    private static final String REPOSITORY_GITHUB_REPOSITORY = "repository.github.repository";
    private static final String REPOSITORY_GITHUB_BRANCH = "repository.github.branch";
    private static final String REPOSITORY_GITHUB_USE_SHALLOW_CLONES = "repository.github.useShallowClones";
    private static final String REPOSITORY_GITHUB_USE_SUBMODULES = "repository.github.useSubmodules";
    private static final String REPOSITORY_GITHUB_COMMAND_TIMEOUT = "repository.github.commandTimeout";
    private static final String REPOSITORY_GITHUB_VERBOSE_LOGS = "repository.github.verbose.logs";

    private static final String REPOSITORY_GITHUB_TEMPORARY_PASSWORD = "repository.github.temporary.password";
    private static final String TEMPORARY_GITHUB_PASSWORD_CHANGE = "temporary.github.password.change";

    private static final String REPOSITORY_GITHUB_ERROR_MISSING_REPOSITORY = "repository.github.error.missingRepository";

    // ------------------------------------------------------------------------------------------------- Type Properties

    private GitRepository gitRepository = new GitRepository();

    private GitHubRepositoryAccessData accessData = new GitHubRepositoryAccessData();

    // ---------------------------------------------------------------------------------------------------- Dependencies

    private I18nResolver i18nResolver;
    private EncryptionService encryptionService;

    public void setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager)
    {
        super.setBuildDirectoryManager(buildDirectoryManager);
        gitRepository.setBuildDirectoryManager(buildDirectoryManager);
    }

    public void setBuildLoggerManager(BuildLoggerManager buildLoggerManager)
    {
        super.setBuildLoggerManager(buildLoggerManager);
        gitRepository.setBuildLoggerManager(buildLoggerManager);
    }

    public void setI18nResolver(I18nResolver i18nResolver)
    {
        this.i18nResolver = i18nResolver;
        gitRepository.setI18nResolver(i18nResolver);
    }

    public void setEncryptionService(EncryptionService encryptionService)
    {
        this.encryptionService = encryptionService;
        gitRepository.setEncryptionService(encryptionService);
    }

    @Override
    public void setCustomVariableContext(CustomVariableContext customVariableContext)
    {
        super.setCustomVariableContext(customVariableContext);
        gitRepository.setCustomVariableContext(customVariableContext);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext)
    {
        gitRepository.setCapabilityContext(capabilityContext);
    }

    public void setSshProxyService(SshProxyService sshProxyService)
    {
        gitRepository.setSshProxyService(sshProxyService);
    }

    public void setBranchIntegrationHelper(final BranchIntegrationHelper branchIntegrationHelper)
    {
        gitRepository.setBranchIntegrationHelper(branchIntegrationHelper);
    }


    @Override
    public void setTemplateRenderer(TemplateRenderer templateRenderer)
    {
        super.setTemplateRenderer(templateRenderer);
        gitRepository.setTemplateRenderer(templateRenderer);
    }

    // ---------------------------------------------------------------------------------------------------- Constructors
    // ----------------------------------------------------------------------------------------------- Interface Methods

    @NotNull
    public String getName()
    {
        return "GitHub";
    }

    public String getHost()
    {
        return null;
    }

    public boolean isRepositoryDifferent(@NotNull Repository repository)
    {
        if (repository instanceof GitHubRepository)
        {
            GitHubRepository ghRepo = (GitHubRepository) repository;
            return !new EqualsBuilder()
                    .append(this.getRepository(), ghRepo.getRepository())
                    .append(this.getBranch(), ghRepo.getBranch())
                    .isEquals();
        }
        else
        {
            return true;
        }
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GITHUB_COMMAND_TIMEOUT, String.valueOf(GitRepository.DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GITHUB_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GITHUB_USE_SHALLOW_CLONES, true);
        buildConfiguration.clearTree(REPOSITORY_GITHUB_USE_SUBMODULES);

    }

    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GITHUB_USERNAME, buildConfiguration.getString(REPOSITORY_GITHUB_USERNAME, "").trim());
        if (buildConfiguration.getBoolean(TEMPORARY_GITHUB_PASSWORD_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GITHUB_PASSWORD, encryptionService.encrypt(buildConfiguration.getString(REPOSITORY_GITHUB_TEMPORARY_PASSWORD)));
        }
        buildConfiguration.setProperty(REPOSITORY_GITHUB_REPOSITORY, buildConfiguration.getString(REPOSITORY_GITHUB_REPOSITORY, "").trim());
        buildConfiguration.setProperty(REPOSITORY_GITHUB_BRANCH, buildConfiguration.getString(REPOSITORY_GITHUB_BRANCH, "").trim());
    }

    @Override
    public void populateFromConfig(@NotNull HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);

        final GitHubRepositoryAccessData accessData = GitHubRepositoryAccessData.builder(getAccessData())
                .repository(config.getString(REPOSITORY_GITHUB_REPOSITORY))
                .username(config.getString(REPOSITORY_GITHUB_USERNAME))
                .password(config.getString(REPOSITORY_GITHUB_PASSWORD))
                .branch(config.getString(REPOSITORY_GITHUB_BRANCH))
                .useShallowClones(config.getBoolean(REPOSITORY_GITHUB_USE_SHALLOW_CLONES))
                .useSubmodules(config.getBoolean(REPOSITORY_GITHUB_USE_SUBMODULES))
                .commandTimeout(config.getInt(REPOSITORY_GITHUB_COMMAND_TIMEOUT, GitRepository.DEFAULT_COMMAND_TIMEOUT_IN_MINUTES))
                .verboseLogs(config.getBoolean(REPOSITORY_GITHUB_VERBOSE_LOGS, false))
                .build();


        setAccessData(accessData);
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GITHUB_USERNAME, getUsername());
        configuration.setProperty(REPOSITORY_GITHUB_PASSWORD, getEncryptedPassword());
        configuration.setProperty(REPOSITORY_GITHUB_REPOSITORY, getRepository());
        configuration.setProperty(REPOSITORY_GITHUB_BRANCH, getBranch());
        configuration.setProperty(REPOSITORY_GITHUB_USE_SHALLOW_CLONES, isUseShallowClones());
        configuration.setProperty(REPOSITORY_GITHUB_USE_SUBMODULES, isUseSubmodules());
        configuration.setProperty(REPOSITORY_GITHUB_COMMAND_TIMEOUT, getCommandTimeout());
        configuration.setProperty(REPOSITORY_GITHUB_VERBOSE_LOGS, getVerboseLogs());

        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        if (StringUtils.isBlank(buildConfiguration.getString(REPOSITORY_GITHUB_REPOSITORY)))
        {
            errorCollection.addError(REPOSITORY_GITHUB_REPOSITORY, i18nResolver.getText(REPOSITORY_GITHUB_ERROR_MISSING_REPOSITORY));
        }
        return errorCollection;
    }

    @Override
    @NotNull
    public BuildRepositoryChanges collectChangesForRevision(@NotNull PlanKey planKey, @NotNull String targetRevision) throws RepositoryException
    {
        return gitRepository.collectChangesForRevision(planKey, targetRevision);
    }

    @NotNull
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable String lastVcsRevisionKey) throws RepositoryException
    {
        return gitRepository.collectChangesSinceLastBuild(planKey, lastVcsRevisionKey);
    }

    @NotNull
    public String retrieveSourceCode(@NotNull BuildContext buildContext, @Nullable final String vcsRevision) throws RepositoryException
    {
        return gitRepository.retrieveSourceCode(buildContext, vcsRevision, getSourceCodeDirectory(buildContext.getPlanResultKey().getPlanKey()));
    }

    @NotNull
    @Override
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevisionKey, @NotNull final File sourceDirectory) throws RepositoryException
    {
        return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory);
    }

    @NotNull
    @Override
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevisionKey, @NotNull final File sourceDirectory, int depth) throws RepositoryException
    {
        return gitRepository.retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory, depth);
    }

    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------------------- Helper Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public String getUsername()
    {
        return accessData.getUsername();
    }

    public String getRepository()
    {
        return accessData.getRepository();
    }

    public String getBranch()
    {
        return accessData.getBranch();
    }

    public boolean isUseShallowClones()
    {
        return accessData.isUseShallowClones();
    }

    public String getEncryptedPassword()
    {
        return accessData.getPassword();
    }

    public boolean isUseSubmodules()
    {
        return accessData.isUseSubmodules();
    }

    public int getCommandTimeout()
    {
        return accessData.getCommandTimeout();
    }

    public boolean getVerboseLogs()
    {
        return accessData.isVerboseLogs();
    }

    @NotNull
    @Deprecated
    public List<VcsBranch> getOpenBranches() throws RepositoryException
    {
        return getOpenBranches(null);
    }

    @NotNull
    @Override
    public List<VcsBranch> getOpenBranches(@Nullable final String context) throws RepositoryException
    {
        return gitRepository.getOpenBranches(context);
    }

    @Override
    @NotNull
    public VcsBranch getVcsBranch()
    {
        return gitRepository.getVcsBranch();
    }

    @Override
    public void setVcsBranch(@NotNull final VcsBranch vcsBranch)
    {
        gitRepository.setVcsBranch(vcsBranch);
    }

    @Override
    public CacheId getCacheId(@NotNull final CachableOperation cachableOperation)
    {
        switch (cachableOperation)
        {
            case BRANCH_DETECTION:
                return new CacheId(this, gitRepository.getAccessData().getRepositoryUrl());
        }
        return null;
    }

    @Override
    public boolean isCachingSupportedFor(@NotNull final CachableOperation cachableOperation)
    {
        return cachableOperation==CachableOperation.BRANCH_DETECTION;
    }

    @Override
    public CommitContext getLastCommit() throws RepositoryException
    {
        return gitRepository.getLastCommit();
    }

    @Override
    public CommitContext getFirstCommit() throws RepositoryException
    {
        return gitRepository.getFirstCommit();
    }

    @Override
    public boolean mergeWorkspaceWith(@NotNull final BuildContext buildContext, @NotNull final File file, @NotNull final String s) throws RepositoryException
    {
        return gitRepository.mergeWorkspaceWith(buildContext, file, s);
    }

    @Override
    public boolean isMergingSupported()
    {
        return gitRepository.isMergingSupported();
    }

    @Override
    public void pushRevision(@NotNull final File file, @Nullable final String s) throws RepositoryException
    {
        gitRepository.pushRevision(file, s);
    }

    @NotNull
    @Override
    public String commit(@NotNull final File file, @NotNull final String s) throws RepositoryException
    {
        return gitRepository.commit(file, s);
    }

    public String getOptionDescription()
    {
        String capabilitiesLink = ServletActionContext.getRequest().getContextPath() + "/admin/agent/configureSharedLocalCapabilities.action";
        return i18nResolver.getText("repository.git.description", gitRepository.getGitCapability(), capabilitiesLink);
    }

    public GitHubRepositoryAccessData getAccessData()
    {
        return accessData;
    }

    public void setAccessData(GitHubRepositoryAccessData accessData)
    {
        this.accessData = accessData;

        gitRepository.setAccessData(GitRepositoryAccessData.builder(gitRepository.getAccessData())
                                            .repositoryUrl("https://github.com/" + accessData.getRepository() + ".git")
                                            .username(accessData.getUsername())
                                            .password(accessData.getPassword())
                                            .branch(accessData.getBranch())
                                            .sshKey(null)
                                            .sshPassphrase(null)
                                            .authenticationType(GitAuthenticationType.PASSWORD)
                                            .useShallowClones(accessData.isUseShallowClones())
                                            .useSubmodules(accessData.isUseSubmodules())
                                            .commandTimeout(accessData.getCommandTimeout())
                                            .verboseLogs(accessData.isVerboseLogs())
                                            .build());

        gitRepository.setVcsBranch(new VcsBranchImpl(accessData.getBranch()));
    }
}
