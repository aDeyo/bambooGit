package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.author.Author;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.commit.CommitContextImpl;
import com.atlassian.bamboo.core.TransportProtocol;
import com.atlassian.bamboo.credentials.CredentialsAccessor;
import com.atlassian.bamboo.credentials.CredentialsData;
import com.atlassian.bamboo.credentials.PrivateKeyCredentials;
import com.atlassian.bamboo.credentials.SharedCredentialDepender;
import com.atlassian.bamboo.credentials.SshCredentialsImpl;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.branch.BranchIntegrationHelper;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.repository.AbstractStandaloneRepository;
import com.atlassian.bamboo.repository.AdvancedConfigurationAwareRepository;
import com.atlassian.bamboo.repository.BranchDetectionCapableRepository;
import com.atlassian.bamboo.repository.BranchMergingAwareRepository;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.CacheHandler;
import com.atlassian.bamboo.repository.CacheId;
import com.atlassian.bamboo.repository.CachingAwareRepository;
import com.atlassian.bamboo.repository.CustomVariableProviderRepository;
import com.atlassian.bamboo.repository.DeploymentAwareRepository;
import com.atlassian.bamboo.repository.MavenPomAccessor;
import com.atlassian.bamboo.repository.MavenPomAccessorCapableRepository;
import com.atlassian.bamboo.repository.NameValuePair;
import com.atlassian.bamboo.repository.PushCapableRepository;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.repository.SelectableAuthenticationRepository;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.util.TextProviderUtils;
import com.atlassian.bamboo.utils.BambooFieldValidate;
import com.atlassian.bamboo.utils.SystemProperty;
import com.atlassian.bamboo.utils.error.ErrorCollection;
import com.atlassian.bamboo.utils.fage.Result;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.v2.build.agent.capability.Requirement;
import com.atlassian.bamboo.v2.build.agent.remote.RemoteBuildDirectoryManager;
import com.atlassian.bamboo.v2.build.repository.CustomSourceDirectoryAwareRepository;
import com.atlassian.bamboo.v2.build.repository.RequirementsAwareRepository;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.util.concurrent.Supplier;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Function;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opensymphony.webwork.ServletActionContext;
import com.opensymphony.xwork.ValidationAware;
import org.apache.commons.configuration.AbstractConfiguration;
import org.apache.commons.configuration.HierarchicalConfiguration;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.builder.EqualsBuilder;
import org.apache.log4j.Logger;
import org.eclipse.jgit.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Callable;


