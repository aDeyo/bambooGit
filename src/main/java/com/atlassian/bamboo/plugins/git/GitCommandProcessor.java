package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.commit.CommitContext;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.ProxyErrorReceiver;
import com.atlassian.bamboo.util.BambooFileUtils;
import com.atlassian.bamboo.util.BambooFilenameUtils;
import com.atlassian.bamboo.util.BambooStringUtils;
import com.atlassian.bamboo.util.Narrow;
import com.atlassian.bamboo.util.PasswordMaskingUtils;
import com.atlassian.bamboo.utils.Pair;
import com.atlassian.utils.process.ExternalProcess;
import com.atlassian.utils.process.ExternalProcessBuilder;
import com.atlassian.utils.process.LineOutputHandler;
import com.atlassian.utils.process.OutputHandler;
import com.atlassian.utils.process.PluggableProcessHandler;
import com.atlassian.utils.process.StringOutputHandler;
import com.google.common.base.Preconditions;
import com.google.common.base.Splitter;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.cache.CacheLoader;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.commons.lang.SystemUtils;
import org.apache.log4j.Logger;
import org.eclipse.jgit.lib.Constants;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class GitCommandProcessor implements Serializable, ProxyErrorReceiver
{
    private static final Logger log = Logger.getLogger(GitCommandProcessor.class);

    // ------------------------------------------------------------------------------------------------------- Constants
    public static final String GIT_OUTPUT_ENCODING = "UTF-8";
    private static final String ENCODING_OPTION = "--encoding=" + GIT_OUTPUT_ENCODING;
    private static final Pattern GIT_VERSION_PATTERN = Pattern.compile("^git version (.*)");
    private static final Pattern LS_REMOTE_LINE_PATTERN = Pattern.compile("^([0-9a-f]{40})\\s+(.*)");

    private static final String SSH_OPTIONS = "-o StrictHostKeyChecking=no -o BatchMode=yes -o UserKnownHostsFile=/dev/null";
    private static final String SSH_WIN =
            "@ssh " + SSH_OPTIONS + " %*\r\n";

    private static final String SSH_UNIX =
            "#!/bin/sh\n" +
                    "exec ssh " + SSH_OPTIONS + " $@\n";

    private static final String REMOTE_ORIGIN = Constants.R_REMOTES + Constants.DEFAULT_REMOTE_NAME + '/';

    // ------------------------------------------------------------------------------------------------- Type Properties

    private final String gitExecutable;
    private final BuildLogger buildLogger;
    private final String passwordToObfuscate;
    private final int commandTimeoutInMinutes;
    private final boolean maxVerboseOutput;
    private String proxyErrorMessage;
    private Throwable proxyException;
    private String sshCommand;

    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    public GitCommandProcessor(@NotNull final String gitExecutable, @NotNull final BuildLogger buildLogger, @Nullable String passwordToObfuscate, final int commandTimeoutInMinutes, boolean maxVerboseOutput)
    {
        this.gitExecutable = gitExecutable;
        this.buildLogger = buildLogger;
        this.passwordToObfuscate = passwordToObfuscate;
        Preconditions.checkArgument(commandTimeoutInMinutes>0, "Command timeout must be greater than 0");
        this.commandTimeoutInMinutes = commandTimeoutInMinutes;
        this.maxVerboseOutput = maxVerboseOutput;
    }

    private String getDefaultSshWrapperScriptContent()
    {
        return SystemUtils.IS_OS_WINDOWS ? SSH_WIN : SSH_UNIX;
    }

    private String getCustomisedSshWrapperScriptContent()
    {
        return SystemUtils.IS_OS_WINDOWS ?
                "@\"" + sshCommand + "\" %*\r\n"
                :
                "#!/bin/sh\n\""+
                        sshCommand + "\" $@\n";
    }

    private String getSshScriptToRun()
    {
        final String scriptContent = StringUtils.isBlank(sshCommand) ? getDefaultSshWrapperScriptContent() : getCustomisedSshWrapperScriptContent();
        try
        {
            //on Windows, git cannot cope with GIT_SSH pointing to a batch file located in a directory with spaces in its name
            final boolean tmpDirHasSpaceInName = SystemUtils.getJavaIoTmpDir().getAbsolutePath().contains(" ");

            final BambooFileUtils.TemporaryFileSpecBuilder specBuilder =
                    new BambooFileUtils.TemporaryFileSpecBuilder(scriptContent, "bamboo-ssh.")
                            .setSuffix(BambooFilenameUtils.getScriptSuffix())
                            .setExecutable(true)
                            .setPrefer83PathsOnWindows(tmpDirHasSpaceInName);

            final File sshScript = BambooFileUtils.getSharedTemporaryFile(specBuilder.build());
            return sshScript.getAbsolutePath();
        }
        catch (IOException e)
        {
            throw new RuntimeException(e);
        }
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    private final static class GitExistencePair
    {
        public final File workingDirectory;
        private final String gitExecutableName;
        public final GitCommandProcessor gitCommandProcessor;

        public GitExistencePair(final File workingDirectory, final String gitExecutableName, final GitCommandProcessor gitCommandProcessor)
        {
            this.workingDirectory = workingDirectory;
            this.gitExecutableName = gitExecutableName;
            this.gitCommandProcessor = gitCommandProcessor;
        }

        @Override
        public boolean equals(Object o)
        {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            GitExistencePair that = (GitExistencePair) o;

            if (!gitExecutableName.equals(that.gitExecutableName)) return false;
            if (!workingDirectory.equals(that.workingDirectory)) return false;

            return true;
        }

        @Override
        public int hashCode()
        {
            int result = workingDirectory.hashCode();
            result = 31 * result + gitExecutableName.hashCode();
            return result;
        }
    }

    private final static Cache<GitExistencePair, String> GIT_EXISTENCE_CHECK_RESULT =
            CacheBuilder.newBuilder().expireAfterWrite(15, TimeUnit.MINUTES).build(new CacheLoader<GitExistencePair, String>()
            {
                @Override
                public String load(final GitExistencePair input) throws Exception
                {
                    final GitCommandProcessor gitCommandProcessor = input.gitCommandProcessor;
                    final File workingDirectory = input.workingDirectory;
                    final GitCommandBuilder commandBuilder = gitCommandProcessor.createCommandBuilder("version");

                    final GitStringOutputHandler outputHandler = new GitStringOutputHandler();
                    final int exitCode = gitCommandProcessor.runCommand(commandBuilder, workingDirectory, outputHandler);
                    final String output = outputHandler.getOutput();
                    final Matcher matcher = GIT_VERSION_PATTERN.matcher(output);
                    if (!matcher.find())
                    {
                        throw new GitCommandException("Unable to parse git command output: " + exitCode, null, output, "", null);
                    }
                    return matcher.group();
                }
            });


    /**
     * Checks whether git exist in current system.
     *
     * @param workingDirectory specifies arbitrary directory.
     *
     * @throws RepositoryException when git wasn't found in current system.
     */
    public void checkGitExistenceInSystem(@NotNull final File workingDirectory) throws RepositoryException
    {
        final boolean gitDependsOnWorkingDirectory = gitExecutable.trim().startsWith(".");
        final File directory = gitDependsOnWorkingDirectory ? workingDirectory : new File("/");
        final GitExistencePair cacheKey = new GitExistencePair(directory, gitExecutable, this);
        try
        {
            GIT_EXISTENCE_CHECK_RESULT.get(cacheKey);
        }
        catch (ExecutionException e)
        {
            final Throwable cause = e.getCause();
            final RepositoryException re = Narrow.to(cause, RepositoryException.class);
            if (re!=null)
            {
                throw re;
            }
            throw new RepositoryException(cause);
        }
    }

    /**
     * Creates .git repository in a given directory.
     *
     * @param workingDirectory - directory in which we want to create empty repository.
     *
     * @throws RepositoryException when init command fails
     */
    public void runInitCommand(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("init");
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public List<String> runStatusCommand(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("status", "--porcelain", "--untracked-files=no");
        final LineOutputHandlerImpl gitOutputHandler = new LineOutputHandlerImpl();
        runCommand(commandBuilder, workingDirectory, gitOutputHandler);
        if (log.isDebugEnabled())
        {
            log.debug("git status output: " + gitOutputHandler.getStdout());
        }
        return gitOutputHandler.getLines();
    }

    public void runFetchCommand(@NotNull final File workingDirectory, @NotNull final GitRepositoryAccessData accessData, String refSpec, boolean useShallow) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("fetch", accessData.getRepositoryUrl(), refSpec, "--update-head-ok");
        if (useShallow)
        {
            commandBuilder.shallowClone();
        }
        File shallowFile = new File(new File(workingDirectory, Constants.DOT_GIT), "shallow");
        if (!useShallow && shallowFile.exists())
        {
            //directory has shallows: we need to make it deep
            log.info(String.format("Detected that the directory needs to be converted to a full clone: %s, branch: %s, working dir: %s",
                                   accessData.getRepositoryUrl(), accessData.getVcsBranch().getName(), workingDirectory.getAbsolutePath()));
            commandBuilder.append("--depth=99999999");
        }
        if (accessData.isVerboseLogs())
        {
            commandBuilder.verbose(true);
            commandBuilder.append("--progress");
        }
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));

        //BDEV-3230: it can happen (e.g. with Stash) that fetch returns nothing and gives no error
        File fetchHeadFile = new File(new File(workingDirectory, Constants.DOT_GIT), Constants.FETCH_HEAD);
        try
        {
            if (!fetchHeadFile.exists() || StringUtils.isBlank(FileUtils.readFileToString(fetchHeadFile)))
            {
                throw new RepositoryException("fatal: FETCH_HEAD is empty after fetch.");
            }
        }
        catch (IOException e)
        {
            throw new RepositoryException("fatal: Error reading FETCH_HEAD file");
        }
    }

    public void runCloneCommand(@NotNull final File workingDirectory, @NotNull final String repositoryUrl, boolean useShallowClone, boolean verboseLogs) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("clone", repositoryUrl);
        commandBuilder.destination(workingDirectory.getAbsolutePath());
        if (useShallowClone)
        {
            commandBuilder.shallowClone();
        }
        if (verboseLogs)
        {
            commandBuilder.verbose(true);
            commandBuilder.append("--progress");
        }
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runLocalCloneCommand(@NotNull final File workingDirectory, final File cacheDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("clone", "file://" + cacheDirectory.getAbsolutePath());
        commandBuilder.append("-n"); //no checkout
        commandBuilder.append("--reference");
        commandBuilder.append(cacheDirectory.getAbsolutePath()); //instruct git to create .git/objects/info/alternates
        commandBuilder.destination(workingDirectory.getAbsolutePath());
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runCheckoutCommand(@NotNull final File workingDirectory, String revision, String configuredBranchName) throws RepositoryException
    {
        /**
         * this call to git log checks if requested revision is considered as HEAD of resolved branch. If so, instead of calling explicit revision,
         * checkout to branch is called to avoid DETACHED HEAD
         */
        String possibleBranch = getPossibleBranchNameForCheckout(workingDirectory, revision, configuredBranchName);

        String destination = revision;
        if (StringUtils.isNotBlank(possibleBranch))
        {
            destination = possibleBranch;
        }
        runCheckoutCommandForBranchOrRevision(workingDirectory, destination);
    }

    public void runCheckoutCommandForBranchOrRevision(@NotNull final File workingDirectory, String destination) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("checkout", "-f", destination);
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public void runSubmoduleUpdateCommand(@NotNull final File workingDirectory) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("submodule", "update", "--init", "--recursive");

        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    @NotNull
    public String getRevisionHash(@NotNull final File workingDirectory, @NotNull String revision) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("log", "-1", ENCODING_OPTION, "--format=%H");
        commandBuilder.append(revision);
        final GitStringOutputHandler outputHandler = new GitStringOutputHandler(GIT_OUTPUT_ENCODING);
        runCommand(commandBuilder, workingDirectory, outputHandler);
        return outputHandler.getOutput().trim();
    }

    // -------------------------------------------------------------------------------------------------- Helper Methods
    private boolean isMatchingLocalRef(String refString, String branchName)
    {
        return refString.startsWith(Constants.R_HEADS) && StringUtils.removeStart(refString, Constants.R_HEADS).equals(branchName);
    }

    private boolean isMatchingRemoteRef(String refString, String branchName)
    {
        return refString.startsWith(REMOTE_ORIGIN) && StringUtils.removeStart(refString, REMOTE_ORIGIN).equals(branchName);
    }

    //Because we no longer do file copy from cache when creating git repo we don't automagically get all the local heads.
    //That's why we need to look in refs/remotes/origin/ when determining if revision is head of a branch.
    //Subsequent git checkout <branchname> works correctly, that is creates correct local ref and connects it with remote branch
    //We must check however if local branch doesn't exist when doing this.
    public String getPossibleBranchNameForCheckout(File workingDirectory, String revision, String configuredBranchName) throws RepositoryException
    {
        String branchName = StringUtils.isBlank(configuredBranchName) ? Constants.MASTER : configuredBranchName;

        GitCommandBuilder commandBuilder = createCommandBuilder("show-ref", branchName);
        final LineOutputHandlerImpl outputHandler = new LineOutputHandlerImpl();
        runCommand(commandBuilder, workingDirectory, outputHandler);

        Iterable<String> lines = outputHandler.getLines();
        if (log.isDebugEnabled())
        {
            log.debug("--- Full output: ---\n");
            for (String line : lines)
            {
                log.debug(line);
            }
            log.debug("--- End of output: ---\n");
        }

        boolean remoteRefFound = false;
        //line format is: <sha> <refString>
        for (String line : lines)
        {
            line = line.trim();
            Iterable<String> splitLine = Splitter.on(' ').trimResults().split(line);
            String sha = Iterables.getFirst(splitLine, null);
            String refString = Iterables.getLast(splitLine, null);
            if (isMatchingLocalRef(refString, branchName))
            {
                if (revision.equals(sha))
                {
                    //local branch found with correct sha: proceed with branch checkout
                    return branchName;
                }
                else
                {
                    //local branch found with different sha: give up
                    return "";
                }
            }
            else if (isMatchingRemoteRef(refString, branchName) && revision.equals(sha))
            {
                remoteRefFound = true;
            }
        }

        if (remoteRefFound)
        {
            //we've found matching remote with correct sha but no matching local branch: proceed with branch checkout, it will create local branch
            return branchName;
        }
        return "";
    }

    @NotNull
    public ImmutableMap<String, String> getRemoteRefs(@NotNull File workingDirectory, @NotNull GitRepositoryAccessData accessData) throws RepositoryException
    {
        final LineOutputHandlerImpl goh = new LineOutputHandlerImpl();
        final GitCommandBuilder commandBuilder = createCommandBuilder("ls-remote", accessData.getRepositoryUrl());
        runCommand(commandBuilder, workingDirectory, goh);
        final ImmutableMap<String, String> result = parseLsRemoteOutput(goh);
        return result;
    }

    @NotNull
    static ImmutableMap<String, String> parseLsRemoteOutput(final LineOutputHandlerImpl goh)
    {
        final ImmutableMap.Builder<String, String> refs = ImmutableMap.builder();
        for (final String ref : goh.getLines())
        {
            if (ref.contains("^{}"))
            {
                continue;
            }
            final Matcher matcher = LS_REMOTE_LINE_PATTERN.matcher(ref);
            if (matcher.matches())
            {
                refs.put(matcher.group(2), matcher.group(1));
            }
        }
        return refs.build();
    }

    public GitCommandBuilder createCommandBuilder(String... commands)
    {
        return new GitCommandBuilder(commands)
                .executable(gitExecutable)
                .sshCommand(getSshScriptToRun());
    }

    @Override
    public void reportProxyError(String message, Throwable exception)
    {
        proxyErrorMessage = message;
        proxyException = exception;
    }

    public void runCommand(@NotNull final GitCommandBuilder commandBuilder, @NotNull final File workingDirectory) throws RepositoryException
    {
        runCommand(commandBuilder, workingDirectory, new LoggingOutputHandler(buildLogger));
    }

    public int runCommand(@NotNull final GitCommandBuilder commandBuilder, @NotNull final File workingDirectory,
                          @NotNull final GitOutputHandler outputHandler) throws RepositoryException
    {
        //noinspection ResultOfMethodCallIgnored
        workingDirectory.mkdirs();

        PluggableProcessHandler handler = new PluggableProcessHandler();
        handler.setOutputHandler(outputHandler);
        handler.setErrorHandler(outputHandler);

        final List<String> commandArgs = commandBuilder.build();

        final String maskedCommandLine = PasswordMaskingUtils.mask(BambooStringUtils.toCommandLineString(commandArgs), passwordToObfuscate);
        if (maxVerboseOutput || log.isDebugEnabled())
        {
            if (maxVerboseOutput)
            {
                buildLogger.addBuildLogEntry(maskedCommandLine);
            }
            log.debug("Running in " + workingDirectory + ": '" + maskedCommandLine + "'");
        }

        final ExternalProcessBuilder externalProcessBuilder = new ExternalProcessBuilder()
                .command(commandArgs, workingDirectory)
                .handler(handler)
                .idleTimeout(TimeUnit.MINUTES.toMillis(commandTimeoutInMinutes))
                .env(commandBuilder.getEnv());

        ExternalProcess process = externalProcessBuilder.build();
        process.execute();

        if (!handler.succeeded())
        {
            // command may contain user password (url) in plaintext -> hide it from bamboo plan/build logs. see BAM-5781
            final String maskedOutput = PasswordMaskingUtils.mask(outputHandler.getStdout(), passwordToObfuscate);
            String message = "command " + maskedCommandLine + " failed with code " + handler.getExitCode() + ". Working directory was [" + workingDirectory + "].";

            throw new GitCommandException(
                    message, proxyException != null ? proxyException : handler.getException(),
                    maskedOutput,
                    proxyErrorMessage != null ? PasswordMaskingUtils.mask(proxyErrorMessage, passwordToObfuscate) : maskedOutput,
                    passwordToObfuscate);
        }

        return handler.getExitCode();
    }

    /**
     * Returns true if there are modified files in the working directory or repository index after the merge
     */
    public void runMergeCommand(@NotNull final GitCommandBuilder commandBuilder, @NotNull final File workspaceDir) throws RepositoryException
    {
        final LoggingOutputHandler mergeOutputHandler = new LoggingOutputHandler(buildLogger);
        runCommand(commandBuilder, workspaceDir, mergeOutputHandler);
        log.debug(mergeOutputHandler.getStdout());
    }

    @NotNull
    public CommitContext extractCommit(final File directory, final String targetRevision) throws  RepositoryException
    {
        final CommitOutputHandler coh = new CommitOutputHandler(Collections.<String>emptySet());
        GitCommandBuilder commandBuilder = createCommandBuilder("log", "-1", ENCODING_OPTION, "--format=" + CommitOutputHandler.LOG_COMMAND_FORMAT_STRING, targetRevision);
        runCommand(commandBuilder, directory, coh);
        List<CommitContext> commits = coh.getExtractedCommits();

        if (commits.isEmpty())
        {
            throw new RepositoryException("Could not find commit with revision " + targetRevision);
        }
        return commits.get(0);
    }

    public Pair<List<CommitContext>, Integer> runLogCommand(final File cacheDirectory, final String lastVcsRevisionKey, final String targetRevision, @NotNull final Set<String> shallows, final int maxCommits) throws RepositoryException
    {
        GitCommandBuilder commandBuilder = createCommandBuilder("log", "-p", "--name-only", ENCODING_OPTION, "--format=" + CommitOutputHandler.LOG_COMMAND_FORMAT_STRING);
        if (lastVcsRevisionKey.equals(targetRevision))
        {
            commandBuilder.append(targetRevision).append("-1");
        }
        else
        {
            commandBuilder.append(lastVcsRevisionKey + ".." + targetRevision);
        }
        log.info("from revision: [" + lastVcsRevisionKey + "]; to revision: [" + targetRevision + "]");
        final CommitOutputHandler coh = new CommitOutputHandler(shallows, maxCommits);
        runCommand(commandBuilder, cacheDirectory, coh);
        return Pair.make(coh.getExtractedCommits(), coh.getSkippedCommitCount());
    }

    interface GitOutputHandler extends OutputHandler
    {
        String getStdout();
    }

    static class GitStringOutputHandler extends StringOutputHandler implements GitOutputHandler
    {
        public GitStringOutputHandler(final String encoding)
        {
            super(encoding);
        }

        /**
         * @deprecated use ${@link #GitStringOutputHandler(String)} if you can set the output encoding
         */
        @Deprecated
        public GitStringOutputHandler()
        {
        }

        @Override
        public String getStdout()
        {
            return getOutput();
        }
    }

    static class LineOutputHandlerImpl extends LineOutputHandler implements GitOutputHandler
    {
        private final List<String> lines = Lists.newLinkedList();

        @Override
        protected void processLine(int i, String s)
        {
            lines.add(s);
        }

        @NotNull
        public List<String> getLines()
        {
            return lines;
        }

        @Override
        public String getStdout()
        {
            return lines.toString();
        }
    }

    static class LoggingOutputHandler extends LineOutputHandler implements GitCommandProcessor.GitOutputHandler
    {
        final BuildLogger buildLogger;
        final StringBuilder stringBuilder;

        public LoggingOutputHandler(@NotNull final BuildLogger buildLogger)
        {
            this.buildLogger = buildLogger;
            stringBuilder = new StringBuilder();
        }

        @Override
        protected void processLine(int i, String s)
        {
            buildLogger.addBuildLogEntry(s);
            if (stringBuilder.length()!=0)
            {
                stringBuilder.append("\n");
            }
            stringBuilder.append(s);
        }

        @Override
        public String getStdout()
        {
            return stringBuilder.toString();
        }
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators


    public void setSshCommand(String sshCommand)
    {
        this.sshCommand = sshCommand;
    }
}