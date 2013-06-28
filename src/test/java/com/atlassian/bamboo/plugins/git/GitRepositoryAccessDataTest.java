package com.atlassian.bamboo.plugins.git;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;

import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.credentials.SshCredentials;
import com.atlassian.bamboo.credentials.SshCredentialsImpl;


@RunWith(MockitoJUnitRunner.class)
public class GitRepositoryAccessDataTest
{
    
    @Mock
    private CredentialsManager credentialsManager;
    
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
    
    @Test
    public void whenYouUseSHARED_CREDENTIALSTheObjectReturnsSshKeyforAuthenticationType()
    {
        Long credentialsId = 3L;
        SshCredentials credentials = new SshCredentialsImpl("nameSshCredentials", "sshKey", "sshPassphrase");
        when(credentialsManager.getSshCredentials(credentialsId)).thenReturn(credentials);
        
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .credentialsManager(credentialsManager)
            .authenticationType(GitAuthenticationType.SHARED_CREDENTIALS)
            .repositoryUrl("repositoryUrl")
            .username("")
            .password(null)
            .sshKey(null)
            .sshPassphrase(null)
            .sharedCredentialsId(credentialsId)
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
    public void whenYouUseSHARED_CREDENTIALSTheObjectReturnsSshKeyAndTheSharedCredentialsAreNotFound()
    {
        Long credentialsId = 3L;
        when(credentialsManager.getSshCredentials(credentialsId)).thenReturn(null);
        
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .credentialsManager(credentialsManager)
            .authenticationType(GitAuthenticationType.SHARED_CREDENTIALS)
            .repositoryUrl("repositoryUrl")
            .username("")
            .password(null)
            .sshKey(null)
            .sshPassphrase(null)
            .sharedCredentialsId(credentialsId)
            .useShallowClones(false)
            .useRemoteAgentCache(false)
            .useSubmodules(false)
            .commandTimeout(180)
            .verboseLogs(false)
            .build();
        
        assertEquals(GitAuthenticationType.SSH_KEYPAIR, accessData.getAuthenticationType());
        assertEquals(null, accessData.getSshKey());
        assertEquals(null, accessData.getSshPassphrase());
        
    }
    
    
    @Test
    public void whenYouUseSHARED_CREDENTIALSTheObjectReturnsEmptySshKeyAndTheSharedCredentialIdIsNull()
    {
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .credentialsManager(credentialsManager)
            .authenticationType(GitAuthenticationType.SHARED_CREDENTIALS)
            .repositoryUrl("repositoryUrl")
            .username("")
            .password(null)
            .sshKey(null)
            .sshPassphrase(null)
            .sharedCredentialsId(null)
            .useShallowClones(false)
            .useRemoteAgentCache(false)
            .useSubmodules(false)
            .commandTimeout(180)
            .verboseLogs(false)
            .build();
        
        assertEquals(GitAuthenticationType.SSH_KEYPAIR, accessData.getAuthenticationType());
        assertEquals(null, accessData.getSshKey());
        assertEquals(null, accessData.getSshPassphrase());
        
    }
    
    
    @Test
    public void whenYouUseSHARED_CREDENTIALSifYouDecryptTheSshKeyTheValuesDecryptedAreReturned()
    {
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .credentialsManager(credentialsManager)
            .authenticationType(GitAuthenticationType.SHARED_CREDENTIALS)
            .repositoryUrl("repositoryUrl")
            .sshKey("decrypted_sshKeyValue")
            .sshPassphrase("decrypted_sshPassphrase")
            .decryptedCredentials(true)
            .sharedCredentialsId(4L)
            .build();
        
        assertEquals(GitAuthenticationType.SSH_KEYPAIR, accessData.getAuthenticationType());
        assertEquals("decrypted_sshKeyValue", accessData.getSshKey());
        assertEquals("decrypted_sshPassphrase", accessData.getSshPassphrase());
        verify(credentialsManager, times(0)).getSshCredentials(anyLong());
        
    }
    
    @Test
    public void whenYouUseSHARED_CREDENTIALSifYouSetDecryptedCredentialsTheSshKeyTheValuesFromSharedCredentialsAreReturned()
    {
        Long credentialsId = 3L;
        SshCredentials credentials = new SshCredentialsImpl("nameSshCredentials", "sshKey", "sshPassphrase");
        when(credentialsManager.getSshCredentials(credentialsId)).thenReturn(credentials);
        
        GitRepositoryAccessData accessData = GitRepositoryAccessData.builder()
            .credentialsManager(credentialsManager)
            .authenticationType(GitAuthenticationType.SHARED_CREDENTIALS)
            .repositoryUrl("repositoryUrl")
            .username("")
            .password("")
            .sshKey("decrypted_sshKeyValue")
            .sshPassphrase("decrypted_sshPassphrase")
             // decryptedCredentials <- false by default
            .sharedCredentialsId(credentialsId)
            .build();
        
        assertEquals(GitAuthenticationType.SSH_KEYPAIR, accessData.getAuthenticationType());
        assertEquals("sshKey", accessData.getSshKey());
        assertEquals("sshPassphrase", accessData.getSshPassphrase());
        
    }
    
    
}
