package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.buildqueue.AgentAssignment;
import com.atlassian.bamboo.buildqueue.manager.AgentManager;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.plan.PlanHelper;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.cache.CachedPlanManager;
import com.atlassian.bamboo.plan.cache.ImmutableChain;
import com.atlassian.bamboo.plan.cache.ImmutablePlan;
import com.atlassian.bamboo.repository.CacheDescription;
import com.atlassian.bamboo.repository.Repository;
import com.atlassian.bamboo.repository.RepositoryDefinition;
import com.atlassian.bamboo.v2.build.agent.AgentCommandSender;
import com.atlassian.bamboo.v2.build.agent.BuildAgent;
import com.atlassian.bamboo.v2.build.agent.BuildAgentImpl;
import com.atlassian.bamboo.v2.build.agent.RemoteAgentDefinitionImpl;
import com.atlassian.bamboo.v2.build.agent.messages.RemoteBambooMessage;
import com.atlassian.sal.api.message.I18nResolver;
import com.atlassian.spring.container.ContainerContext;
import com.atlassian.spring.container.ContainerManager;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.opensymphony.xwork.ValidationAware;
import org.apache.commons.io.FileUtils;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.mockito.internal.stubbing.defaultanswers.ReturnsMocks;
import org.mockito.invocation.InvocationOnMock;
import org.mockito.runners.MockitoJUnitRunner;
import org.mockito.stubbing.Answer;
import org.testng.Assert;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @since 2.6
 */
@RunWith (MockitoJUnitRunner.class)
public class GitCacheHandlerTest extends GitAbstractTest
{

    private static final String URL1 = "http://some.url";
    private static final String URL2 = "http://some.other.url";
    private static final String URL3 = "http://some.other.url2";
    private static File cacheDir1;
    private static File cacheDir2;
    private static File cacheDir3;

    private static final String UNUSED_URL = "http://some.unused.url";
    private static final String ANOTHER_UNUSED_URL = "http://another.unused.url";

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Mock
    private GitCacheDirectoryUtils gitCacheDirectoryUtils;

    @Mock
    private BuildDirectoryManager directoryManager;

