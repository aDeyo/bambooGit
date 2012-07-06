package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.build.CommandLogEntry;
import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.sal.api.message.I18nResolver;
import org.apache.log4j.Logger;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.dircache.DirCacheCheckout;
import org.eclipse.jgit.errors.MissingObjectException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.RefUpdate;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.transport.FetchResult;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.TagOpt;
import org.eclipse.jgit.transport.Transport;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;

public class JGitOperationHelper extends GitOperationHelperToBeRemoved
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(JGitOperationHelper.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors

    public JGitOperationHelper(final GitRepository.GitRepositoryAccessData accessData, final @NotNull BuildLogger buildLogger,
                               final @NotNull I18nResolver i18nResolver)
    {
        super(accessData, buildLogger, i18nResolver);
    }

    // ----------------------------------------------------------------------------------------------- Interface Methods

    @Override
    protected void doFetch(@NotNull final Transport transport, @NotNull final File sourceDirectory, final RefSpec refSpec, final boolean useShallow) throws RepositoryException
    {
        String branchDescription = "(unresolved) " + accessData.branch;
        try
        {
            transport.setTagOpt(TagOpt.AUTO_FOLLOW);

            FetchResult fetchResult = transport.fetch(new BuildLoggerProgressMonitor(buildLogger), Arrays.asList(refSpec), useShallow ? 1 : 0);
            buildLogger.addBuildLogEntry("Git: " + fetchResult.getMessages());
        }
        catch (IOException e)
        {
            String message = i18nResolver.getText("repository.git.messages.fetchingFailed", accessData.repositoryUrl, branchDescription, sourceDirectory);
            throw new RepositoryException(buildLogger.addErrorLogEntry(message + " " + e.getMessage()), e);
        }
        finally
        {
            if (transport != null)
            {
                transport.close();
            }
        }
    }

    @Override
    public String commit(@NotNull File sourceDirectory, @NotNull String message, @NotNull String comitterName, @NotNull String comitterEmail) throws RepositoryException
    {
        throw new UnsupportedOperationException("JGit implementation does not support commit, please use native Git");
        //has to be modified to make empty commit a no-op
        //try
        //{
        //    File gitDir = new File(sourceDirectory, Constants.DOT_GIT);
        //    FileRepository fileRepository = new FileRepository(gitDir);
        //    Git git = new Git(fileRepository);
        //    git.add().addFilepattern(".").call();
        //    return git.commit().setMessage(message).setCommitter(comitterName, comitterEmail).call().name();
        //}
        //catch (IOException e)
        //{
        //    throw new RepositoryException("IOException during committing", e);
        //}
        //catch (GitAPIException e)
        //{
        //    throw new RepositoryException("GitAPIException during committing", e);
        //}
    }

    @Override
    protected String doCheckout(@NotNull final FileRepository localRepository, @NotNull final File sourceDirectory, @NotNull final String targetRevision, @Nullable final String previousRevision, final boolean useSubmodules) throws RepositoryException
    {
        if (useSubmodules)
        {
            buildLogger.addBuildLogEntry(new CommandLogEntry(i18nResolver.getText("repository.git.messages.jgit.submodules.not.supported")));
        }

        RevWalk revWalk = null;
        DirCache dirCache = null;
        try
        {
            dirCache = localRepository.lockDirCache();

            revWalk = new RevWalk(localRepository);
            final RevCommit targetCommit = revWalk.parseCommit(localRepository.resolve(targetRevision));
            final RevCommit previousCommit = previousRevision == null ? null : revWalk.parseCommit(localRepository.resolve(previousRevision));

            DirCacheCheckout dirCacheCheckout = new DirCacheCheckout(localRepository,
                                                                     previousCommit == null ? null : previousCommit.getTree(),
                                                                     dirCache,
                                                                     targetCommit.getTree());
            dirCacheCheckout.setFailOnConflict(true);
            try
            {
                dirCacheCheckout.checkout();
            }
            catch (MissingObjectException e)
            {
                final String message = i18nResolver.getText("repository.git.messages.checkoutFailedMissingObject", targetRevision, e.getObjectId().getName());
                throw new RepositoryException(buildLogger.addErrorLogEntry(message));
            }

            final RefUpdate refUpdate = localRepository.updateRef(Constants.HEAD);
            refUpdate.setNewObjectId(targetCommit);
            refUpdate.forceUpdate();
            // if new branch -> refUpdate.link() instead of forceUpdate()

            return targetCommit.getId().getName();
        }
        catch (IOException e)
        {
            throw new RepositoryException(buildLogger.addErrorLogEntry(i18nResolver.getText("repository.git.messages.checkoutFailed", targetRevision)) + e.getMessage(), e);
        }
        finally
        {
            if (revWalk != null)
            {
                revWalk.release();
            }
            if (dirCache != null)
            {
                dirCache.unlock();
            }
            if (localRepository != null)
            {
                localRepository.close();
            }
        }
    }

    @Override
    public boolean merge(@NotNull final File workspaceDir, @NotNull final String targetRevision,
                         @NotNull String committerName, @NotNull String committerEmail)
    {
        throw new UnsupportedOperationException("JGit implementation does not support merging, please use native Git");
    }

    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators
}
