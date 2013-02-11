package com.atlassian.bamboo.plugins.git.messages;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.plugins.git.GitCacheDirectoryUtils;
import com.atlassian.bamboo.v2.build.agent.messages.AbstractBambooAgentMessage;
import com.atlassian.bamboo.v2.build.agent.messages.RemoteBambooMessage;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.collect.ImmutableSet;

/**
 * Message carrying active git cache SHAs in order to remove all other unused caches.
 *
 * @since 2.6
 */
public class DeleteUnusedGitCacheDirectoriesOnAgentMessage extends AbstractBambooAgentMessage
        implements RemoteBambooMessage
{
    private final ImmutableSet<String> usedSHAs;
    private final GitCacheDirectoryUtils gitCacheDirectoryUtils;

    public DeleteUnusedGitCacheDirectoriesOnAgentMessage(Iterable<String> usedSHAs, final GitCacheDirectoryUtils gitCacheDirectoryUtils)
    {
        this.gitCacheDirectoryUtils = gitCacheDirectoryUtils;
        this.usedSHAs = ImmutableSet.copyOf(usedSHAs);
    }

    public Object deliver()
    {
        final BuildDirectoryManager buildDirectoryManager = (BuildDirectoryManager) ContainerManager.getComponent("buildDirectoryManager");
        gitCacheDirectoryUtils.deleteCacheDirectoriesExcept(buildDirectoryManager.getBaseBuildWorkingDirectory(), usedSHAs);
        return null;
    }
}

