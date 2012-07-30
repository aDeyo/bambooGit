package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.commit.CommitContext;
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
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import com.opensymphony.webwork.ServletActionContext;

import java.io.File;
import java.util.List;

public class GitHubRepository extends AbstractStandaloneRepository implements CustomSourceDirectoryAwareRepository,
                                                                              AdvancedConfigurationAwareRepository,
                                                                              BranchDetectionCapableRepository,
                                                                              CachingAwareRepository,
                                                                              PushCapableRepository,
                                                                              BranchMergingAwareRepository

{
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

    private String username;
    private String password;
    private String repository;
    private String branch;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private boolean verboseLogs;
    private int commandTimeout;


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
                    .append(this.repository, ghRepo.getRepository())
                    .append(this.branch, ghRepo.getBranch())
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
        username = config.getString(REPOSITORY_GITHUB_USERNAME);
        password = config.getString(REPOSITORY_GITHUB_PASSWORD);
        repository = config.getString(REPOSITORY_GITHUB_REPOSITORY);
        branch = config.getString(REPOSITORY_GITHUB_BRANCH);
        useShallowClones = config.getBoolean(REPOSITORY_GITHUB_USE_SHALLOW_CLONES);
        useSubmodules = config.getBoolean(REPOSITORY_GITHUB_USE_SUBMODULES);
        commandTimeout = config.getInt(REPOSITORY_GITHUB_COMMAND_TIMEOUT, GitRepository.DEFAULT_COMMAND_TIMEOUT_IN_MINUTES);
        verboseLogs = config.getBoolean(REPOSITORY_GITHUB_VERBOSE_LOGS, false);

        gitRepository.accessData.repositoryUrl = "https://github.com/" + repository + ".git";
        gitRepository.accessData.username = username;
        gitRepository.accessData.password = password;
        gitRepository.accessData.branch = branch;
        gitRepository.accessData.sshKey = "";
        gitRepository.accessData.sshPassphrase = "";
        gitRepository.accessData.authenticationType = GitAuthenticationType.PASSWORD;
        gitRepository.accessData.useShallowClones = useShallowClones;
        gitRepository.accessData.useSubmodules = useSubmodules;
        gitRepository.accessData.commandTimeout = commandTimeout;
        gitRepository.accessData.verboseLogs = verboseLogs;

        gitRepository.setVcsBranch(new VcsBranchImpl(branch));
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GITHUB_USERNAME, username);
        configuration.setProperty(REPOSITORY_GITHUB_PASSWORD, password);
        configuration.setProperty(REPOSITORY_GITHUB_REPOSITORY, repository);
        configuration.setProperty(REPOSITORY_GITHUB_BRANCH, branch);
        configuration.setProperty(REPOSITORY_GITHUB_USE_SHALLOW_CLONES, useShallowClones);
        configuration.setProperty(REPOSITORY_GITHUB_USE_SUBMODULES, useSubmodules);
        configuration.setProperty(REPOSITORY_GITHUB_COMMAND_TIMEOUT, commandTimeout);
        configuration.setProperty(REPOSITORY_GITHUB_VERBOSE_LOGS, verboseLogs);

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
        return username;
    }

    public String getRepository()
    {
        return repository;
    }

    public String getBranch()
    {
        return branch;
    }

    public boolean isUseShallowClones()
    {
        return useShallowClones;
    }

    public String getEncryptedPassword()
    {
        return password;
    }

    public boolean isUseSubmodules()
    {
        return useSubmodules;
    }

    public int getCommandTimeout()
    {
        return commandTimeout;
    }

    public boolean getVerboseLogs()
    {
        return verboseLogs;
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
        return gitRepository.getOpenBranches();
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
        branch = vcsBranch.getName();
    }

    @Override
    public CacheId getCacheId(@NotNull final CachableOperation cachableOperation)
    {
        switch (cachableOperation)
        {
            case BRANCH_DETECTION:
                return new CacheId(this, gitRepository.accessData.repositoryUrl);
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
    public String getBranchIntegrationEditHtml()
    {
        return gitRepository.getBranchIntegrationEditHtml();
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

}