    @Before
    public void setUp() throws Exception
    {
        when(directoryManager.getBaseBuildWorkingDirectory()).thenReturn(temporaryFolder.getRoot());

        final ContainerContext containerContext = Mockito.mock(ContainerContext.class);
        when(containerContext.getComponent("buildDirectoryManager")).thenReturn(directoryManager);

        ContainerManager.getInstance().setContainerContext(containerContext);

        cacheDir1 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(URL1));
        cacheDir2 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(URL2));
        cacheDir3 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(URL3));
    }

    @Test
    public void testNonExistentRootDir() throws Exception
    {
        final GitCacheHandler gitCacheHandler = new GitCacheHandler();

        final BuildDirectoryManager directoryManager = Mockito.mock(BuildDirectoryManager.class, new Returns(temporaryFolder.newFolder("non-existent-subdir")));
        gitCacheHandler.setBuildDirectoryManager(directoryManager);
        gitCacheHandler.setCachedPlanManager(Mockito.mock(CachedPlanManager.class, new ReturnsMocks()));

        final Collection<CacheDescription> cacheDescriptions = gitCacheHandler.getCacheDescriptions();

        Assert.assertEquals(cacheDescriptions, Collections.<Object>emptyList());
    }

    @Test
    public void testGettingDescriptions() throws Exception
    {
        final GitCacheHandler handler = createInitializedHandler();
        FileUtils.forceMkdir(cacheDir1);
        final File nonExistentCacheDir = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(UNUSED_URL));
        FileUtils.forceMkdir(nonExistentCacheDir);

        final Collection<CacheDescription> cacheDescriptions = handler.getCacheDescriptions();
        Assert.assertEquals(cacheDescriptions.size(), 4);

        final Map<String, CacheDescription> indexed = Maps.uniqueIndex(cacheDescriptions, new Function<CacheDescription, String>()
        {
            public String apply(CacheDescription from)
            {
                return from.getKey();
            }
        });

        verifyDescription(cacheDir1, indexed.get(cacheDir1.getName()), true, URL1, 3);
        verifyDescription(cacheDir2, indexed.get(cacheDir2.getName()), false, URL2, 2);
        verifyDescription(cacheDir3, indexed.get(cacheDir3.getName()), false, URL3, 1);
        verifyDescription(nonExistentCacheDir, indexed.get(nonExistentCacheDir.getName()), true, null, 0);
    }

    @Test
    public void testDeletingUnusedDirs() throws Exception
    {
        final GitCacheHandler handler = createInitializedHandler();

        FileUtils.forceMkdir(cacheDir1);
        FileUtils.forceMkdir(cacheDir2);

        final File unusedCache1 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(UNUSED_URL));
        FileUtils.forceMkdir(unusedCache1);
        final File unusedCache2 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(ANOTHER_UNUSED_URL));
        FileUtils.forceMkdir(unusedCache2);

        final List<RemoteBambooMessage> messages = feedWithRemoteAgents(handler);
        handler.setGitCacheDirectoryUtils(gitCacheDirectoryUtils);
        handler.deleteUnusedCaches(Mockito.mock(ValidationAware.class));
        Assert.assertEquals(messages.size(), 1);

        Assert.assertTrue(cacheDir1.exists(), "Cache directory (1) " + cacheDir1.getAbsolutePath() + " should not have been deleted.");
        Assert.assertTrue(cacheDir2.exists(), "Cache directory (2) " + cacheDir2.getAbsolutePath() + " should not have been deleted.");
        Assert.assertFalse(cacheDir3.exists(), "Cache directory (3) " + cacheDir3.getAbsolutePath() + " should not have been created.");
        Assert.assertFalse(unusedCache1.exists(), "Cache directory (1) " + unusedCache1.getAbsolutePath() + " should have been deleted.");
        Assert.assertFalse(unusedCache2.exists(), "Cache directory (2) " + unusedCache2.getAbsolutePath() + " should have been deleted.");

        final Set<String> usedSHAs = ImmutableSet.of(cacheDir1.getName(), cacheDir2.getName(), cacheDir3.getName());
        messages.get(0).deliver();
        Mockito.verify(gitCacheDirectoryUtils).deleteCacheDirectoriesExcept(temporaryFolder.getRoot(), usedSHAs);
    }

    @Test
    public void testDeletingSpecificDirs() throws Exception
    {
        final GitCacheHandler handler = createInitializedHandler();

        FileUtils.forceMkdir(cacheDir1);
        FileUtils.forceMkdir(cacheDir2);

        final File unusedCache1 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(UNUSED_URL));
        FileUtils.forceMkdir(unusedCache1);
        final File unusedCache2 = GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(ANOTHER_UNUSED_URL));
        FileUtils.forceMkdir(unusedCache2);

        final List<RemoteBambooMessage> messages = feedWithRemoteAgents(handler);

        final Set<String> deleteSHAs = Sets.newHashSet(GitCacheDirectory.calculateRepositorySha(createAccessData(URL1)),
                GitCacheDirectory.calculateRepositorySha(createAccessData(URL3)),
                GitCacheDirectory.calculateRepositorySha(createAccessData(UNUSED_URL)));

        handler.setGitCacheDirectoryUtils(gitCacheDirectoryUtils);
        handler.deleteCaches(deleteSHAs, Mockito.mock(ValidationAware.class));
        Assert.assertEquals(messages.size(), 1);

        Assert.assertFalse(cacheDir1.exists(), "Cache directory (used 1) " + cacheDir1.getAbsolutePath() + " should have been deleted.");
        Assert.assertTrue(cacheDir2.exists(), "Cache directory (used 2) " + cacheDir2.getAbsolutePath() + " should not have been created.");
        Assert.assertFalse(cacheDir3.exists(), "Cache directory (used 3) " + cacheDir3.getAbsolutePath() + " should not have been created.");
        Assert.assertFalse(unusedCache1.exists(), "Cache directory (unused 1) " + unusedCache1.getAbsolutePath() + " should have been deleted.");
        Assert.assertTrue(unusedCache2.exists(), "Cache directory (unused 2) " + unusedCache2.getAbsolutePath() + " should not have been deleted.");

        final Set<String> usedSHAs = ImmutableSet.of(cacheDir1.getName(), cacheDir3.getName(), unusedCache1.getName());
        messages.get(0).deliver();
        Mockito.verify(gitCacheDirectoryUtils).deleteCacheDirectories(temporaryFolder.getRoot(), usedSHAs);
    }

    private GitCacheHandler createInitializedHandler() throws Exception
    {
        int cnt = 0;
        final List<ImmutableChain> allPlans = Lists.newArrayList();
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitRepository(URL1)));
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitRepository(URL1)));
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitRepository(URL1)));
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitHubRepository(URL2)));
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitHubRepository(URL2)));
        allPlans.add(createPlan("TST-AUTO" + ++cnt, createGitRepository(URL3)));

        allPlans.add(createPlan("TST-AUTO" + ++cnt, Mockito.mock(Repository.class)));

        final GitCacheHandler gitCacheHandler = new GitCacheHandler();

        gitCacheHandler.setBuildDirectoryManager(directoryManager);
        final CachedPlanManager planManager = Mockito.mock(CachedPlanManager.class);
        Mockito.when(planManager.getPlans(ImmutableChain.class)).thenReturn(allPlans);
        gitCacheHandler.setCachedPlanManager(planManager);
        gitCacheHandler.setI18nResolver(mock(I18nResolver.class));
        gitCacheHandler.setAgentManager(Mockito.mock(AgentManager.class));
        return gitCacheHandler;
    }

    private GitRepository createGitRepository(String url) throws IOException
    {
        final GitRepository repository = mock(GitRepository.class);
        when(repository.getRepositoryUrl()).thenReturn(url);
        when(repository.getCacheDirectory()).thenReturn(GitCacheDirectory.getCacheDirectory(temporaryFolder.getRoot(), createAccessData(url)));
        when(repository.getSubstitutedAccessData()).thenReturn(createAccessData(url));
        return repository;
    }

    private GitHubRepository createGitHubRepository(String url) throws IOException
    {
        final GitHubRepository repository = mock(GitHubRepository.class);
        final  GitRepository wrappedGitRepo = createGitRepository(url);
        when(repository.getGitRepository()).thenReturn(wrappedGitRepo);
        return repository;
    }

    private Chain createPlan(final String key, Repository repositories)
    {
        final Chain plan = mock(Chain.class);
        final List<RepositoryDefinition> repositoryDefinitions = createRepositoryDefinitionList(repositories);

        when(plan.getEffectiveRepositoryDefinitions()).thenReturn(repositoryDefinitions);
        when(plan.getKey()).thenReturn(key);
        when(plan.getPlanKey()).thenReturn(PlanKeys.getPlanKey(key));

        return plan;
    }

    private List<RepositoryDefinition> createRepositoryDefinitionList(final Repository... repositories)
    {
        return Lists.newArrayList(
                Iterables.transform(Arrays.asList(repositories), new Function<Repository, RepositoryDefinition>()
                {
                    @Override
                    public RepositoryDefinition apply(@Nullable Repository input)
                    {
                        final RepositoryDefinition repositoryDefinition = mock(RepositoryDefinition.class);
                        when(repositoryDefinition.getRepository()).thenReturn(input);
                        return repositoryDefinition;
                    }
                })
        );
    }

    private void verifyPlanList(final String url, int expectedCount, Collection<ImmutablePlan> plans)
    {
        for (ImmutablePlan plan : plans)
        {
            if (!Iterables.any(PlanHelper.getRepositoryDefinitions(plan), new Predicate<RepositoryDefinition>()
            {
                @Override
                public boolean apply(RepositoryDefinition repositoryDefinition)
                {
                    final Repository repository = repositoryDefinition.getRepository();

                    final GitRepository gitRepository;
                    if (repository instanceof GitHubRepository)
                    {
                        final GitHubRepository gitHubRepository = (GitHubRepository) repository;
                        gitRepository = gitHubRepository.getGitRepository();
                    }
                    else
                    {
                        gitRepository = (GitRepository) repository;
                    }

                    return gitRepository.getSubstitutedAccessData().getRepositoryUrl().equals(url);
                }
            }))
            {
                Assert.fail("repository not found " + url);
            }
        }
        Assert.assertEquals(plans.size(), expectedCount);
    }

    private void verifyDescription(File cacheDir, CacheDescription cacheDescription, boolean exists, String url, int expectedCount)
    {
        if (url != null)
        {
            Assert.assertEquals(cacheDescription.getDescription(), "URL: '" + url + "'");
        }
        Assert.assertEquals(cacheDescription.getLocation(), cacheDir.getAbsolutePath());
        Assert.assertEquals(cacheDescription.isExists(), exists);
        verifyPlanList(url, expectedCount, cacheDescription.getUsingPlans());
    }

    private List<RemoteBambooMessage> feedWithRemoteAgents(final GitCacheHandler handler)
    {
        final AgentManager agentManager = Mockito.mock(AgentManager.class);
        final BuildAgentImpl buildAgent = new BuildAgentImpl(null, null, Lists.<AgentAssignment>newArrayList());
        buildAgent.setDefinition(new RemoteAgentDefinitionImpl());
        Mockito.when(agentManager.getAllAgents()).thenReturn(Collections.<BuildAgent>singletonList(buildAgent));
        handler.setAgentManager(agentManager);

        final AgentCommandSender commandSender = Mockito.mock(AgentCommandSender.class);
        final List<RemoteBambooMessage> messages = Lists.newArrayList();
        Mockito.doAnswer(new Answer()
        {
            public Object answer(InvocationOnMock invocation) throws Throwable
            {
                messages.add((RemoteBambooMessage) invocation.getArguments()[0]);
                return null;
            }
        }).when(commandSender).send(Mockito.<RemoteBambooMessage>any(), Mockito.anyLong());

        handler.setAgentCommandSender(commandSender);
        return messages;
    }
}

