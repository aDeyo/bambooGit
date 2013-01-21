package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.ssh.ProxyRegistrationInfo;
import org.jetbrains.annotations.NotNull;

import java.io.Serializable;

public final class GitHubRepositoryAccessData implements Serializable
{
    private String repository;
    private String branch;
    private String username;
    private String password;
    private boolean useShallowClones;
    private boolean useSubmodules;
    private int commandTimeout;
    private boolean verboseLogs;

    private transient ProxyRegistrationInfo proxyRegistrationInfo;

    public static final class Builder
    {
        private String repository;
        private String branch;
        private String username;
        private String password;
        private boolean useShallowClones;
        private boolean useSubmodules;
        private int commandTimeout;
        private boolean verboseLogs;

        public Builder clone(GitHubRepositoryAccessData gitRepositoryAccessData)
        {
            this.repository = gitRepositoryAccessData.repository;
            this.branch = gitRepositoryAccessData.branch;
            this.username = gitRepositoryAccessData.username;
            this.password = gitRepositoryAccessData.password;
            this.useShallowClones = gitRepositoryAccessData.useShallowClones;
            this.useSubmodules = gitRepositoryAccessData.useSubmodules;
            this.commandTimeout = gitRepositoryAccessData.commandTimeout;
            this.verboseLogs = gitRepositoryAccessData.verboseLogs;
            return this;
        }

        public Builder repository(final String repository)
        {
            this.repository = repository;
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

        public GitHubRepositoryAccessData build()
        {
            GitHubRepositoryAccessData data = new GitHubRepositoryAccessData();
            data.repository = this.repository;
            data.branch = this.branch;
            data.username = this.username;
            data.password = this.password;
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

    public static Builder builder(@NotNull GitHubRepositoryAccessData accessData)
    {
        return new Builder().clone(accessData);
    }

    public String getRepository()
    {
        return repository;
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