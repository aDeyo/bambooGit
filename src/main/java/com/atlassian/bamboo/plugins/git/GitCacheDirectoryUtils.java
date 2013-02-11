package com.atlassian.bamboo.plugins.git;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.ArrayUtils;
import org.apache.log4j.Logger;

import java.io.File;
import java.io.FileFilter;
import java.io.IOException;
import java.util.Collection;

/**
 * Utilities for handling operations on the git cache directory.
 *
 * @since 2.6
 */
public class GitCacheDirectoryUtils
{

    private static final Logger log = Logger.getLogger(GitCacheDirectoryUtils.class);

    public void deleteCacheDirectoriesExcept(File baseCacheDirectory, final Collection<String> dirs)
    {
        deleteCacheDirectories(baseCacheDirectory, new FileFilter()
        {
            public boolean accept(File pathName)
            {
                return (!dirs.contains(pathName.getName())) && pathName.isDirectory();
            }
        });
    }

    public void deleteCacheDirectories(File baseCacheDirectory, final Collection<String> dirs)
    {
        deleteCacheDirectories(baseCacheDirectory, new FileFilter()
        {
            public boolean accept(File pathName)
            {
                return dirs.contains(pathName.getName()) && pathName.isDirectory();
            }
        });
    }

    private void deleteCacheDirectories(File baseCacheDirectory, FileFilter filter)
    {
        final File[] toDelete = GitCacheDirectory.getCacheDirectoryRoot(baseCacheDirectory).listFiles(filter); // will be null if cacheRootDir does not exist
        if (ArrayUtils.isEmpty(toDelete))
        {
            log.info("No cache directories to delete found.");
        }
        else
        {
            for (File dir : toDelete)
            {
                try
                {
                    FileUtils.deleteDirectory(dir);
                    log.info("Successfully deleted cache directory: " + dir);
                }
                catch (IOException e)
                {
                    log.error("Failed to delete cache directory: " + dir, e);
                }
            }
        }
    }

}
