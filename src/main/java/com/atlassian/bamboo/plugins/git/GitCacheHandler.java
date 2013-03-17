package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.plugins.git.messages.DeleteSpecifiedGitCacheDirectoriesOnAgentMessage;
import com.atlassian.bamboo.plugins.git.messages.DeleteUnusedGitCacheDirectoriesOnAgentMessage;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.utils.BambooPredicates;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.LocalBuildAgent;
import com.atlassian.bamboo.v2.build.agent.messages.RemoteBambooMessage;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.base.Function;
import com.google.common.base.Predicates;
import com.google.common.collect.Collections2;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Sets;
import com.opensymphony.xwork.ValidationAware;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.DirectoryFileFilter;
import org.apache.commons.lang.ArrayUtils;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Set;

/**
 * Implementation of a cache handler for git. This caters for data displayed on the Repository Settings admin page for
 * Git repositories.
 *
 * @since 2.6
 * @see com.atlassian.bamboo.repository.CacheHandler
 */
@SuppressWarnings ({ "JavaDoc" })
public class GitCacheHandler
{
    private CachedPlanManager cachedPlanManager;
    private BuildDirectoryManager buildDirectoryManager;
    private GitCacheDirectoryUtils gitCacheDirectoryUtils;
    private I18nResolver i18nResolver;
    private AgentManager agentManager;
    private AgentCommandSender agentCommandSender;

    /**
     * Retrieves git repositories of one plan
     */
    private static Function<ImmutablePlan, Iterable<GitRepository>> GIT_REPOSITORIES_OF_PLAN = new Function<ImmutablePlan, Iterable<GitRepository>>()
    {
        @Override
        public Iterable<GitRepository> apply(@Nullable final ImmutablePlan input)
        {
            final Iterable<Repository> allRepositories = Iterables.transform(PlanHelper.getRepositoryDefinitions(input), new Function<RepositoryDefinition, Repository>()
            {

                @Override
                public Repository apply(@Nullable final RepositoryDefinition input)
                {
                    return input.getRepository();
                }
            });

            return Iterables.transform(Iterables.filter(allRepositories, Predicates.or(Predicates.instanceOf(GitRepository.class), Predicates.instanceOf(GitHubRepository.class))),
                    new Function<Repository, GitRepository>()
                    {
                        @Override
                        public GitRepository apply(@Nullable final Repository input)
                        {
                            if (input instanceof GitHubRepository)
                            {
                                return ((GitHubRepository) input).getGitRepository();
                            }
                            else
                            {
                                return (GitRepository) input;
                            }
                        }
                    });
        }
    };


    /**
     * Handles both Git and GitHub repositories.
     * @see com.atlassian.bamboo.repository.CacheHandler#getCacheDescriptions()
     */
    @NotNull
    public Collection<CacheDescription> getCacheDescriptions()
    {
        final Collection<CacheDescription> cacheDescriptions = Lists.newArrayList();

        final Multimap<File, ImmutablePlan> plans = HashMultimap.create();
        final Map<File, GitRepository> repositories = Maps.newHashMap();

        for (ImmutablePlan plan : cachedPlanManager.getPlans(ImmutableChain.class))
        {
            for (GitRepository gitRepository : GIT_REPOSITORIES_OF_PLAN.apply(plan))
            {

                File cacheDir = gitRepository.getCacheDirectory();

                plans.put(cacheDir, plan);
                if (!repositories.containsKey(cacheDir))
                {
                    repositories.put(cacheDir, gitRepository);
                }
            }
        }

        // add the used caches:
        for (File cacheDir : repositories.keySet())
        {
            cacheDescriptions.add(createCacheDescription(repositories.get(cacheDir), cacheDir, plans.get(cacheDir)));
        }

        // add sparse info on unused caches:
        final Set<File> unusedDirs = findUnusedCaches(plans.keySet());
        for (File unusedDir : unusedDirs)
        {
            final String description = "Descriptions for unused caches is unsupported";
            final CacheDescription cacheDescription = new CacheDescription.FileBased(unusedDir, description, Collections.<ImmutablePlan>emptyList());
            cacheDescriptions.add(cacheDescription);
        }

        return cacheDescriptions;

    }

    @NotNull
    private static CacheDescription createCacheDescription(@NotNull GitRepository repository, @NotNull File cacheDir, @NotNull Collection<ImmutablePlan> usingPlans)
    {
        final GitRepositoryAccessData accessData = repository.getSubstitutedAccessData();
        final StringBuilder sb = new StringBuilder();
        sb.append("URL: '").append(accessData.getRepositoryUrl()).append('\'');
        if (accessData.getUsername() != null)
        {
            sb.append(", Username: '").append(accessData.getUsername()).append('\'');
        }

        final Collection<String> features = new ArrayList<String>(2);
        if (accessData.isUseShallowClones())
        {
            features.add("shallow clones");
        }
        if (accessData.isUseRemoteAgentCache())
        {
            features.add("remote agent caching");
        }

        if (!features.isEmpty())
        {
            sb.append(" (").append(StringUtils.join(features, ", ")).append(")");
        }

        final String description = sb.toString();

        return new CacheDescription.FileBased(cacheDir, description, usingPlans);
    }

