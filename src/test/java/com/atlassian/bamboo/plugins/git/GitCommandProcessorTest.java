package com.atlassian.bamboo.plugins.git;

import com.google.common.collect.Lists;
import org.jetbrains.annotations.NotNull;
import org.testng.annotations.Test;

import java.util.List;
import java.util.Map;

import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;

public class GitCommandProcessorTest
{
    private static final String TAG = "refs/tags/atlassian-bamboo-git-plugin-1.9.8";
    private static final String OLD_TAG = TAG + "^{}";
    private static final String TAG_HASH = "5c9f3532b29f4ae2753b73921e054d0de85a0716";
    private static final String OLD_TAG_HASH = "bfdae60b5354c50391f09c0369cdca74b0545671";
    private final static String[] LS_REMOTE_OUTPUT =  {
            "Warning: Permanently added '[127.0.0.1]:49091' (DSA) to the list of known hosts.",
            "From git@bitbucket.org:atlassian/bamboo-git-plugin.git",
            "24f5adaaa0c4cb153b7b231284cd3a50e31da3eb        HEAD",
            "057c74de40f56460a14c5a4e9f3102ab8339adb6        refs/heads/1.8.23-ondemand",
            "72a05fe8d3184996f96d5351412591a934a102e4        refs/heads/BAM-10937",
            "4a4dd973a5284969550b8e7eec5630d68395c8f7        refs/heads/BDEV-1084",
            "849d9e321dd3a364919a310284eee8525ae0e40b        refs/heads/BUILDENG-2047-1.9.3.x",
            "563f75dfaa8ba08d37a5e243646930e0f90c1b6a        refs/heads/atlassian-bamboo-plugin-git-1.3-branch",
            TAG_HASH + "        " + TAG,
            OLD_TAG_HASH + "        " + OLD_TAG
    };

    @Test
    public void testParsingRemotes()
    {
        final Map<String, String> result = GitCommandProcessor.parseLsRemoteOutput(
                new GitCommandProcessor.LineOutputHandlerImpl()
                {
                    @NotNull
                    @Override
                    public List<String> getLines()
                    {
                        return Lists.newArrayList(LS_REMOTE_OUTPUT);
                    }
                });
        assertEquals(result.size(), 7);
        assertEquals(result.get(TAG), TAG_HASH);
        assertFalse(result.containsKey(OLD_TAG));
    }
}
