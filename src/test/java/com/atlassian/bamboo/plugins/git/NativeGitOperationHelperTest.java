package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.build.logger.BuildLogger;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.sal.api.message.I18nResolver;
import org.apache.commons.lang.SystemUtils;
import org.junit.Test;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class NativeGitOperationHelperTest
{
    @Test
    public void survivesUrlsWithoutScheme() throws RepositoryException
    {
        final String localRepositoryUrl = "/pbruski@stash-dev.atlassian.com/scm/BAM/bamboo-stash-plugin.git";
        final GitRepositoryAccessData accessData = createAccessData(
                GitAuthenticationType.NONE,
                localRepositoryUrl);

        final NativeGitOperationHelper nativeGitOperationHelper = newNativeGitOperationHelper(accessData);

        final GitRepositoryAccessData gitRepositoryAccessData = nativeGitOperationHelper.adjustRepositoryAccess(accessData);

        assertThat(gitRepositoryAccessData.getRepositoryUrl(), equalTo(localRepositoryUrl));
    }

    private GitRepositoryAccessData createAccessData(final GitAuthenticationType gitAuthenticationType, final String repositoryUrl)
    {
        return GitRepositoryAccessData.builder()
                    .authenticationType(gitAuthenticationType)
                    .commandTimeout(1)
                    .repositoryUrl(repositoryUrl).build();
    }

    private NativeGitOperationHelper newNativeGitOperationHelper(final GitRepositoryAccessData gitRepositoryAccessData) throws RepositoryException
    {
        final GitRepository gitRepository = mock(GitRepository.class);
        when(gitRepository.getGitCapability()).thenReturn("/usr/bin/git");
        when(gitRepository.getWorkingDirectory()).thenReturn(SystemUtils.getJavaIoTmpDir());

        return new NativeGitOperationHelper(
                gitRepository,
                gitRepositoryAccessData,
                mock(SshProxyService.class),
                mock(BuildLogger.class),
                mock(I18nResolver.class));
    }
}
