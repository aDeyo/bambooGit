package com.atlassian.bamboo.plugins.git;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import static org.junit.Assert.assertEquals;


@RunWith(MockitoJUnitRunner.class)
public class GitRepositoryAccessDataTest
{
    
    @Test
    public void whenYouUseSSH_KEYPAIRforAuthenticationTypetheObjectReturnsTheRightValues()
    {
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .repositoryUrl("repositoryUrl")
            .username("")
            .password(null)
            .sshKey("sshKey")
            .sshPassphrase("sshPassphrase")
            .authenticationType(GitAuthenticationType.SSH_KEYPAIR)
            .useShallowClones(false)
            .useRemoteAgentCache(false)
            .useSubmodules(false)
            .commandTimeout(180)
            .verboseLogs(false)
            .build();
        
        assertEquals(GitAuthenticationType.SSH_KEYPAIR, accessData.getAuthenticationType());
        assertEquals("sshKey", accessData.getSshKey());
        assertEquals("sshPassphrase", accessData.getSshPassphrase());
    }
    
    @Test
    public void whenYouUsePASSWORDforAuthenticationTypetheObjectReturnsTheRightValues()
    {
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .repositoryUrl("repositoryUrl")
            .username("username")
            .password("password")
            .sshKey("")
            .sshPassphrase(null)
            .authenticationType(GitAuthenticationType.PASSWORD)
            .useShallowClones(false)
            .useRemoteAgentCache(false)
            .useSubmodules(false)
            .commandTimeout(180)
            .verboseLogs(false)
            .build();
        
        assertEquals(GitAuthenticationType.PASSWORD, accessData.getAuthenticationType());
        assertEquals("username", accessData.getUsername());
        assertEquals("password", accessData.getPassword());
    }
}
