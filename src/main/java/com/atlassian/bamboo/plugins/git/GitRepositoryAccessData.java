package com.atlassian.bamboo.plugins.git;

import java.io.Serializable;

import org.jetbrains.annotations.NotNull;

import com.atlassian.bamboo.credentials.CredentialsManager;
import com.atlassian.bamboo.credentials.Credentials;
import com.atlassian.bamboo.credentials.SshCredentialsImpl;
import com.atlassian.bamboo.plan.branch.VcsBranch;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.ssh.ProxyRegistrationInfo;

public final class GitRepositoryAccessData implements Serializable
{
    private String repositoryUrl;
    private VcsBranch branch;
    private String username;
    private String password;
    private String sshKey;
    private String sshPassphrase;
    private GitAuthenticationType authenticationType;
    private boolean useShallowClones;
    private boolean useRemoteAgentCache;
    private boolean useSubmodules;
    private int commandTimeout;
    private boolean verboseLogs;
    private boolean useSharedCredentials;
    private Long sharedCredentialsId;
    private boolean decryptedCredentials;

    private transient ProxyRegistrationInfo proxyRegistrationInfo;
    private transient CredentialsManager credentialsManager;
    

    public static final class Builder
    {
        private String repositoryUrl;
        private VcsBranch branch;
        private String username;
        private String password;
        private String sshKey;
        private String sshPassphrase;
        private GitAuthenticationType authenticationType;
        private boolean useShallowClones;
        private boolean useRemoteAgentCache;
        private boolean useSubmodules;
        private int commandTimeout;
        private boolean verboseLogs;
        private Long sharedCredentialsId;
        private boolean decryptedCredentials;
        private CredentialsManager credentialsManager;

        public Builder clone(GitRepositoryAccessData gitRepositoryAccessData)
        {
            this.repositoryUrl = gitRepositoryAccessData.repositoryUrl;
            this.branch = gitRepositoryAccessData.branch;
            this.username = gitRepositoryAccessData.username;
            this.password = gitRepositoryAccessData.password;
            this.sshKey = gitRepositoryAccessData.sshKey;
            this.sshPassphrase = gitRepositoryAccessData.sshPassphrase;
            this.authenticationType = gitRepositoryAccessData.authenticationType;
            this.useShallowClones = gitRepositoryAccessData.useShallowClones;
            this.useSubmodules = gitRepositoryAccessData.useSubmodules;
            this.useRemoteAgentCache = gitRepositoryAccessData.useRemoteAgentCache;
            this.commandTimeout = gitRepositoryAccessData.commandTimeout;
            this.verboseLogs = gitRepositoryAccessData.verboseLogs;
            this.sharedCredentialsId = gitRepositoryAccessData.sharedCredentialsId;
            this.credentialsManager = gitRepositoryAccessData.credentialsManager;
            this.decryptedCredentials = gitRepositoryAccessData.decryptedCredentials;
            return this;
        }

        public Builder repositoryUrl(final String repositoryUrl)
        {
            this.repositoryUrl = repositoryUrl;
            return this;
        }

        @Deprecated
        public Builder branch(final String branch)
        {
            this.branch = new VcsBranchImpl(branch);
            return this;
        }

        public Builder branch(final VcsBranch branch)
        {
            this.branch = branch;
            return this;
        }

        public Builder username(final String username)
        {
            this.username = username;
            return this;
        }

        public Builder password(final String password)
        {
            this.password = password;
            return this;
        }

        public Builder sshKey(final String sshKey)
        {
            this.sshKey = sshKey;
            return this;
        }

        public Builder sshPassphrase(final String sshPassphrase)
        {
            this.sshPassphrase = sshPassphrase;
            return this;
        }

        public Builder authenticationType(final GitAuthenticationType authenticationType)
        {
            this.authenticationType = authenticationType;
            return this;
        }

        public Builder useShallowClones(final boolean useShallowClones)
        {
            this.useShallowClones = useShallowClones;
            return this;
        }

        public Builder useRemoteAgentCache(final boolean useRemoteAgentCache)
        {
            this.useRemoteAgentCache = useRemoteAgentCache;
            return this;
        }

        public Builder useSubmodules(final boolean useSubmodules)
        {
            this.useSubmodules = useSubmodules;
            return this;
        }

