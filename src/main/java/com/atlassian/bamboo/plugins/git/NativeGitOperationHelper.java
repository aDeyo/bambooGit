package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.repository.InvalidRepositoryException;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.ProxyConnectionData;
import com.atlassian.bamboo.ssh.ProxyConnectionDataBuilder;
import com.atlassian.bamboo.ssh.ProxyException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.util.PasswordMaskingUtils;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.atlassian.bamboo.v2.build.BuildRepositoryChangesImpl;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.annotations.VisibleForTesting;
import com.google.common.cache.CacheBuilder;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.LineIterator;
import org.apache.commons.lang.StringUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.storage.file.RefDirectory;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class NativeGitOperationHelper extends AbstractGitOperationHelper implements GitOperationHelper
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(NativeGitOperationHelper.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    protected SshProxyService sshProxyService;
    GitCommandProcessor gitCommandProcessor;

    private final static CallableResultCache<ImmutableMap<String, String>> GET_REMOTE_REFS_CACHE =
            CallableResultCache.build(CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.SECONDS));

    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public NativeGitOperationHelper(final @NotNull GitRepository repository,
                                    final @NotNull GitRepositoryAccessData accessData,
                                    final @NotNull SshProxyService sshProxyService,
                                    final @NotNull BuildLogger buildLogger,
                                    final @NotNull I18nResolver i18nResolver) throws RepositoryException
    {
        super(accessData, buildLogger, i18nResolver);
        this.sshProxyService = sshProxyService;
        this.gitCommandProcessor = new GitCommandProcessor(repository.getGitCapability(), buildLogger, accessData.getPassword(),
                                                           accessData.getCommandTimeout(), accessData.isVerboseLogs());
        this.gitCommandProcessor.checkGitExistenceInSystem(repository.getWorkingDirectory());
        this.gitCommandProcessor.setSshCommand(repository.getSshCapability());
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    public void pushRevision(@NotNull final File sourceDirectory, @NotNull String revision) throws RepositoryException
    {
        String possibleBranch = gitCommandProcessor.getPossibleBranchNameForCheckout(sourceDirectory, revision, accessData.getVcsBranch().getName());
        if (StringUtils.isBlank(possibleBranch))
        {
            throw new RepositoryException("Can't guess branch name for revision " + revision + " when trying to perform push.");
        }
        final GitRepositoryAccessData proxiedAccessData = adjustRepositoryAccess(accessData);
        GitCommandBuilder commandBuilder = gitCommandProcessor.createCommandBuilder("push", proxiedAccessData.getRepositoryUrl(), possibleBranch);
        if (proxiedAccessData.isVerboseLogs())
        {
            commandBuilder.verbose(true);
        }
        gitCommandProcessor.runCommand(commandBuilder, sourceDirectory);
    }

    @Override
    public String commit(@NotNull File sourceDirectory, @NotNull String message, @NotNull String comitterName, @NotNull String comitterEmail) throws RepositoryException
    {
        if (!containsSomethingToCommit(sourceDirectory))
        {
            log.debug("Nothing to commit");
            return getCurrentRevision(sourceDirectory);
        }

        GitCommandBuilder commandBuilder = gitCommandProcessor
                .createCommandBuilder("commit", "--all", "-m", message)
                .env(identificationVariables(comitterName, comitterEmail));

        if (accessData.isVerboseLogs())
        {
            commandBuilder.verbose(true);
        }
        gitCommandProcessor.runCommand(commandBuilder, sourceDirectory);
        return getCurrentRevision(sourceDirectory);
    }

    public ImmutableMap<String, String> identificationVariables(@NotNull String name, @NotNull String email)
    {
        return ImmutableMap.of(
                "GIT_COMMITTER_NAME", name, //needed for merge
                "GIT_COMMITTER_EMAIL", email, //otherwise warning on commit
                "GIT_AUTHOR_NAME", name, //needed for commit
                "GIT_AUTHOR_EMAIL", email); //not required
    }

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators

    @VisibleForTesting
    protected GitRepositoryAccessData adjustRepositoryAccess(@NotNull final GitRepositoryAccessData accessData) throws RepositoryException
    {
        final boolean sshKeypair = accessData.getAuthenticationType() == GitAuthenticationType.SSH_KEYPAIR;
        final boolean sshWithPassword = UriUtils.requiresSshTransport(accessData.getRepositoryUrl()) && accessData.getAuthenticationType() == GitAuthenticationType.PASSWORD;
        final boolean needsProxy = sshKeypair || sshWithPassword;
        if (needsProxy)
        {
            final GitRepositoryAccessData.Builder proxyAccessDataBuilder = GitRepositoryAccessData.builder(accessData);
            GitRepositoryAccessData proxyAccessData = proxyAccessDataBuilder.build();

            ScpAwareUri repositoryUri = ScpAwareUri.create(accessData.getRepositoryUrl());

            if (UriUtils.requiresSshTransport(repositoryUri))
            {
                try
                {
                    String username = UriUtils.extractUsername(accessData.getRepositoryUrl());
                    if (username != null)
                    {
                        proxyAccessData.setUsername(username);
                    }

                    final ProxyConnectionDataBuilder proxyConnectionDataBuilder =
                            sshProxyService.createProxyConnectionDataBuilder()
                                    .withRemoteHost(repositoryUri.getHost())
                                    .withRemotePort(repositoryUri.getPort() == -1 ? null : repositoryUri.getPort())
                                    .withRemoteUserName(StringUtils.defaultIfEmpty(proxyAccessData.getUsername(), repositoryUri.getUserInfo()))
                                    .withErrorReceiver(gitCommandProcessor);

                    if (repositoryUri.isRelativePath())
                    {
                        proxyConnectionDataBuilder.withRemotePathMapping(repositoryUri.getAbsolutePath(), repositoryUri.getRawPath());
                    }

                    switch (accessData.getAuthenticationType())
                    {
                        case SSH_KEYPAIR:
                            proxyConnectionDataBuilder.withKeyFromString(proxyAccessData.getSshKey(), proxyAccessData.getSshPassphrase());
                            break;
                        case PASSWORD:
                            proxyConnectionDataBuilder.withRemotePassword(StringUtils.defaultString(proxyAccessData.getPassword()));
                            break;
                        default:
                            throw new IllegalArgumentException("Proxy does not know how to handle " + accessData.getAuthenticationType());
                    }

                    final ProxyConnectionData connectionData = proxyConnectionDataBuilder.build();

                    proxyAccessData.setProxyRegistrationInfo(sshProxyService.register(connectionData));
                    final URI repositoryViaProxy = UriUtils.getUriViaProxy(proxyAccessData, repositoryUri);
                    proxyAccessData.setRepositoryUrl(repositoryViaProxy.toString());
                }
                catch (IOException e)
                {
                    if (e.getMessage().contains("exception using cipher - please check password and data."))
                    {
                        throw new RepositoryException(buildLogger.addErrorLogEntry("Encryption exception - please check ssh keyfile passphrase."), e);
                    }
                    else
                    {
                        throw new RepositoryException("Cannot decode connection params", e);
                    }
                }
                catch (ProxyException e)
                {
                    throw new RepositoryException("Cannot create SSH proxy", e);
                }
                catch (URISyntaxException e)
                {
                    throw new RepositoryException("Remote repository URL invalid", e);
                }

                return proxyAccessData;
            }
        }
        else
        {
            final GitRepositoryAccessData credentialsAwareAccessData = GitRepositoryAccessData.builder(accessData).build();
            final String repositoryUrl = getUrlWithNormalisedCredentials(credentialsAwareAccessData);
            credentialsAwareAccessData.setRepositoryUrl(repositoryUrl);
            return credentialsAwareAccessData;
        }

        return accessData;
    }


    /**
     * @return true if modified files exist in the directory or current revision in the directory has changed
     */
    @Override
    public boolean merge(@NotNull final File workspaceDir, @NotNull final String targetRevision,
                         @NotNull String committerName, @NotNull String committerEmail) throws RepositoryException
    {
        GitCommandBuilder commandBuilder =
                gitCommandProcessor
                        .createCommandBuilder("merge", "--no-commit", targetRevision)
                        .env(identificationVariables(committerName, committerEmail));

        String headRevisionBeforeMerge = getCurrentRevision(workspaceDir);
        gitCommandProcessor.runMergeCommand(commandBuilder, workspaceDir);

        if (containsSomethingToCommit(workspaceDir))
        {
            return true;
        }
        //fast forward merge check
        String headRevisionAfterMerge = getCurrentRevision(workspaceDir);
        log.debug("Revision before merge: " + headRevisionBeforeMerge + ", after merge: " + headRevisionAfterMerge);
        return !headRevisionAfterMerge.equals(headRevisionBeforeMerge);
    }

    private boolean containsSomethingToCommit(@NotNull File workspaceDir) throws RepositoryException
    {
        //check for merge with no changes to files, but with changes to index
        final String mergeHead = getRevisionIfExists(workspaceDir, Constants.MERGE_HEAD);
        if (mergeHead!=null)
        {
            log.debug("Has modified index");
            return true;
        }

        final List<String> strings = gitCommandProcessor.runStatusCommand(workspaceDir);
        final boolean hasModifiedFiles = !strings.isEmpty();
        if (hasModifiedFiles)
        {
            log.debug("Has modified files");
        }
        return hasModifiedFiles;
    }

    @NotNull
    private String getUrlWithNormalisedCredentials(final GitRepositoryAccessData repositoryAccessData)
    {
        try
        {
            final URIish repositoryLocation = new URIish(repositoryAccessData.getRepositoryUrl());

            final URIish normalisedUri = UriUtils.normaliseRepositoryLocation(repositoryAccessData.getUsername(), repositoryAccessData.getPassword(), repositoryLocation);

            return normalisedUri.toPrivateString();
        }
        catch (URISyntaxException e)
        {
            // can't really happen
            final String message = "Cannot parse remote URI: " + repositoryAccessData.getRepositoryUrl();
            NativeGitOperationHelper.log.error(message, e);
            throw new RuntimeException(e);
        }
    }

    private void createLocalRepository(final File sourceDirectory, final File cacheDirectory) throws RepositoryException, IOException
    {
        if (!sourceDirectory.exists())
        {
            sourceDirectory.mkdirs();
        }
        File gitDirectory = new File(sourceDirectory, Constants.DOT_GIT);
        String headRef = null;
        File cacheGitDir = null;
        File alternateObjectDir = null;
        if (cacheDirectory != null && cacheDirectory.exists())
        {
            cacheGitDir = new File(cacheDirectory, Constants.DOT_GIT);
            File objectsCache = new File(cacheGitDir, "objects");
            if (objectsCache.exists())
            {
                alternateObjectDir = objectsCache;
                headRef = FileUtils.readFileToString(new File(cacheGitDir, Constants.HEAD));
            }
        }

        if (!gitDirectory.exists())
        {
            buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.creatingGitRepository", gitDirectory));
            gitCommandProcessor.runInitCommand(sourceDirectory);
        }

        // lets update alternatives here for a moment
        if (alternateObjectDir !=null)
        {
            List<String> alternatePaths = new ArrayList<String>(1);
            alternatePaths.add(alternateObjectDir.getAbsolutePath());
            final File alternates = new File(new File(new File(gitDirectory, "objects"), "info"), "alternates");
            FileUtils.writeLines(alternates, alternatePaths, "\n");
        }

        if (cacheGitDir != null && cacheGitDir.isDirectory())
        {
            // copy tags and branches heads from the cache repository
            FileUtils.copyDirectoryToDirectory(new File(cacheGitDir, Constants.R_TAGS), new File(gitDirectory, Constants.R_REFS));
            FileUtils.copyDirectoryToDirectory(new File(cacheGitDir, Constants.R_HEADS), new File(gitDirectory, Constants.R_REFS));

            File shallow = new File(cacheGitDir, "shallow");
            if (shallow.exists())
            {
                FileUtils.copyFileToDirectory(shallow, gitDirectory);
            }
        }

        if (StringUtils.startsWith(headRef, RefDirectory.SYMREF))
        {
            FileUtils.writeStringToFile(new File(gitDirectory, Constants.HEAD), headRef);
        }
    }

    protected void closeProxy(@NotNull final GitRepositoryAccessData accessData)
    {
        sshProxyService.unregister(accessData.getProxyRegistrationInfo());
    }

    @NotNull
    @Override
    public String checkout(@Nullable final File cacheDirectory, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision) throws RepositoryException
    {
        // would be cool to store lastCheckoutedRevision in the localRepository somehow - so we don't need to specify it
        buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.checkingOutRevision", targetRevision));

        try
        {
            createLocalRepository(sourceDirectory, cacheDirectory);
            //try to clean .git/index.lock file prior to checkout, otherwise checkout would fail with Exception
            File lck = new File(sourceDirectory, "index.lock");
            FileUtils.deleteQuietly(lck);

            gitCommandProcessor.runCheckoutCommand(sourceDirectory, targetRevision, accessData.getVcsBranch().getName());
            if (accessData.isUseSubmodules())
            {
                gitCommandProcessor.runSubmoduleUpdateCommand(sourceDirectory);
            }
            return targetRevision;
        }
        catch (Exception e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(i18nResolver.getText("repository.git.messages.checkoutFailed", targetRevision)) + e.getMessage(), e);
        }
    }

    @Override
    public void fetch(@NotNull final File sourceDirectory, @NotNull String targetBranchOrRevision, final boolean useShallow) throws RepositoryException
    {
        final String[] refSpecDescription = {"(unresolved) " + targetBranchOrRevision};
        try
        {
            createLocalRepository(sourceDirectory, null);
            final GitRepositoryAccessData proxiedAccessData = adjustRepositoryAccess(accessData);

            try
            {
                final String resolvedRefSpec;
                if (StringUtils.startsWithAny(targetBranchOrRevision, FQREF_PREFIXES))
                {
                    resolvedRefSpec = targetBranchOrRevision;
                }
                else
                {
                    final Pair<String, String> symbolicRefAndHash = resolveBranch(accessData, proxiedAccessData, sourceDirectory, targetBranchOrRevision);
                    if (symbolicRefAndHash==null)
                    {
                        resolvedRefSpec=Constants.R_HEADS + "*"; //assume it's an SHA hash, so we need to fetch all
                    }
                    else
                    {
                        resolvedRefSpec=symbolicRefAndHash.first;
                    }
                }
                refSpecDescription[0] = resolvedRefSpec;

                buildLogger.addBuildLogEntry(i18nResolver.getText("repository.git.messages.fetching", resolvedRefSpec, accessData.getRepositoryUrl())
                                             + (useShallow ? " " + i18nResolver.getText("repository.git.messages.doingShallowFetch") : ""));

                gitCommandProcessor.runFetchCommand(sourceDirectory, proxiedAccessData, "+"+resolvedRefSpec+":"+resolvedRefSpec, useShallow);
            }
            finally
            {
                closeProxy(proxiedAccessData);
            }
        }
        catch (Exception e)
        {
            String message = i18nResolver.getText("repository.git.messages.fetchingFailed", accessData.getRepositoryUrl(), refSpecDescription[0], sourceDirectory);
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
    }

    @Nullable
    private Pair<String, String> resolveBranch(@NotNull final GitRepositoryAccessData directAccessData,
                                               @Nullable final GitRepositoryAccessData proxiedAccessData,
                                               final File sourceDirectory,
                                               final String branch) throws RepositoryException
    {
        final ImmutableMap<String, String> remoteRefs = getRemoteRefs(sourceDirectory, directAccessData, proxiedAccessData);
        final Collection<String> candidates;
        if (StringUtils.isBlank(branch))
        {
            candidates = Arrays.asList(Constants.R_HEADS + Constants.MASTER, Constants.HEAD);
        }
        else if (StringUtils.startsWithAny(branch, FQREF_PREFIXES))
        {
            candidates = Collections.singletonList(branch);
        }
        else
        {
            candidates = Arrays.asList(branch, Constants.R_HEADS + branch, Constants.R_TAGS + branch);
        }
        for (final String symbolicName : candidates)
        {
            final String hash = remoteRefs.get(symbolicName);
            if (hash!=null)
            {
                return Pair.make(symbolicName, hash);
            }
        }
        return null;
    }

    @NotNull
    @Override
    public List<VcsBranch> getOpenBranches(@NotNull final GitRepositoryAccessData repositoryData, final File workingDir) throws RepositoryException
    {
        final ImmutableMap<String, String> refs = getRemoteRefs(workingDir, repositoryData, null);
        final List<VcsBranch> openBranches = Lists.newArrayList();
        for (final String refSymbolicName : refs.keySet())
        {
            if (refSymbolicName.startsWith(Constants.R_HEADS))
            {
                openBranches.add(new VcsBranchImpl(refSymbolicName.substring(Constants.R_HEADS.length())));
            }
        }
        return openBranches;
    }

    private ImmutableMap<String, String> getRemoteRefs(final File workingDir, @NotNull final GitRepositoryAccessData accessData, @Nullable final GitRepositoryAccessData proxiedAccessData) throws RepositoryException
    {
        final Callable<ImmutableMap<String, String>> getRemoteRefs = new Callable<ImmutableMap<String, String>>()
        {
            @Override
            public ImmutableMap<String, String> call() throws Exception
            {
                final boolean createNewProxySession = proxiedAccessData == null;
                final GitRepositoryAccessData accessDataToUse = createNewProxySession ? adjustRepositoryAccess(accessData) : proxiedAccessData;

                try
                {
                    return gitCommandProcessor.getRemoteRefs(workingDir, accessDataToUse);
                }
                finally
                {
                    if (createNewProxySession)
                    {
                        closeProxy(accessDataToUse);
                    }
                }
            }
        };

        final ImmutableMap<String, String> callResult = GET_REMOTE_REFS_CACHE.call(getRemoteRefs, accessData.getRepositoryUrl(), accessData.getUsername(), accessData.getSshKey());
        if (log.isDebugEnabled())
        {
            log.debug(GET_REMOTE_REFS_CACHE.stats());
        }
        return callResult;
    }

    @NotNull
    @Override
    public String getCurrentRevision(@NotNull final File sourceDirectory) throws RepositoryException
    {
        return gitCommandProcessor.getRevisionHash(sourceDirectory, Constants.HEAD);
    }

    @Override
    public String getRevisionIfExists(@NotNull final File sourceDirectory, @NotNull final String revision)
    {
        try
        {
            return gitCommandProcessor.getRevisionHash(sourceDirectory, revision);
        }
        catch (RepositoryException e)
        {
            return null;
        }
    }

    @NotNull
    @Override
    public String obtainLatestRevision() throws RepositoryException
    {
        final File workingDir = new File(".");
        final Pair<String, String> branchRef = resolveBranch(accessData, null, workingDir, accessData.getVcsBranch().getName());
        if (branchRef==null)
        {
            throw new InvalidRepositoryException(i18nResolver.getText("repository.git.messages.cannotDetermineHead",
                                                                      PasswordMaskingUtils.mask(accessData.getRepositoryUrl(), accessData.getPassword()), accessData.getVcsBranch().getName()));
        }

        return branchRef.second;
    }

    @Override
    public boolean checkRevisionExistsInCacheRepository(@NotNull final File repositoryDirectory, @NotNull final String targetRevision) throws RepositoryException
    {
        return targetRevision.equals(gitCommandProcessor.getRevisionHash(repositoryDirectory, targetRevision));
    }

    @Override
    @NotNull
    public CommitContext getCommit(final File directory, final String targetRevision) throws RepositoryException
    {
        return gitCommandProcessor.extractCommit(directory, targetRevision);
    }

    @Override
    public BuildRepositoryChanges extractCommits(final File cacheDirectory, final String lastVcsRevisionKey, final String targetRevision) throws RepositoryException
    {
        Pair<List<CommitContext>, Integer> result = gitCommandProcessor.runLogCommand(cacheDirectory, lastVcsRevisionKey, targetRevision, getShallows(cacheDirectory), CHANGESET_LIMIT);
        BuildRepositoryChanges buildChanges = new BuildRepositoryChangesImpl(targetRevision, result.getFirst());
        buildChanges.setSkippedCommitsCount(result.getSecond());
        return buildChanges;
    }

    private Set<String> getShallows(final File cacheDirectory)
    {
        File shallowFile = new File(new File(cacheDirectory, ".git"), "shallow");
        if (shallowFile.exists())
        {
            try
            {
                LineIterator shallowFileContent = FileUtils.lineIterator(shallowFile);
                try
                {
                    Set<String> result = Sets.newHashSet();
                    while (shallowFileContent.hasNext())
                    {
                        String aShallow = shallowFileContent.nextLine();
                        if (!StringUtils.isBlank(aShallow))
                        {
                            result.add(aShallow.trim());
                        }
                    }
                    return result;
                }
                finally
                {
                    LineIterator.closeQuietly(shallowFileContent);
                }
            }
            catch (IOException e)
            {
                log.warn("Cannot read 'shallow' file " + shallowFile.getAbsolutePath());
            }
        }
        return Collections.emptySet();
    }
}