public class GitRepository
        extends AbstractStandaloneRepository
        implements MavenPomAccessorCapableRepository,
        SelectableAuthenticationRepository,
        CustomVariableProviderRepository,
        CustomSourceDirectoryAwareRepository,
        RequirementsAwareRepository,
        AdvancedConfigurationAwareRepository,
        BranchDetectionCapableRepository,
        PushCapableRepository,
        CachingAwareRepository,
        BranchMergingAwareRepository,
        CacheHandler,
        DeploymentAwareRepository,
        SharedCredentialDepender
{
    // ------------------------------------------------------------------------------------------------------- Constants

    private static final String REPOSITORY_GIT_NAME = "repository.git.name";
    private static final String REPOSITORY_URL = "repositoryUrl";
    private static final String REPOSITORY_GIT_REPOSITORY_URL = "repository.git." + REPOSITORY_URL;
    @VisibleForTesting
    static final String REPOSITORY_GIT_AUTHENTICATION_TYPE = "repository.git.authenticationType";
    private static final String REPOSITORY_USERNAME = "username";
    private static final String REPOSITORY_GIT_USERNAME = "repository.git." + REPOSITORY_USERNAME;
    private static final String REPOSITORY_GIT_PASSWORD = "repository.git.password";
    private static final String REPOSITORY_BRANCH = "branch";
    private static final String REPOSITORY_GIT_BRANCH = "repository.git." + REPOSITORY_BRANCH;
    private static final String REPOSITORY_GIT_SSH_KEY = "repository.git.ssh.key";
    private static final String REPOSITORY_GIT_SSH_PASSPHRASE = "repository.git.ssh.passphrase";
    private static final String REPOSITORY_GIT_USE_SHALLOW_CLONES = "repository.git.useShallowClones";
    private static final String REPOSITORY_GIT_USE_REMOTE_AGENT_CACHE = "repository.git.useRemoteAgentCache";
    private static final String REPOSITORY_GIT_USE_SUBMODULES = "repository.git.useSubmodules";
    private static final String REPOSITORY_GIT_MAVEN_PATH = "repository.git.maven.path";
    private static final String REPOSITORY_GIT_COMMAND_TIMEOUT = "repository.git.commandTimeout";
    private static final String REPOSITORY_GIT_VERBOSE_LOGS = "repository.git.verbose.logs";
    private static final String REPOSITORY_GIT_SHAREDCREDENTIALS_ID = "repository.git.sharedCrendentials";
    private static final String TEMPORARY_GIT_PASSWORD = "temporary.git.password";
    private static final String TEMPORARY_GIT_PASSWORD_CHANGE = "temporary.git.password.change";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE = "temporary.git.ssh.passphrase";
    private static final String TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE = "temporary.git.ssh.passphrase.change";
    private static final String TEMPORARY_GIT_SSH_KEY_FROM_FILE = "temporary.git.ssh.keyfile";
    private static final String TEMPORARY_GIT_SSH_KEY_CHANGE = "temporary.git.ssh.key.change";
    private static final String SHARED_CREDENTIALS = "SHARED_CREDENTIALS";

    protected static boolean USE_SHALLOW_CLONES = new SystemProperty(false, "atlassian.bamboo.git.useShallowClones", "ATLASSIAN_BAMBOO_GIT_USE_SHALLOW_CLONES").getValue(true);

    static final int DEFAULT_COMMAND_TIMEOUT_IN_MINUTES = 180;

    // ------------------------------------------------------------------------------------------------- Type Properties

    private static final Logger log = Logger.getLogger(GitRepository.class);
    private BranchIntegrationHelper branchIntegrationHelper;

    private GitRepositoryAccessData accessData = new GitRepositoryAccessData();

    // Maven 2 import
    private transient String pathToPom;


    // ---------------------------------------------------------------------------------------------------- Dependencies
    private transient CapabilityContext capabilityContext;
    private transient I18nResolver i18nResolver;
    private transient GitCacheHandler gitCacheHandler;
    private transient SshProxyService sshProxyService;
    private transient EncryptionService encryptionService;
    private transient CredentialsAccessor credentialsAccessor;
    // ---------------------------------------------------------------------------------------------------- Constructors

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    @NotNull
    public String getName()
    {
        return i18nResolver.getText(REPOSITORY_GIT_NAME);
    }

    @Override
    public String getHost()
    {
        return "";
    }

    @Override
    public boolean isRepositoryDifferent(@NotNull Repository repository)
    {
        if (repository instanceof GitRepository)
        {
            GitRepository gitRepo = (GitRepository) repository;
            return !new EqualsBuilder()
                    .append(accessData.getRepositoryUrl(), gitRepo.accessData.getRepositoryUrl())
                    .append(accessData.getVcsBranch(), gitRepo.accessData.getVcsBranch())
                    .append(accessData.getUsername(), gitRepo.accessData.getUsername())
                    .append(accessData.getSshKey(), gitRepo.accessData.getSshKey())
                    .isEquals();
        }
        else
        {
            return true;
        }
    }

    @Override
    @NotNull
    public BuildRepositoryChanges collectChangesForRevision(@NotNull PlanKey planKey, @NotNull String targetRevision) throws RepositoryException
    {
        return collectChangesSinceLastBuild(planKey.getKey(), targetRevision, targetRevision);
    }

    @Override
    @NotNull
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable final String lastVcsRevisionKey) throws RepositoryException
    {
        return collectChangesSinceLastBuild(planKey, lastVcsRevisionKey, null);
    }

    @NotNull
    public BuildRepositoryChanges collectChangesSinceLastBuild(@NotNull String planKey, @Nullable final String lastVcsRevisionKey, @Nullable final String customRevision) throws RepositoryException
    {
        try
        {
            final BuildLogger buildLogger = buildLoggerManager.getLogger(PlanKeys.getPlanKey(planKey));
            final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
            final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, i18nResolver);

            final String latestRevision = helper.obtainLatestRevision();
            final String fetchRevision = customRevision != null ? customRevision : substitutedAccessData.getVcsBranch().getName();
            final String targetRevision = customRevision != null ? customRevision : latestRevision;

            if (latestRevision.equals(lastVcsRevisionKey) && customRevision == null)
            {
                return new BuildRepositoryChangesImpl(latestRevision);
            }

            final File cacheDirectory = getCacheDirectory();
            if (lastVcsRevisionKey == null)
            {
                buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.ccRepositoryNeverChecked", fetchRevision));
                try
                {
                    GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<Void>()
                    {
                        public Void call() throws Exception
                        {
                            try
                            {
                                helper.fetch(cacheDirectory, fetchRevision, false);
                            }
                            catch (Exception e)
                            {
                                rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.rsRecover.failedToFetchCache");
                                buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedCacheDirectory", cacheDirectory));

                                helper.fetch(cacheDirectory, fetchRevision, false);
                            }

                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RepositoryException(e.getMessage(), e);
                }
                return new BuildRepositoryChangesImpl(targetRevision);
            }

            final BuildRepositoryChanges buildChanges = GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Supplier<BuildRepositoryChanges>()
            {
                public BuildRepositoryChanges get()
                {
                    try
                    {
                        helper.fetch(cacheDirectory, fetchRevision, false);
                        return helper.extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                    }
                    catch (Exception e) // not just RepositoryException - see HandlingSwitchingRepositoriesToUnrelatedOnesTest.testCollectChangesWithUnrelatedPreviousRevision
                    {
                        try
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.ccRecover.failedToCollectChangesets");
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.ccRecover.cleanedCacheDirectory", cacheDirectory));
                            helper.fetch(cacheDirectory, fetchRevision, false);
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.ccRecover.fetchedRemoteRepository", cacheDirectory));
                            BuildRepositoryChanges extractedChanges = helper.extractCommits(cacheDirectory, lastVcsRevisionKey, targetRevision);
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.ccRecover.completed"));
                            return extractedChanges;
                        }
                        catch (Exception e2)
                        {
                            log.error(buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.ccRecover.failedToExtractChangesets")), e2);
                            return null;
                        }
                    }
                }
            });

            if (buildChanges != null && !buildChanges.getChanges().isEmpty())
            {
                return buildChanges;
            }
            else
            {
                return new BuildRepositoryChangesImpl(latestRevision, Collections.singletonList((CommitContext) CommitContextImpl.builder()
                        .author(Author.UNKNOWN_AUTHOR)
                        .comment(i18nResolver.getText("repository.git.messages.unknownChanges", lastVcsRevisionKey, targetRevision))
                        .date(new Date())
                        .build()));
            }
        }
        catch (RuntimeException e)
        {
            throw new RepositoryException(e.getMessage(), e);
        }
    }

    @Override
    @NotNull
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevisionKey, @NotNull final File sourceDirectory) throws RepositoryException
    {
        return retrieveSourceCode(buildContext, vcsRevisionKey, sourceDirectory, 1);
    }

    @Override
    @NotNull
    public String retrieveSourceCode(@NotNull final BuildContext buildContext, @Nullable final String vcsRevisionKey, @NotNull final File sourceDirectory, int depth) throws RepositoryException
    {
        try
        {
            final GitRepositoryAccessData.Builder substitutedAccessDataBuilder = getSubstitutedAccessDataBuilder();
            final boolean doShallowFetch = USE_SHALLOW_CLONES && accessData.isUseShallowClones() && depth == 1 && !isOnLocalAgent();
            substitutedAccessDataBuilder.useShallowClones(doShallowFetch);
            final GitRepositoryAccessData substitutedAccessData = substitutedAccessDataBuilder.build();

            final BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getPlanResultKey());
            final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, i18nResolver);

            final String revisionToCheckout = vcsRevisionKey != null ? vcsRevisionKey : helper.obtainLatestRevision();
            final String refToFetch = substitutedAccessData.getVcsBranch().getName();
            final String previousRevision = helper.getRevisionIfExists(sourceDirectory, Constants.HEAD);

            if (isOnLocalAgent() || substitutedAccessData.isUseRemoteAgentCache())
            {
                final File cacheDirectory = getCacheDirectory(substitutedAccessData);
                return GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<String>()
                {
                    public String call() throws Exception
                    {
                        try
                        {
                            helper.fetch(cacheDirectory, refToFetch, false);
                            helper.checkRevisionExistsInCacheRepository(cacheDirectory, revisionToCheckout);
                        }
                        catch (Exception e)
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.rsRecover.failedToFetchCache");
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedCacheDirectory", cacheDirectory));
                            helper.fetch(cacheDirectory, refToFetch, false);
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.fetchingCacheCompleted", cacheDirectory));
                        }

                        try
                        {
                            return helper.checkout(cacheDirectory, sourceDirectory, revisionToCheckout, previousRevision);
                        }
                        catch (Exception e)
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, sourceDirectory, "repository.git.messages.rsRecover.failedToCheckout");
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", sourceDirectory));
                            String returnRevision = helper.checkout(cacheDirectory, sourceDirectory, revisionToCheckout, null);
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.checkoutCompleted"));
                            return returnRevision;
                        }
                    }
                });

            }
            else //isOnRemoteAgent
            {
                try
                {
                    helper.fetch(sourceDirectory, refToFetch, doShallowFetch);
                    return helper.checkout(null, sourceDirectory, revisionToCheckout, previousRevision);
                }
                catch (Exception e)
                {
                    rethrowOrRemoveDirectory(e, buildLogger, sourceDirectory, "repository.git.messages.rsRecover.failedToCheckout");
                    buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", sourceDirectory));
                    helper.fetch(sourceDirectory, refToFetch, false);
                    buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.fetchingCompleted", sourceDirectory));
                    String returnRevision = helper.checkout(null, sourceDirectory, revisionToCheckout, null);
                    buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.checkoutCompleted"));
                    return returnRevision;
                }
            }
        }
        catch (RepositoryException e)
        {
            throw e;
        }
        catch (Exception e)
        {
            throw new RepositoryException(i18nResolver.getText("repository.git.messages.runtimeException"), e);
        }
    }

    private boolean isOnLocalAgent()
    {
        return !(buildDirectoryManager instanceof RemoteBuildDirectoryManager);
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
        final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
        final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, new NullBuildLogger(), i18nResolver);
        return helper.getOpenBranches(substitutedAccessData, getWorkingDirectory());
    }

    @Override
    public void pushRevision(@NotNull File sourceDirectory, @Nullable String vcsRevisionKey) throws RepositoryException
    {
        final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
        final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, new NullBuildLogger(), i18nResolver);
        helper.pushRevision(sourceDirectory, vcsRevisionKey);
    }
    
    @NotNull
    @Override
    public String commit(@NotNull File sourceDirectory, @NotNull String message) throws RepositoryException
    {
        final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
        final GitOperationHelper helper =
                GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, new NullBuildLogger(), i18nResolver);
        return helper.commit(sourceDirectory, message, branchIntegrationHelper.getCommitterName(this), branchIntegrationHelper.getCommitterEmail(this));
    }

    @Override
    public CacheId getCacheId(@NotNull final CachableOperation cachableOperation)
    {
        switch (cachableOperation)
        {
            case BRANCH_DETECTION:
                final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
                return new CacheId(this, substitutedAccessData.getRepositoryUrl(), substitutedAccessData.getUsername(), substitutedAccessData.getSshKey());
        }
        return null;
    }

    @Override
    public boolean isCachingSupportedFor(@NotNull final CachableOperation cachableOperation)
    {
        return cachableOperation==CachableOperation.BRANCH_DETECTION;
    }

    @Override
    @NotNull
    public VcsBranch getVcsBranch()
    {
        return accessData.getVcsBranch();
    }

    @Override
    public void setVcsBranch(@NotNull final VcsBranch branch)
    {
        this.accessData = GitRepositoryAccessData.builder(accessData).branch(branch).build();
    }

    @Override
    public boolean mergeWorkspaceWith(@NotNull final BuildContext buildContext, @NotNull final File workspaceDir, @NotNull final String targetRevision) throws RepositoryException
    {
        final BuildLogger buildLogger = buildLoggerManager.getLogger(buildContext.getEntityKey());
        final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessDataBuilder().useShallowClones(false).build();
        final GitOperationHelper connector = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, i18nResolver);

        final File cacheDirectory = getCacheDirectory(substitutedAccessData);

        try
        {
            if (isOnLocalAgent() || getAccessData().isUseRemoteAgentCache())
            {
                GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<Void>()
                {
                    @Override
                    public Void call() throws Exception
                    {
                        try
                        {
                            connector.fetch(cacheDirectory, targetRevision, false);
                            connector.checkRevisionExistsInCacheRepository(cacheDirectory, targetRevision);
                        }
                        catch (Exception e)
                        {
                            rethrowOrRemoveDirectory(e, buildLogger, cacheDirectory, "repository.git.messages.rsRecover.failedToFetchCache");
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedCacheDirectory", cacheDirectory));
                            connector.fetch(cacheDirectory, targetRevision, false);
                            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.fetchingCacheCompleted", cacheDirectory));
                        }
                        return null;
                    }
                });

            }
            else
            {
                try
                {
                    connector.fetch(workspaceDir, targetRevision, false);
                }
                catch (Exception e)
                {
                    rethrowOrRemoveDirectory(e, buildLogger, workspaceDir, "repository.git.messages.rsRecover.failedToFetchWorkingDir");
                    buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.rsRecover.cleanedSourceDirectory", workspaceDir));
                    connector.fetch(workspaceDir, targetRevision, false);
                }
            }
        }
        catch (Exception e)
        {
            throw new RepositoryException(i18nResolver.getText("repository.git.messages.runtimeException"), e);
        }

        return connector.merge(workspaceDir, targetRevision, branchIntegrationHelper.getCommitterName(this), branchIntegrationHelper.getCommitterEmail(this));
    }

    @Override
    public boolean isMergingSupported()
    {
        return GitOperationHelperFactory.isNativeGitEnabled(this);
    }

    @Override
    public CommitContext getFirstCommit() throws RepositoryException
    {
        return null;
    }

    @Override
    public CommitContext getLastCommit() throws RepositoryException
    {

        final BuildLogger buildLogger = new NullBuildLogger();
        final GitRepositoryAccessData substitutedAccessData = getSubstitutedAccessData();
        final GitOperationHelper helper = GitOperationHelperFactory.createGitOperationHelper(this, substitutedAccessData, sshProxyService, buildLogger, i18nResolver);

        final String targetRevision = helper.obtainLatestRevision();

        final File cacheDirectory = getCacheDirectory();
        Result<RepositoryException, CommitContext> result = GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Supplier<Result<RepositoryException, CommitContext>>()
        {
            public Result<RepositoryException, CommitContext> get()
            {
                try
                {
                    try
                    {
                        final CommitContext commit = helper.getCommit(cacheDirectory, targetRevision);
                        log.info("Found " + commit.getChangeSetId() + " as the last commit for " + this);
                        return Result.result(commit);
                    }
                    catch (RepositoryException e)
                    {
                        // Commit might not exist locally yet, but a fetch is expensive, so let's try getting it first
                        log.debug("Fetching remote repository");
                        helper.fetch(cacheDirectory, targetRevision, false);
                        return Result.result(helper.getCommit(cacheDirectory, targetRevision));
                    }
                }
                catch (RepositoryException e)
                {
                    return Result.exception(e);
                }
            }
        });
        return result.getResultThrowException();
    }

    @Override
    public void addDefaultValues(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, Integer.valueOf(DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        buildConfiguration.clearTree(REPOSITORY_GIT_VERBOSE_LOGS);
        buildConfiguration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, true);
        buildConfiguration.setProperty(REPOSITORY_GIT_USE_REMOTE_AGENT_CACHE, false);
        buildConfiguration.clearTree(REPOSITORY_GIT_USE_SUBMODULES);
    }

    @Override
    public void prepareConfigObject(@NotNull BuildConfiguration buildConfiguration)
    {
        buildConfiguration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, buildConfiguration.getInt(REPOSITORY_GIT_COMMAND_TIMEOUT, DEFAULT_COMMAND_TIMEOUT_IN_MINUTES));
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_PASSWORD_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GIT_PASSWORD, encryptionService.encrypt(buildConfiguration.getString(TEMPORARY_GIT_PASSWORD)));
        }
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_SSH_PASSPHRASE_CHANGE))
        {
            buildConfiguration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, encryptionService.encrypt(buildConfiguration.getString(TEMPORARY_GIT_SSH_PASSPHRASE)));
        }
        if (buildConfiguration.getBoolean(TEMPORARY_GIT_SSH_KEY_CHANGE))
        {
            final Object o = buildConfiguration.getProperty(TEMPORARY_GIT_SSH_KEY_FROM_FILE);
            if (o instanceof File)
            {
                final String key;
                try
                {
                    key = FileUtils.readFileToString((File) o);
                }
                catch (IOException e)
                {
                    log.error("Cannot read uploaded ssh key file", e);
                    return;
                }
                buildConfiguration.setProperty(REPOSITORY_GIT_SSH_KEY, encryptionService.encrypt(key));
            }
            else
            {
                buildConfiguration.clearProperty(REPOSITORY_GIT_SSH_KEY);
            }
        }
    }

    @Override
    public void populateFromConfig(@NotNull final HierarchicalConfiguration config)
    {
        super.populateFromConfig(config);

        final String sshPassphrase;
        final String sshKey;
        final GitAuthenticationType gitAuthenticationType;
        final Long sharedCredentialsId;

        final String chosenAuthentication = config.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.NONE.name());
        if (!chosenAuthentication.equals(SHARED_CREDENTIALS))
        {
            sharedCredentialsId = null;
            sshPassphrase = config.getString(REPOSITORY_GIT_SSH_PASSPHRASE);
            sshKey = config.getString(REPOSITORY_GIT_SSH_KEY, "");
            gitAuthenticationType = GitAuthenticationType.valueOf(chosenAuthentication);
        }
        else
        {
            sharedCredentialsId = config.getLong(REPOSITORY_GIT_SHAREDCREDENTIALS_ID, null);
            final CredentialsData credentials = credentialsAccessor.getCredentials(sharedCredentialsId);

            Preconditions.checkArgument(credentials != null, "Shared Credentials with id '" + sharedCredentialsId + " are not found");

            final PrivateKeyCredentials sshCredentials = new SshCredentialsImpl(credentials);
            sshKey = sshCredentials.getKey();
            sshPassphrase = sshCredentials.getPassphrase();
            gitAuthenticationType = getAuthenticationTypeForSharedCredentials(config);
        }
        
        final VcsBranchImpl branch = new VcsBranchImpl(StringUtils.defaultIfEmpty(config.getString(REPOSITORY_GIT_BRANCH, ""), "master"));
        
        accessData = GitRepositoryAccessData.builder()
                .repositoryUrl(StringUtils.trimToEmpty(config.getString(REPOSITORY_GIT_REPOSITORY_URL)))
                .username(config.getString(REPOSITORY_GIT_USERNAME, ""))
                .password(config.getString(REPOSITORY_GIT_PASSWORD, null))
                .branch(branch)
                .sshKey(sshKey)
                .sshPassphrase(sshPassphrase)
                .authenticationType(gitAuthenticationType)
                .useShallowClones(config.getBoolean(REPOSITORY_GIT_USE_SHALLOW_CLONES))
                .useRemoteAgentCache(config.getBoolean(REPOSITORY_GIT_USE_REMOTE_AGENT_CACHE, false))
                .useSubmodules(config.getBoolean(REPOSITORY_GIT_USE_SUBMODULES, false))
                .commandTimeout(config.getInt(REPOSITORY_GIT_COMMAND_TIMEOUT, DEFAULT_COMMAND_TIMEOUT_IN_MINUTES))
                .verboseLogs(config.getBoolean(REPOSITORY_GIT_VERBOSE_LOGS, false))
                .sharedCredentialsId(sharedCredentialsId)
                .build();

        pathToPom = config.getString(REPOSITORY_GIT_MAVEN_PATH);
    }

    @NotNull
    @Override
    public Iterable<Long> getSharedCredentialIds()
    {
        final Long sharedCredentialsId = accessData.getSharedCredentialsId();
        return sharedCredentialsId!=null ? ImmutableList.of(sharedCredentialsId) : Collections.<Long>emptyList();
    }

    @NotNull
    @Override
    public HierarchicalConfiguration toConfiguration()
    {
        final HierarchicalConfiguration configuration = super.toConfiguration();
        configuration.setProperty(REPOSITORY_GIT_REPOSITORY_URL, accessData.getRepositoryUrl());
        configuration.setProperty(REPOSITORY_GIT_BRANCH, accessData.getVcsBranch().getName());
        configuration.setProperty(REPOSITORY_GIT_USE_SHALLOW_CLONES, accessData.isUseShallowClones());
        configuration.setProperty(REPOSITORY_GIT_USE_REMOTE_AGENT_CACHE, accessData.isUseRemoteAgentCache());
        configuration.setProperty(REPOSITORY_GIT_USE_SUBMODULES, accessData.isUseSubmodules());
        configuration.setProperty(REPOSITORY_GIT_COMMAND_TIMEOUT, accessData.getCommandTimeout());
        configuration.setProperty(REPOSITORY_GIT_VERBOSE_LOGS, accessData.isVerboseLogs());

        final Long sharedCredentialsId = accessData.getSharedCredentialsId();
        if (sharedCredentialsId!=null)
        {
            configuration.setProperty(REPOSITORY_GIT_SHAREDCREDENTIALS_ID, sharedCredentialsId);
            configuration.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE, SHARED_CREDENTIALS);
        }
        else
        {
            configuration.setProperty(REPOSITORY_GIT_SSH_KEY, accessData.getSshKey());
            configuration.setProperty(REPOSITORY_GIT_SSH_PASSPHRASE, accessData.getSshPassphrase());
            configuration.setProperty(REPOSITORY_GIT_USERNAME, accessData.getUsername());
            configuration.setProperty(REPOSITORY_GIT_PASSWORD, accessData.getPassword());
            configuration.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE, accessData.getAuthenticationType().name());
        }
        return configuration;
    }

    @Override
    @NotNull
    public ErrorCollection validate(@NotNull BuildConfiguration buildConfiguration)
    {
        ErrorCollection errorCollection = super.validate(buildConfiguration);

        final String repositoryUrl = StringUtils.trim(buildConfiguration.getString(REPOSITORY_GIT_REPOSITORY_URL));
        final GitAuthenticationType authenticationType = getGitAuthenticationType(buildConfiguration);

        if (BambooFieldValidate.findFieldShellInjectionViolation(errorCollection, i18nResolver, REPOSITORY_GIT_REPOSITORY_URL, substituteString(buildConfiguration.getString(REPOSITORY_GIT_REPOSITORY_URL))))
        {
            return errorCollection;
        }
        if (BambooFieldValidate.findFieldShellInjectionViolation(errorCollection, i18nResolver, REPOSITORY_GIT_BRANCH, substituteString(buildConfiguration.getString(REPOSITORY_GIT_BRANCH))))
        {
            return errorCollection;
        }
        if (BambooFieldValidate.findFieldShellInjectionViolation(errorCollection, i18nResolver, REPOSITORY_GIT_USERNAME, substituteString(buildConfiguration.getString(REPOSITORY_GIT_USERNAME))))
        {
            return errorCollection;
        }

        if (StringUtils.isBlank(repositoryUrl))
        {
            errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, i18nResolver.getText("repository.git.messages.missingRepositoryUrl"));
        }
        else
        {
            TransportProtocol transportProtocol = TransportProtocol.of(repositoryUrl, TransportProtocol.SSH);
            if (!featureManager.isTransportSupported(transportProtocol))
            {
                errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL, TextProviderUtils.getText(i18nResolver, "repository.git.messages.unsupportedTransportProtocol", transportProtocol.toString()));
            }

            final boolean hasUsername = StringUtils.isNotBlank(buildConfiguration.getString(REPOSITORY_GIT_USERNAME));
            final boolean hasPassword = StringUtils.isNotBlank(buildConfiguration.getString(REPOSITORY_GIT_PASSWORD));
            try
            {
                final URIish uri = new URIish(repositoryUrl);
                if (authenticationType == GitAuthenticationType.SSH_KEYPAIR && ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme())))
                {
                    errorCollection.addError(REPOSITORY_GIT_AUTHENTICATION_TYPE, i18nResolver.getText("repository.git.messages.unsupportedHttpAuthenticationType"));
                }
                else if (authenticationType == GitAuthenticationType.PASSWORD)
                {
                    boolean duplicateUsername = hasUsername && StringUtils.isNotBlank(uri.getUser());
                    boolean duplicatePassword = hasPassword && StringUtils.isNotBlank(uri.getPass());
                    if (duplicateUsername || duplicatePassword)
                    {
                        errorCollection.addError(REPOSITORY_GIT_REPOSITORY_URL,
                                (duplicateUsername ? i18nResolver.getText("repository.git.messages.duplicateUsernameField") : "")
                                        + ((duplicateUsername && duplicatePassword) ? " " : "")
                                        + (duplicatePassword ? i18nResolver.getText("repository.git.messages.duplicatePasswordField") : ""));
                    }
                    if (duplicateUsername)
                    {
                        errorCollection.addError(REPOSITORY_GIT_USERNAME, i18nResolver.getText("repository.git.messages.duplicateUsernameField"));
                    }
                    if (duplicatePassword)
                    {
                        errorCollection.addError(TEMPORARY_GIT_PASSWORD_CHANGE, i18nResolver.getText("repository.git.messages.duplicatePasswordField"));
                    }
                    if (uri.getHost() == null && hasUsername)
                    {
                        errorCollection.addError(REPOSITORY_GIT_USERNAME, i18nResolver.getText("repository.git.messages.unsupportedUsernameField"));
                    }
                }
            }
            catch (URISyntaxException e)
            {
                if (hasUsername)
                {
                    errorCollection.addError(REPOSITORY_GIT_USERNAME, i18nResolver.getText("repository.git.messages.unsupportedUsernameField"));
                }
            }
        }

        if (buildConfiguration.getString(REPOSITORY_GIT_MAVEN_PATH, "").contains(".."))
        {
            errorCollection.addError(REPOSITORY_GIT_MAVEN_PATH, i18nResolver.getText("repository.git.messages.invalidPomPath"));
        }

        return errorCollection;
    }

    private GitAuthenticationType getGitAuthenticationType(final BuildConfiguration buildConfiguration)
    {
        final String chosenAuthentication = buildConfiguration.getString(REPOSITORY_GIT_AUTHENTICATION_TYPE);
        if (chosenAuthentication.equals(SHARED_CREDENTIALS))
        {
            return getAuthenticationTypeForSharedCredentials(buildConfiguration);
        }
        return GitAuthenticationType.valueOf(chosenAuthentication);
    }

    private GitAuthenticationType getAuthenticationTypeForSharedCredentials(final AbstractConfiguration buildConfiguration)
    {
        return GitAuthenticationType.SSH_KEYPAIR;
    }

    @NotNull
    @Override
    public Map<String, String> getCustomVariables()
    {
        Map<String, String> variables = Maps.newHashMap();
        variables.put(REPOSITORY_GIT_REPOSITORY_URL, accessData.getRepositoryUrl());
        variables.put(REPOSITORY_GIT_BRANCH, accessData.getVcsBranch().getName());
        variables.put(REPOSITORY_GIT_USERNAME, accessData.getUsername());
        return variables;
    }

    @NotNull
    @Override
    public Map<String, String> getPlanRepositoryVariables()
    {
        Map<String, String> variables = Maps.newHashMap();
        variables.put(REPOSITORY_URL, accessData.getRepositoryUrl());
        variables.put(REPOSITORY_BRANCH, accessData.getVcsBranch().getName());
        variables.put(REPOSITORY_USERNAME, accessData.getUsername());
        return variables;
    }

    @NotNull
    public MavenPomAccessor getMavenPomAccessor()
    {
        return new GitMavenPomAccessor(this, sshProxyService, i18nResolver, getGitCapability()).withPath(pathToPom);
    }

    @Override
    @NotNull
    public List<NameValuePair> getAuthenticationTypes()
    {
        final List<NameValuePair> authTypes = Lists.newArrayList();
        for (final GitAuthenticationType gitAuthenticationType : GitAuthenticationType.values())
        {
            final String name = gitAuthenticationType.name();
            authTypes.add(new NameValuePair(name, getAuthTypeName(name)));
        }

        if (!getSharedCredentials().isEmpty())
        {
            authTypes.add(new NameValuePair(SHARED_CREDENTIALS, getAuthTypeName(SHARED_CREDENTIALS)));
        }
        return authTypes;
        
    }
    
    @NotNull
    public Collection<NameValuePair> getSharedCredentials()
    {
        return ImmutableList.copyOf(Iterables.transform(credentialsAccessor.getAllCredentials(), new Function<CredentialsData, NameValuePair>() {
            public NameValuePair apply(CredentialsData credentials)
            {
                return new NameValuePair(Long.toString(credentials.getId()), credentials.getName());
            }
        }));
    }

    @Override
    public String getAuthType()
    {
        return accessData.getAuthenticationType().name();
    }

    // -------------------------------------------------------------------------------------------------- Public Methods

    // -------------------------------------------------------------------------------------------------- Helper Methods

    private String getAuthTypeName(final String authType)
    {
        return i18nResolver.getText("repository.git.authenticationType." + StringUtils.lowerCase(authType));
    }

    GitRepositoryAccessData.Builder getSubstitutedAccessDataBuilder()
    {
        return GitRepositoryAccessData.builder(accessData)
                .repositoryUrl(substituteString(accessData.getRepositoryUrl()))
                .branch(new VcsBranchImpl(substituteString(accessData.getVcsBranch().getName())))
                .username(substituteString(accessData.getUsername()))
                .password(encryptionService.decrypt(accessData.getPassword()))
                .sshKey(encryptionService.decrypt(accessData.getSshKey()))
                .sshPassphrase(encryptionService.decrypt(accessData.getSshPassphrase()));             
    }

    GitRepositoryAccessData getSubstitutedAccessData()
    {
        return getSubstitutedAccessDataBuilder().build();
    }

    private void rethrowOrRemoveDirectory(final Exception originalException, final BuildLogger buildLogger, final File directory, final String key) throws Exception
    {
        Throwable e = originalException;
        do
        {
            if (e instanceof TransportException)
            {
                throw originalException;
            }
            e = e.getCause();
        } while (e!=null);

        buildLogger.addBuildLogEntry(i18nResolver.getText(key, directory));
        log.warn("Deleting directory " + directory, e);

        // This section does not really work on Windows (files open by antivirus software or leaked by jgit - and it does leak handles - will remain on the harddrive),
        // so it should be entered if we know that the cache has to be blown away
        FileUtils.deleteQuietly(directory);

        final String[] filesInDirectory = directory.list();
        if (filesInDirectory !=null)
        {
            log.error("Unable to delete files: " + Arrays.toString(filesInDirectory) + ", expect trouble");
        }
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    public boolean isUseShallowClones()
    {
        return accessData.isUseShallowClones();
    }

    public boolean isUseSubmodules()
    {
        return accessData.isUseSubmodules();
    }

    public String getRepositoryUrl()
    {
        return accessData.getRepositoryUrl();
    }

    /**
     * @deprecated since 4.0 use {@link com.atlassian.bamboo.repository.BranchAwareRepository methods)}
     * @return
     */
    @Deprecated
    public String getBranch()
    {
        return accessData.getBranch();
    }

    public int getCommandTimeout()
    {
        return accessData.getCommandTimeout();
    }

    public boolean getVerboseLogs()
    {
        return accessData.isVerboseLogs();
    }

    public String getAuthTypeName()
    {
        return getAuthTypeName(getAuthType());
    }

    public File getCacheDirectory()
    {
        return getCacheDirectory(getSubstitutedAccessData());
    }

    public File getCacheDirectory(GitRepositoryAccessData accessData)
    {
        return GitCacheDirectory.getCacheDirectory(buildDirectoryManager.getBaseBuildWorkingDirectory(), accessData);
    }

    public void setI18nResolver(I18nResolver i18nResolver)
    {
        this.i18nResolver = i18nResolver;
    }

    public void setEncryptionService(EncryptionService encryptionService)
    {
        this.encryptionService = encryptionService;
    }
    
    public void setCredentialsAccessor(final CredentialsAccessor credentialsAccessor)
    {
        this.credentialsAccessor = credentialsAccessor;
    }
    
    public String getOptionDescription()
    {
        String capabilitiesLink = ServletActionContext.getRequest().getContextPath() +
                                  "/admin/agent/configureSharedLocalCapabilities.action";
        return i18nResolver.getText("repository.git.description", getGitCapability(), capabilitiesLink);
    }

    // Git capability is optional, so we don't enforce it here
    @Override
    public Set<Requirement> getRequirements()
    {
        return Sets.newHashSet();
    }

    @Nullable
    public String getGitCapability()
    {
        return capabilityContext.getCapabilityValue(GitCapabilityTypeModule.GIT_CAPABILITY);
    }

    @Nullable
    public String getSshCapability()
    {
        return capabilityContext.getCapabilityValue(GitCapabilityTypeModule.SSH_CAPABILITY);
    }

    public void setCapabilityContext(final CapabilityContext capabilityContext)
    {
        this.capabilityContext = capabilityContext;
    }

    public void setSshProxyService(SshProxyService sshProxyService)
    {
        this.sshProxyService = sshProxyService;
    }

    public void setBranchIntegrationHelper(final BranchIntegrationHelper branchIntegrationHelper)
    {
        this.branchIntegrationHelper = branchIntegrationHelper;
    }

    public GitRepositoryAccessData getAccessData()
    {
        return accessData;
    }
    public void setGitCacheHandler(final GitCacheHandler gitCacheHandler)
    {
        this.gitCacheHandler = gitCacheHandler;
    }

    public void setAccessData(final GitRepositoryAccessData accessData)
    {
        this.accessData = accessData;
    }
    @NotNull
    @Override
    public String getHandlerDescription()
    {
        return i18nResolver.getText("manageCaches.git.description");
    }

    /**
     * {@inheritDoc}
     * Handles both Git and GitHub repositories.
     */
    @NotNull
    @Override
    public Collection<CacheDescription> getCacheDescriptions()
    {
        return gitCacheHandler.getCacheDescriptions();
    }

    /**
     * {@inheritDoc}
     * Handles both Git and GitHub repositories.
     */
    @Override
    public void deleteCaches(@NotNull final Collection<String> strings, @NotNull final ValidationAware validationAware)
    {
        gitCacheHandler.deleteCaches(strings, validationAware);
    }

    /**
     * {@inheritDoc}
     * Handles both Git and GitHub repositories.
     */
    @Override
    public void deleteUnusedCaches(@NotNull final ValidationAware validationAware)
    {
        gitCacheHandler.deleteUnusedCaches(validationAware);
    }
}
