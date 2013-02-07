package com.atlassian.bamboo.plugins.git.messages;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.plugins.git.GitCacheDirectoryUtils;
import com.atlassian.bamboo.v2.build.agent.messages.AbstractBambooAgentMessage;
import com.atlassian.bamboo.v2.build.agent.messages.RemoteBambooMessage;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.collect.ImmutableSet;


/**
 * Message carrying SHAs of git caches to be deleted.
 * @since 2.6
 */
public class DeleteSpecifiedGitCacheDirectoriesOnAgentMessage extends AbstractBambooAgentMessage
        implements RemoteBambooMessage
{
    private final ImmutableSet<String> deleteSHAs;
    private final GitCacheDirectoryUtils gitCacheDirectoryUtils;

    public DeleteSpecifiedGitCacheDirectoriesOnAgentMessage(final Iterable<String> deleteSHAs, final GitCacheDirectoryUtils gitCacheDirectoryUtils)
    {
        this.gitCacheDirectoryUtils = gitCacheDirectoryUtils;
        this.deleteSHAs = ImmutableSet.copyOf(deleteSHAs);
    }

    public Object deliver()
    {
        final BuildDirectoryManager buildDirectoryManager = (BuildDirectoryManager) ContainerManager.getComponent("buildDirectoryManager");
        gitCacheDirectoryUtils.deleteCacheDirectories(buildDirectoryManager.getBaseBuildWorkingDirectory(), deleteSHAs);
        return null;
    }
}

