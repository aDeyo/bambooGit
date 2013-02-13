package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import org.testng.Assert;
import org.testng.annotations.DataProvider;
import org.testng.annotations.Test;

import java.io.File;
import java.lang.reflect.Field;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class GitCacheDirectoryTest extends GitAbstractTest
{
    @DataProvider
    Object[][] fieldInfluenceOnCacheLocationNonShallow()
    {
        return new Object[][] {
                {"repositoryUrl", true, null},
                {"username", true, null},

                {"branch", false, VcsBranch.class},
                {"password", false, null},
                {"sshKey", false, null},
                {"sshPassphrase", false, null},
        };
    }

    @Test(dataProvider = "fieldInfluenceOnCacheLocationNonShallow")
    public void testFieldInfluenceOnCacheLocatonNonShallow(final String field, final boolean different, final Class<?> clazz) throws Exception
    {
        doTestFieldInfluenceOnCacheLocaton(field, false, different, clazz);
    }

    @DataProvider
    Object[][] fieldInfluenceOnCacheLocationShallow()
    {
        return new Object[][] {
                {"repositoryUrl", true, null},
                {"username", true, null},
                {"branch", true, VcsBranch.class},

                {"password", false, null},
                {"sshKey", false, null},
                {"sshPassphrase", false, null},
        };
    }

    @Test(dataProvider = "fieldInfluenceOnCacheLocationShallow")
    public void testFieldInfluenceOnCacheLocatonShallow(String field, boolean different, final Class<?> clazz) throws Exception
    {
        doTestFieldInfluenceOnCacheLocaton(field, true, different, clazz);
    }

    private void doTestFieldInfluenceOnCacheLocaton(String field, boolean shallow, boolean different, Class<?> clazz) throws Exception
    {
        GitRepositoryAccessData accessData = createSampleAccessData(shallow);
        GitRepositoryAccessData accessData2 = createSampleAccessData(shallow);

        Field f = GitRepositoryAccessData.class.getDeclaredField(field);
        f.setAccessible(true);
        String val = f.get(accessData2).toString();
        if (clazz!=null && VcsBranch.class.isAssignableFrom(clazz))
        {
            f.set(accessData2, new VcsBranchImpl(val + "chg"));
        }
        else
        {
            f.set(accessData2, val + "chg");
        }


        File baseDir = createTempDirectory();
        File cache1 = GitCacheDirectory.getCacheDirectory(baseDir, accessData);
        File cache2 = GitCacheDirectory.getCacheDirectory(baseDir, accessData2);

        Assert.assertEquals(cache1.equals(cache2), !different);
    }

    @Test
    public void testShallowGetsDifferentCache() throws Exception
    {
        GitRepositoryAccessData accessData = createSampleAccessData(false);
        GitRepositoryAccessData accessData2 = createSampleAccessData(true);

        File baseDir = createTempDirectory();
        File cache1 = GitCacheDirectory.getCacheDirectory(baseDir, accessData);
        File cache2 = GitCacheDirectory.getCacheDirectory(baseDir, accessData2);

        Assert.assertFalse(cache1.equals(cache2));

    }

    @Test
    public void testShallowGetsDifferentCacheWithEmptyBranch() throws Exception
    {
        GitRepositoryAccessData accessDataNonShallow = createSampleAccessData(false);
        GitRepositoryAccessData accessDataShallow = createSampleAccessData(true);

        accessDataNonShallow = GitRepositoryAccessData.builder(accessDataNonShallow).branch("").build();
        accessDataShallow = GitRepositoryAccessData.builder(accessDataShallow).branch("").build();

        File baseDir = createTempDirectory();
        File cache1 = GitCacheDirectory.getCacheDirectory(baseDir, accessDataNonShallow);
        File cache2 = GitCacheDirectory.getCacheDirectory(baseDir, accessDataShallow);

        Assert.assertFalse(cache1.equals(cache2));

    }

    private static GitRepositoryAccessData createSampleAccessData(boolean shallow)
    {
        GitRepositoryAccessData accessData = createAccessData(
                "someUrl",
                "branch",
                "username",
                "password",
                "sshKey",
                "sshPass"
        );
        return GitRepositoryAccessData.builder(accessData).useShallowClones(shallow).build();
    }

    @Test(timeOut = 5000)
    public void testCallOnSameDirectoryBlocks() throws Exception
    {
        verifySecondThreadBlocks("repository.url", "repository.url", true);
    }

    @Test(timeOut = 5000)
    public void testCallOnDifferentDirectoryDoesNotBlock() throws Exception
    {
        verifySecondThreadBlocks("repository.url", "different.url", false);
    }

    private void verifySecondThreadBlocks(String firstUrl, String secondUrl, boolean blockExpected) throws Exception
    {
        final GitRepository repository1 = createGitRepository();
        setRepositoryProperties(repository1, firstUrl, "");

        final GitRepository repository2 = createGitRepository();
        repository2.setWorkingDir(repository1.getWorkingDirectory());
        setRepositoryProperties(repository2, secondUrl, "");

        final ArrayBlockingQueue<Boolean> hasBlocked = new ArrayBlockingQueue<Boolean>(1);
        final CountDownLatch firstCalled = new CountDownLatch(1);
        final CountDownLatch secondCalled = new CountDownLatch(1);


        Thread firstThread = new Thread("First thread") {
            @Override
            public void run()
            {
                try
                {
                    File cacheDirectory = repository1.getCacheDirectory();
                    GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<Void>()
                    {
                        public Void call() throws Exception
                        {
                            firstCalled.countDown();
                            boolean await = secondCalled.await(1000, TimeUnit.MILLISECONDS);
                            hasBlocked.put(!await); // await false = timeout
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        firstThread.start();

        Assert.assertTrue(firstCalled.await(1000, TimeUnit.MILLISECONDS), "First thread should be let in promptly");
        Thread secondThread = new Thread("Second thread") {
            @Override
            public void run()
            {
                try
                {
                    File cacheDirectory = repository2.getCacheDirectory();
                    GitCacheDirectory.getCacheLock(cacheDirectory).withLock(new Callable<Void>()
                    {
                        public Void call() throws Exception
                        {
                            secondCalled.countDown();
                            return null;
                        }
                    });
                }
                catch (Exception e)
                {
                    throw new RuntimeException(e);
                }
            }
        };

        secondThread.start();

        Assert.assertEquals(hasBlocked.take(), Boolean.valueOf(blockExpected), "Second thread blocking");
        Assert.assertTrue(secondCalled.await(2000, TimeUnit.MILLISECONDS), "Second thread should be eventually let in");
    }
}