        public Builder commandTimeout(final int commandTimeout)
        {
            this.commandTimeout = commandTimeout;
            return this;
        }

        public Builder verboseLogs(final boolean verboseLogs)
        {
            this.verboseLogs = verboseLogs;
            return this;
        }
        public Builder sharedCredentialsId(Long sharedCredentialsId)
        {
            this.sharedCredentialsId = sharedCredentialsId;
            return this;
        }

        public Builder credentialsManager(CredentialsManager credentialsManager)
        {
            this.credentialsManager = credentialsManager;
            return this;
        }
        
        public Builder decryptedCredentials(boolean decryptedCredentials)
        {
            this.decryptedCredentials = decryptedCredentials;
            return this;
        }

        public GitRepositoryAccessData build()
        {
            GitRepositoryAccessData data = new GitRepositoryAccessData();
            data.repositoryUrl = this.repositoryUrl;
            data.branch = this.branch;
            data.username = this.username;
            data.password = this.password;
            data.sshKey = this.sshKey;
            data.sshPassphrase = this.sshPassphrase;
            data.authenticationType = this.authenticationType;
            data.useShallowClones = this.useShallowClones;
            data.useRemoteAgentCache = this.useRemoteAgentCache;
            data.useSubmodules = this.useSubmodules;
            data.commandTimeout = this.commandTimeout;
            data.verboseLogs = this.verboseLogs;
            data.sharedCredentialsId = this.sharedCredentialsId;
            data.credentialsManager = this.credentialsManager;
            data.decryptedCredentials = this.decryptedCredentials;
            data.useSharedCredentials = GitAuthenticationType.SHARED_CREDENTIALS.equals(this.authenticationType);
            return data;
        }
      
    }

   public static Builder builder()
    {
        return new Builder();
    }

    public static Builder builder(@NotNull GitRepositoryAccessData accessData)
    {
        return new Builder().clone(accessData);
    }

    public String getRepositoryUrl()
    {
        return repositoryUrl;
    }

    public void setRepositoryUrl(final String repositoryUrl)
    {
        this.repositoryUrl = repositoryUrl;
    }

    @Deprecated
    public String getBranch()
    {
        return branch.getName();
    }

    public VcsBranch getVcsBranch()
    {
        return branch;
    }

    public String getUsername()
    {
        return username;
    }

    protected void setUsername(final String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public String getSshKey()
    {
        if(!decryptedCredentials && useSharedCredentials && sharedCredentialsId != null)
        {
            Credentials credentials = credentialsManager.getCredentials(this.sharedCredentialsId);
            if(credentials != null)
            {
                return new SshCredentialsImpl(credentials).getSshKey();
            }
        }
        return sshKey;
    }

    public String getSshPassphrase()
    {
        if(!decryptedCredentials && useSharedCredentials  && sharedCredentialsId != null)
        {
            Credentials credentials = credentialsManager.getCredentials(this.sharedCredentialsId);
            if(credentials != null)
            {
                return new SshCredentialsImpl(credentials).getSshPassphrase();
            }
        }
        
        return sshPassphrase;
    }

    public GitAuthenticationType getAuthenticationType()
    {
        if(useSharedCredentials)
        {
            return GitAuthenticationType.SSH_KEYPAIR;
        }
        return authenticationType;
    }

    public String getAuthenticationTypeString()
    {
        return  authenticationType != null ? authenticationType.name() : null;
    }

    public boolean isUseShallowClones()
    {
        return useShallowClones;
    }

    public boolean isUseRemoteAgentCache()
    {
        return useRemoteAgentCache;
    }

    public boolean isUseSubmodules()
    {
        return useSubmodules;
    }

    public int getCommandTimeout()
    {
        return commandTimeout;
    }

    public boolean isVerboseLogs()
    {
        return verboseLogs;
    }

    public ProxyRegistrationInfo getProxyRegistrationInfo()
    {
        return proxyRegistrationInfo;
    }

    public void setProxyRegistrationInfo(final ProxyRegistrationInfo proxyRegistrationInfo)
    {
        this.proxyRegistrationInfo = proxyRegistrationInfo;
    }

    public Long getSharedCredentialsId()
    {
        return sharedCredentialsId;
    }

    public void setSharedCredentialsId(Long sharedCredentialsId)
    {
        this.sharedCredentialsId = sharedCredentialsId;
    }
    
}