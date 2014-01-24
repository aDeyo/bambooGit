package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.v2.build.BuildRepositoryChanges;
import com.google.common.collect.Iterables;
import org.apache.commons.io.FileUtils;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.storage.file.FileRepository;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.Assert;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;

public class ShallowClonesTest extends GitAbstractTest
{
    @BeforeClass
    void setupGlobalShallowClones() throws Exception
    {
        Field useShallowClones = GitRepository.class.getDeclaredField("USE_SHALLOW_CLONES");
        useShallowClones.setAccessible(true);
        useShallowClones.setBoolean(null, true);
    }

    @DataProvider(parallel = true)
    Object[][] testShallowCloneData() throws Exception
    {
        return new Object[][]{
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"},
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"}
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"}
                        },
                },
        };
    }

//    final static Map<String, String[]> testShallowCloneDataMappings = new HashMap<String, String[]>() {{
//        put("1", new String[]{"git://github.com/pstefaniak/1.git", "1455", "shallow-clones/1-contents.zip"});
//        put("2", new String[]{"git://github.com/pstefaniak/2.git", "4c9d", "shallow-clones/2-contents.zip"});
//        put("3", new String[]{"git://github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"});
//    }};

    static final String[] protocols = new String[]{"git://", "https://"};

    @Test(dataProvider = "testShallowCloneData")
    public void testShallowClone(final String[][] successiveFetches) throws Exception
    {
        for (String protocol : protocols)
        {
            File tmp = createTempDirectory();

            String checkoutTip = null;
            for (final String[] currentFetch : successiveFetches)
            {
                final String fetchRepo = currentFetch[0];
                final String fetchHead = currentFetch[1];
                final String verifyRepo = currentFetch[2];

                final GitOperationHelper helper = createJGitOperationHelper(createAccessData(protocol + fetchRepo));
                helper.fetch(tmp, fetchHead, true);
                checkoutTip = helper.checkout(null, tmp, fetchHead, checkoutTip);
                verifyContents(tmp, verifyRepo);
            }
        }
    }

    @DataProvider(parallel = true)
    Object[][] testShallowCloneDataFailing() throws Exception
    {
        return new Object[][]{
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "0a77", "shallow-clones/3-contents.zip"},
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/3.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "0a77", "shallow-clones/3-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "4c9d", "shallow-clones/2-contents.zip"},
                        },
                },
                {
                        new String[][]{
                                new String[]{"github.com/pstefaniak/5.git", "1455", "shallow-clones/1-contents.zip"},
                        },
                },
        };
    }

    @Test(dataProvider = "testShallowCloneDataFailing")
    public void testRepositoryHandlesFailingShallowClone(final String[][] successiveFetches) throws Exception
    {
        for (String protocol : protocols)
        {
            GitRepository gitRepository = createGitRepository(AgentType.LOCAL);

            for (String[] currentFetch : successiveFetches)
            {
                setRepositoryProperties(gitRepository, protocol + currentFetch[0]);
                gitRepository.retrieveSourceCode(mockBuildContext(), currentFetch[1], getCheckoutDir(gitRepository));
                verifyContents(getCheckoutDir(gitRepository), currentFetch[2]);
            }
        }
    }

    @DataProvider(parallel = true)
    Object[][] testUseShallowClonesCheckboxData() throws Exception
    {
        return new Object[][]{
                {"git://github.com/pstefaniak/7.git",         "728b4f095a115a91be26",  true,    1},
                {"git://github.com/pstefaniak/7.git",         "728b4f095a115a91be26", false,    7},
                {"git://github.com/pstefaniak/72parents.git", "f9a3b37fcbf5298c1bfa",  true,   1},
                {"git://github.com/pstefaniak/72parents.git", "f9a3b37fcbf5298c1bfa", false,   74},
        };
    }

    @Test(dataProvider = "testUseShallowClonesCheckboxData")
    public void testUseShallowClonesCheckbox(String repositoryUrl, String targetRevision, boolean shallow, int expectedChangesetCount) throws Exception
    {
        GitRepository gitRepository = createGitRepository(AgentType.REMOTE);

        gitRepository.setBuildDirectoryManager(Mockito.mock(BuildDirectoryManager.class, new Returns(createTempDirectory())));

        Map<String, Object> params = new HashMap<String, Object>();
        params.put("repository.git.useShallowClones", shallow);
        params.put("repository.github.useRemoteAgentCache", true);
        setRepositoryProperties(gitRepository, repositoryUrl, params);

        gitRepository.retrieveSourceCode(mockBuildContext(), targetRevision, getCheckoutDir(gitRepository));
        assertEquals(createJGitOperationHelper(null).extractCommits(getCheckoutDir(gitRepository), null, targetRevision).getChanges().size(), expectedChangesetCount);
    }

    @Test
    public void testShallowClone72Parents() throws Exception
    {
        File tmp = createTempDirectory();
        GitOperationHelper helper = createJGitOperationHelper(createAccessData("git://github.com/pstefaniak/72parents.git"));

        helper.fetch(tmp, "f9a3b37fcbf5298c1bfa", true);
        assertEquals(FileUtils.readLines(new File(tmp, ".git/shallow")).size(), 1);
        helper.checkout(null, tmp, "f9a3b37fcbf5298c1bfa", null);
        verifyContents(tmp, "shallow-clones/72parents-contents.zip");
    }

    @Test
    public void testDefaultFetchingToShallowedCopy() throws Exception
    {
        File tmp = createTempDirectory();
        GitOperationHelper helper3 = createJGitOperationHelper(createAccessData("git://github.com/pstefaniak/3.git"));

        helper3.fetch(tmp, "HEAD", true);
        assertEquals(FileUtils.readFileToString(new File(tmp, ".git/shallow")), "0a77ee667ee310b86022f0173b59174375ed4b5d\n");

        GitOperationHelper helper7 = createJGitOperationHelper(createAccessData("git://github.com/pstefaniak/7.git"));
        helper7.fetch(tmp, "HEAD", false);
        assertEquals(FileUtils.readFileToString(new File(tmp, ".git/shallow")), "0a77ee667ee310b86022f0173b59174375ed4b5d\n");
        helper7.checkout(null, tmp, "1070f438270b8cf1ca36", null);
        verifyContents(tmp, "shallow-clones/5-contents.zip");

        FileRepository repository = new FileRepository(new File(tmp, Constants.DOT_GIT));
        Iterable<RevCommit> log = new Git(repository).log().call();
        List<String> commits = new ArrayList<String>();
        for (RevCommit revCommit : log)
        {
            commits.add(revCommit.name());
        }

        Assert.assertEquals(commits.get(0), "1070f438270b8cf1ca36a026e70302208bf40349");
        Assert.assertEquals(commits.size(), 3);

        Assert.assertTrue(repository.getObjectDatabase().has(ObjectId.fromString("1070f438270b8cf1ca36a026e70302208bf40349")));
        Assert.assertFalse(repository.getObjectDatabase().has(ObjectId.fromString("145570eea6bf9f87a7bcf0cab2c995bf084b0698")));
        repository.close();
    }

    @Test
    public void testShallowDoesNotContainTooMuch() throws Exception
    {
        long sizeDeep = countFetchSize(false);
        long sizeShallow = countFetchSize(true);

        assertTrue(sizeDeep > sizeShallow + 500, "Expecting significant difference: " + sizeDeep + " >> " + sizeShallow);
    }

    private long countFetchSize(boolean useShallow)
            throws IOException, RepositoryException
    {
        File tmpDeep = createTempDirectory();
        GitOperationHelper helper = createJGitOperationHelper(createAccessData("git://github.com/pstefaniak/5.git"));

        helper.fetch(tmpDeep, "HEAD", useShallow);

        GitOperationHelper helper7 = createJGitOperationHelper(createAccessData("git://github.com/pstefaniak/7.git"));
        helper7.fetch(tmpDeep, "HEAD", false);

        RepositorySummary rsDeep = new RepositorySummary(tmpDeep);
        assertTrue(rsDeep.objects.isEmpty());
        long sizeDeep = 0;
        for (File pack : rsDeep.packs)
        {
            sizeDeep += pack.length();
        }
        return sizeDeep;
    }

    @Test
    public void testShallowCloneFromCacheDoesNotContainShallowInfo() throws Exception
    {
        GitRepository gitRepository = createGitRepository(AgentType.LOCAL);
        setRepositoryProperties(gitRepository, "git://github.com/pstefaniak/7.git", Collections.singletonMap("repository.git.useShallowClones", true));

        BuildRepositoryChanges buildChanges = gitRepository.collectChangesSinceLastBuild(PLAN_KEY.getKey(), null);
        gitRepository.retrieveSourceCode(mockBuildContext(), buildChanges.getVcsRevisionKey(), getCheckoutDir(gitRepository));

        FileRepository repository = register(new FileRepositoryBuilder().setWorkTree(getCheckoutDir(gitRepository)).build());
        Git git = new Git(repository);
        Iterable<RevCommit> commits = git.log().call();
        assertEquals(Iterables.size(commits), 7);
    }

}
