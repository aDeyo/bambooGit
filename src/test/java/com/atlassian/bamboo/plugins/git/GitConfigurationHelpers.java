package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;

public class GitConfigurationHelpers {

    public static void addMandatoryConfigurationSettings(final BuildConfiguration conf)
    {
        conf.setProperty(GitRepository.REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.NONE.toString());
    }
}
