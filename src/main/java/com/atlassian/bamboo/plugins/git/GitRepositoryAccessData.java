package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.ssh.ProxyRegistrationInfo;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public final class GitRepositoryAccessData implements Serializable
{
    private String repositoryUrl;
    private String branch;
    private String username;
    private String password;
    private String sshKey;
    private String sshPassphrase;
    private GitAuthenticationType authenticationType;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private int commandTimeout;
    private boolean verboseLogs;

    private transient ProxyRegistrationInfo proxyRegistrationInfo;

    public static final class Builder
    {
        private String repositoryUrl;
        private String branch;
        private String username;
        private String password;
        private String sshKey;
        private String sshPassphrase;
        private GitAuthenticationType authenticationType;
        private boolean useShallowClones;
        private boolean useSubmodules;
        private int commandTimeout;
        private boolean verboseLogs;

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
            this.commandTimeout = gitRepositoryAccessData.commandTimeout;
            this.verboseLogs = gitRepositoryAccessData.verboseLogs;
            return this;
        }

        public Builder repositoryUrl(final String repositoryUrl)
        {
            this.repositoryUrl = repositoryUrl;
            return this;
        }

        public Builder branch(final String branch)
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
            data.useSubmodules = this.useSubmodules;
            data.commandTimeout = this.commandTimeout;
            data.verboseLogs = this.verboseLogs;
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

    public String getBranch()
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
        return sshKey;
    }

    public String getSshPassphrase()
    {
        return sshPassphrase;
    }

    public GitAuthenticationType getAuthenticationType()
    {
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
}