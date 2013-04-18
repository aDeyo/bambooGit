package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.commit.CommitContext;
import com.beust.jcommander.internal.Sets;
import com.google.common.collect.Iterables;
import org.apache.log4j.Logger;
import org.junit.Before;
import org.junit.Test;

import java.io.BufferedReader;
import java.io.StringReader;
import java.util.Date;

import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.COMMITER_EMAIL;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.COMMITER_NAME;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.COMMIT_MESSAGE;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.END_OF_COMMIT_MESSAGE;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.FILE_LIST;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.HASH;
import static com.atlassian.bamboo.plugins.git.CommitOutputHandler.TIMESTAMP;
import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;


public class CommitOutputHandlerTest
{
    private static final Logger log = Logger.getLogger(CommitOutputHandlerTest.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    private CommitOutputHandler commitOutputHandler;
    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Action Methods
    private void parseCommit(String gitLogString)
    {
        BufferedReader in = new BufferedReader(new StringReader(gitLogString));
        String line;
        int lineNum = 0;
        try
        {
            while ((line = in.readLine()) != null)
            {
                commitOutputHandler.processLine(lineNum, line);
                lineNum++;
            }
        }
        catch (Exception e)
        {
            throw new AssertionError(e);
        }
    }

    // -------------------------------------------------------------------------------------------------- Public Methods
    @Before
    public void setUp()
    {
        commitOutputHandler = new CommitOutputHandler(Sets.<String>newHashSet());
    }

    @Test
    public void testParsingCommit()
    {
        long currentTimeLong = new Date().getTime()/1000;
        Date currentTime = new Date(currentTimeLong*1000);
        String commitString = HASH+"tojesthash\n"+COMMITER_NAME+"namenamename\n"+COMMITER_EMAIL+"something@something.com\n"+TIMESTAMP+currentTimeLong+"\n"+COMMIT_MESSAGE+"commmit message\n more commit message\n"+ END_OF_COMMIT_MESSAGE +"\n" +FILE_LIST;
        parseCommit(commitString);
        CommitContext commit = Iterables.getFirst(commitOutputHandler.getExtractedCommits(), null);
        assertNotNull(commit);

        assertEquals("tojesthash", commit.getChangeSetId());
        assertEquals("commmit message\n more commit message", commit.getComment());
        assertEquals(currentTime, commit.getDate());

    }

    @Test
    public void testParsingCommitWithoutEolInMessage()
    {
        long currentTimeLong = new Date().getTime()/1000;
        Date currentTime = new Date(currentTimeLong*1000);
        String commitString = HASH+"tojesthash\n"+COMMITER_NAME+"namenamename\n"+COMMITER_EMAIL+"something@something.com\n"+TIMESTAMP+currentTimeLong+"\n"+COMMIT_MESSAGE+"commmit message\n more commit message"+ END_OF_COMMIT_MESSAGE +"\n" +FILE_LIST;
        parseCommit(commitString);
        CommitContext commit = Iterables.getFirst(commitOutputHandler.getExtractedCommits(), null);
        assertNotNull(commit);

        assertEquals("tojesthash", commit.getChangeSetId());
        assertEquals("commmit message\n more commit message", commit.getComment());
        assertEquals(currentTime, commit.getDate());
    }

    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators
}