    /**
     * Handles both Git and GitHub repositories.
     *
     * @see com.atlassian.bamboo.repository.CacheHandler#deleteCaches(java.util.Collection,
     *      com.opensymphony.xwork.ValidationAware)
     */
    public void deleteCaches(@NotNull Collection<String> keys, @NotNull ValidationAware feedback)
    {
        if (keys.isEmpty())
        {
            feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.nothingToDelete"));
            return;
        }

        final File cacheRootDir = getCacheRootDir();
        for (String key : keys)
        {
            final File cacheCandidate = new File(cacheRootDir, key);

            if (cacheCandidate.exists())
            {
                try
                {
                    FileUtils.deleteDirectory(cacheCandidate);
                    feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.success", key));
                }
                catch (IOException e)
                {
                    feedback.addActionError(i18nResolver.getText("manageCaches.delete.git.failed", key, e.getLocalizedMessage()));
                }
            }
            else
            {
                feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.skipped", key));
            }
        }
        final RemoteBambooMessage message = new DeleteSpecifiedGitCacheDirectoriesOnAgentMessage(keys, gitCacheDirectoryUtils);
        final Collection<String> agentNames = sendMessageToRemoteAgents(message);
        if (!agentNames.isEmpty())
        {
            String names = StringUtils.join(agentNames, ", ");
            feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.scheduling.deleteSpecific", names));
        }
    }

    /**
     * Handles both Git and GitHub repositories.
     * @see com.atlassian.bamboo.repository.CacheHandler#deleteUnusedCaches(com.opensymphony.xwork.ValidationAware)
     */
    public void deleteUnusedCaches(@NotNull final ValidationAware feedback)
    {
        // find all git repositories, (there will be duplicates in here):
        final Iterable<GitRepository> gitRepositories = Iterables.concat(Iterables.transform(cachedPlanManager.getPlans(ImmutableChain.class), GIT_REPOSITORIES_OF_PLAN));

        // get the cache directories for these repositories:
        final Iterable<File> cacheDirectories = Iterables.transform(gitRepositories, new Function<GitRepository, File>()
        {
            @Override
            public File apply(@Nullable final GitRepository input)
            {
                return input.getCacheDirectory();
            }
        });

        // iterate and remove duplicates:
        final Set<File> usedCaches = ImmutableSet.copyOf(cacheDirectories);

        final Set<File> unusedCacheDirs = findUnusedCaches(usedCaches);
        for (File unusedCacheDir : unusedCacheDirs)
        {
            final String sha = unusedCacheDir.getName();
            try
            {
                FileUtils.deleteDirectory(unusedCacheDir);
                feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.unused.success", sha));
            }
            catch (IOException e)
            {
                feedback.addActionError(i18nResolver.getText("manageCaches.delete.git.unused.failed", sha, e.getLocalizedMessage()));
            }
        }

        final Collection<String> usedSHAs = Collections2.transform(usedCaches, new Function<File, String>()
        {
            public String apply(File from)
            {
                return from.getName();
            }
        });

        final RemoteBambooMessage message = new DeleteUnusedGitCacheDirectoriesOnAgentMessage(usedSHAs, gitCacheDirectoryUtils);
        final Collection<String> agentNames = sendMessageToRemoteAgents(message);
        if (!agentNames.isEmpty())
        {
            final String names = StringUtils.join(agentNames, ", ");
            feedback.addActionMessage(i18nResolver.getText("manageCaches.delete.git.scheduling.deleteUnused", names));
        }
    }

    @NotNull
    private Collection<String> sendMessageToRemoteAgents(final RemoteBambooMessage message)
    {
        final Collection<String> agentNames = Lists.newArrayList();
        for (final BuildAgent buildAgent : Iterables.filter(agentManager.getAllAgents(), BambooPredicates.buildAgentIsActive()))
        {
            buildAgent.accept(new BuildAgent.BuildAgentVisitor()
            {
                public void visitLocal(final LocalBuildAgent localBuildAgent)
                {
                }

                public void visitRemote(final BuildAgent remoteBuildAgent)
                {
                    agentNames.add(buildAgent.getName());
                    agentCommandSender.send(message, buildAgent.getId());
                }
            });
        }
        return agentNames;
    }


    @NotNull
    private Set<File> findUnusedCaches(@NotNull Set<File> usedCaches)
    {
        final File[] cacheDirs = getCacheRootDir().listFiles((FileFilter) DirectoryFileFilter.DIRECTORY); // will be null if cacheRootDir does not exist
        return ArrayUtils.isEmpty(cacheDirs) ? Collections.<File>emptySet() : Sets.difference(ImmutableSet.copyOf(cacheDirs), usedCaches);
    }

    public void setCachedPlanManager(CachedPlanManager cachedPlanManager)
    {
        this.cachedPlanManager = cachedPlanManager;
    }

    public void setBuildDirectoryManager(BuildDirectoryManager buildDirectoryManager)
    {
        this.buildDirectoryManager = buildDirectoryManager;
    }

    public void setI18nResolver(I18nResolver i18nResolver)
    {
        this.i18nResolver = i18nResolver;
    }

    public void setAgentManager(AgentManager agentManager)
    {
        this.agentManager = agentManager;
    }

    public void setAgentCommandSender(AgentCommandSender agentCommandSender)
    {
        this.agentCommandSender = agentCommandSender;
    }

    public void setGitCacheDirectoryUtils(final GitCacheDirectoryUtils gitCacheDirectoryUtils)
    {
        this.gitCacheDirectoryUtils = gitCacheDirectoryUtils;
    }

    @NotNull
    private File getCacheRootDir()
    {
        return GitCacheDirectory.getCacheDirectoryRoot(buildDirectoryManager.getBaseBuildWorkingDirectory());
    }
}
