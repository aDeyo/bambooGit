package com.atlassian.bamboo.plugins.git;

import com.google.common.collect.Sets;
import org.eclipse.jgit.util.FileUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;
import static org.hamcrest.collection.IsIterableContainingInAnyOrder.containsInAnyOrder;

/**
 * @since 2.6
 */
@RunWith (Parameterized.class)
public class GitCacheDirectoryUtilsTest
{

    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();


    private File gitCacheRoot;

    private static final String[] allDirectories = new String[] { "aa", "bb", "cc", "dd", "ee" };

    @Parameterized.Parameters
    public static Collection directoriesToDelete()
    {
        return Arrays.asList(new Object[][] {
                { asList(allDirectories) },
                { asList("aa", "cc") },
                { asList("zz", "xx") },
                { asList("aa", "bb", "cc", "dd", "ee", "ff") },
                { Collections.emptyList() },
                { asList("dd", "bb") }
        });
    }

    private List<String> dirs;

    public GitCacheDirectoryUtilsTest(List<String> dirs)
    {
        this.dirs = dirs;
    }

    @Before
    public void setUpDirectory() throws IOException
    {
        gitCacheRoot = GitCacheDirectory.getCacheDirectoryRoot(temporaryFolder.getRoot());
        FileUtils.mkdirs(gitCacheRoot);
        for (String dirName : allDirectories)
        {
            FileUtils.mkdir(new File(gitCacheRoot, dirName));
        }
    }

    @Test
    public void testIfProperDirectoriesAreDeleted()
    {
        new GitCacheDirectoryUtils().deleteCacheDirectories(temporaryFolder.getRoot(), dirs);

        Set<String> expectedDirectoriesRemaining = Sets.newHashSet(allDirectories);
        expectedDirectoriesRemaining.removeAll(dirs);
        Assert.assertThat("Expected different set of directories after deleting", asList(gitCacheRoot.list()), containsInAnyOrder(expectedDirectoriesRemaining.toArray()));
    }

    @Test
    public void testIfProperDirectoriesRemain()
    {
        new GitCacheDirectoryUtils().deleteCacheDirectoriesExcept(temporaryFolder.getRoot(), dirs);

        Set<String> expectedDirectoriesRemaining = Sets.newHashSet(allDirectories);
        expectedDirectoriesRemaining.retainAll(dirs);
        Assert.assertThat("Expected different set of directories after deleting", asList(gitCacheRoot.list()), containsInAnyOrder(expectedDirectoriesRemaining.toArray()));
    }

}
