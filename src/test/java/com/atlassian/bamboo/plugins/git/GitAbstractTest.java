package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.FeatureManager;
import com.atlassian.bamboo.agent.AgentType;
import com.atlassian.bamboo.build.BuildLoggerManager;
import com.atlassian.bamboo.build.context.BuildContextBuilderImpl;
import com.atlassian.bamboo.build.fileserver.BuildDirectoryManager;
import com.atlassian.bamboo.build.logger.NullBuildLogger;
import com.atlassian.bamboo.chains.Chain;
import com.atlassian.bamboo.core.TransportProtocol;
import com.atlassian.bamboo.credentials.CredentialsAccessor;
import com.atlassian.bamboo.plan.PlanKey;
import com.atlassian.bamboo.plan.PlanKeys;
import com.atlassian.bamboo.plan.branch.BranchIntegrationHelper;
import com.atlassian.bamboo.plan.branch.BranchIntegrationService;
import com.atlassian.bamboo.plan.branch.VcsBranchImpl;
import com.atlassian.bamboo.project.Project;
import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.security.EncryptionService;
import com.atlassian.bamboo.security.EncryptionServiceImpl;
import com.atlassian.bamboo.ssh.SshProxyService;
import com.atlassian.bamboo.util.BambooFileUtils;
import com.atlassian.bamboo.utils.i18n.DefaultI18nBean;
import com.atlassian.bamboo.utils.i18n.I18nResolverAdapter;
import com.atlassian.bamboo.v2.build.BuildContext;
import com.atlassian.bamboo.v2.build.agent.capability.CapabilityContext;
import com.atlassian.bamboo.variable.CustomVariableContext;
import com.atlassian.bamboo.variable.CustomVariableContextImpl;
import com.atlassian.bamboo.variable.VariableContext;
import com.atlassian.bamboo.variable.VariableDefinitionContext;
import com.atlassian.bamboo.variable.VariableDefinitionManager;
import com.atlassian.bamboo.ww2.actions.build.admin.create.BuildConfiguration;
import com.atlassian.plugin.PluginAccessor;
import com.atlassian.sal.api.message.I18nResolver;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.filefilter.FileFilterUtils;
import org.eclipse.jgit.lib.Repository;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.mockito.Matchers;
import org.mockito.Mockito;
import org.mockito.internal.stubbing.answers.Returns;
import org.testng.annotations.AfterClass;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import static org.apache.commons.io.FileUtils.listFiles;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.testng.Assert.assertEquals;

public class GitAbstractTest
{
    public static final PlanKey PLAN_KEY = PlanKeys.getPlanKey("PLAN-KEY");
    private final Collection<File> filesToCleanUp = Collections.synchronizedCollection(new ArrayList<File>());
    private final Collection<Repository> repositoriesToCleanUp = Collections.synchronizedCollection(new ArrayList<Repository>());
    private static final ResourceBundle resourceBundle = ResourceBundle.getBundle("com.atlassian.bamboo.plugins.git.i18n", Locale.US);

    protected static final String COMITTER_NAME = "Committer";
    protected static final String COMITTER_EMAIL = "committer@example.com";
    private static final String REPOSITORY_GIT_AUTHENTICATION_TYPE = "repository.git.authenticationType";
    protected static final EncryptionService encryptionService = new EncryptionServiceImpl();

    public static void setRepositoryPropertiesWithFetchAll(GitRepository gitRepository, File repositorySourceDir, String branch) throws Exception
    {
        setRepositoryProperties(gitRepository, repositorySourceDir.getAbsolutePath(), branch, null, null, ImmutableMap.of(GitRepository.REPOSITORY_GIT_FETCH_WHOLE_REPOSITORY, "true"));
    }

    public static void setRepositoryProperties(GitRepository gitRepository, File repositorySourceDir, String branch) throws Exception
    {
        setRepositoryProperties(gitRepository, repositorySourceDir.getAbsolutePath(), branch);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, branch, null, null);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, @Nullable String sshKey, @Nullable String sshPassphrase) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, branch, sshKey, sshPassphrase, Collections.<String, String>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, String branch, String sshKey, String sshPassphrase, Map<String, String> paramMap) throws Exception
    {
        Map<String, String> params = Maps.newHashMap(paramMap);

        params.put("repository.git.branch", branch);
        params.put(REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.NONE.name());
        if (sshKey != null)
        {
            params.put("repository.git.ssh.key", encryptionService.encrypt(sshKey));
            params.put(REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.SSH_KEYPAIR.name());
        }
        if (sshPassphrase != null)
        {
            params.put("repository.git.ssh.passphrase", encryptionService.encrypt(sshPassphrase));
        }
        setRepositoryProperties(gitRepository, repositoryUrl, params);
    }

    public static void setRepositoryProperties(GitRepository gitRepository, File repositorySourceDir) throws Exception
    {
        setRepositoryProperties(gitRepository, repositorySourceDir.getAbsolutePath(), Collections.<String, Object>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl) throws Exception
    {
        setRepositoryProperties(gitRepository, repositoryUrl, Collections.<String, Object>emptyMap());
    }

    public static void setRepositoryProperties(GitRepository gitRepository, String repositoryUrl, Map<String, ?> paramMap) throws Exception
    {

        BuildConfiguration buildConfiguration = new BuildConfiguration();
        buildConfiguration.setProperty("repository.git.repositoryUrl", repositoryUrl);
        buildConfiguration.setProperty(REPOSITORY_GIT_AUTHENTICATION_TYPE, GitAuthenticationType.NONE.name());

        for (Map.Entry<String, ?> entry : paramMap.entrySet())
        {
            buildConfiguration.setProperty(entry.getKey(), entry.getValue());
        }

        if (gitRepository.validate(buildConfiguration).hasAnyErrors())
        {
            throw new Exception("validation failed");
        }
        gitRepository.populateFromConfig(buildConfiguration);
    }

    public JGitOperationHelper createJGitOperationHelper(final GitRepositoryAccessData accessData)
    {
        I18nResolver i18nResolver = Mockito.mock(I18nResolver.class);
        return new JGitOperationHelper(accessData, new NullBuildLogger(), i18nResolver);
    }

    public NativeGitOperationHelper createNativeGitOperationHelper(final GitRepositoryAccessData accessData) throws RepositoryException
    {
        I18nResolver i18nResolver = Mockito.mock(I18nResolver.class);
        final GitRepository repository = Mockito.mock(GitRepository.class);
        Mockito.when(repository.getWorkingDirectory()).thenReturn(new File("/"));
        Mockito.when(repository.getGitCapability()).thenReturn("git");
        final SshProxyService sshProxyService = Mockito.mock(SshProxyService.class);
        return new NativeGitOperationHelper(repository, accessData, sshProxyService, new NullBuildLogger(), i18nResolver);
    }

    public GitRepository createNativeGitRepository() throws Exception
    {
        return _createGitRepository(new NativeGitRepositoryFixture());
    }

    public GitRepository createGitRepository(final AgentType agentType) throws Exception
    {
        return _createGitRepository(new GitRepositoryFixture(agentType));
    }

    public GitRepository _createGitRepository(GitRepository fixture) throws Exception
    {
        File workingDirectory = createTempDirectory();

        BuildLoggerManager buildLoggerManager = Mockito.mock(BuildLoggerManager.class, new Returns(new NullBuildLogger()));
        fixture.setBuildLoggerManager(buildLoggerManager);

        BuildDirectoryManager buildDirectoryManager = Mockito.mock(BuildDirectoryManager.class, new Returns(workingDirectory));
        fixture.setBuildDirectoryManager(buildDirectoryManager);
        fixture.setI18nResolver(getI18nResolver());

        CustomVariableContext customVariableContext = new CustomVariableContextImpl();
        customVariableContext.setVariables(Maps.<String, VariableDefinitionContext>newHashMap());
        fixture.setCustomVariableContext(customVariableContext);
        
        fixture.setCapabilityContext(mock(CapabilityContext.class));

        SshProxyService sshProxyService = mock(SshProxyService.class);
        fixture.setSshProxyService(sshProxyService);

        final BranchIntegrationHelper branchIntegrationHelper = mock(BranchIntegrationHelper.class);
        Mockito.when(branchIntegrationHelper.getCommitterName(fixture)).thenReturn(COMITTER_NAME);
        Mockito.when(branchIntegrationHelper.getCommitterEmail(fixture)).thenReturn(COMITTER_EMAIL);
        fixture.setBranchIntegrationHelper(branchIntegrationHelper);

        FeatureManager featureManager = mock(FeatureManager.class);
        when(featureManager.isTransportSupported(Matchers.<TransportProtocol>any())).thenReturn(true);

        fixture.setFeatureManager(featureManager);

        fixture.setEncryptionService(encryptionService);

        return fixture;
    }

    public File createTempDirectory() throws IOException
    {
        File tmp = BambooFileUtils.createTempDirectory("bamboo-git-plugin-test");
        FileUtils.forceDeleteOnExit(tmp);
        filesToCleanUp.add(tmp);
        return tmp;
    }

    protected <T extends Repository> T register(T repository)
    {
        repositoriesToCleanUp.add(repository);
        return repository;
    }

    public static void verifyContents(File directory, final String expectedZip) throws IOException
    {
        final Enumeration<? extends ZipEntry> entries = new ZipFile(GitAbstractTest.class.getResource("/" + expectedZip).getFile()).entries();
        int fileCount = 0;
        while (entries.hasMoreElements())
        {
            ZipEntry zipEntry = entries.nextElement();
            if (!zipEntry.isDirectory())
            {
                fileCount++;
                String fileName = zipEntry.getName();
                final File file = new File(directory, fileName);
                assertEquals(FileUtils.checksumCRC32(file), zipEntry.getCrc(), "CRC for " + file);
            }
        }

        final Collection files = listFiles(directory, FileFilterUtils.trueFileFilter(), FileFilterUtils.notFileFilter(FileFilterUtils.nameFileFilter(".git")));
        assertEquals(files.size(), fileCount, "Number of files");
    }

    public static I18nResolver getI18nResolver()
    {
        DefaultI18nBean bean = new DefaultI18nBean(Locale.US, Mockito.mock(PluginAccessor.class));
        bean.getI18nBundles().add(resourceBundle);
        return new I18nResolverAdapter(bean);
    }

    protected static GitRepositoryAccessData createAccessData(@NotNull String repositoryUrl)
    {
        return createAccessData(repositoryUrl, null);
    }

    protected static GitRepositoryAccessData createAccessData(File repositoryFile, String branch)
    {
        return createAccessData(repositoryFile.getAbsolutePath(), branch);
    }

    protected static GitRepositoryAccessData createAccessData(@NotNull String repositoryUrl, @Nullable String branch)
    {
        return createAccessData(repositoryUrl, branch, null, null, null, null);
    }

    protected static GitRepositoryAccessData createAccessData(@NotNull String repositoryUrl, String branch, @Nullable String username, @Nullable String password, @Nullable String sshKey, @Nullable String sshPassphrase)
    {
        GitRepositoryAccessData.Builder builder = GitRepositoryAccessData.builder()
                .repositoryUrl(repositoryUrl)
                .branch(new VcsBranchImpl(branch))
                .username(username)
                .password(password)
                .sshKey(sshKey)
                .sshPassphrase(sshPassphrase)
                .commandTimeout(1);
        if (password!=null)
        {
            builder.authenticationType(GitAuthenticationType.PASSWORD);
        }
        if (password==null && sshKey==null)
        {
            builder.authenticationType(GitAuthenticationType.NONE);
        }
        return builder.build();
    }

    protected static BuildContext mockBuildContext()
    {
        Chain chain = Mockito.mock(Chain.class);
        Mockito.when(chain.getKey()).thenReturn(PLAN_KEY.toString());

        Project project = Mockito.mock(Project.class);
        Mockito.when(chain.getProject()).thenReturn(project);
        return new BuildContextBuilderImpl(Mockito.mock(BranchIntegrationService.class),
                Mockito.mock(VariableDefinitionManager.class),
                Mockito.mock(CredentialsAccessor.class), null)
                .plan(chain)
                .buildNumber(1)
                .variableContext(Mockito.mock(VariableContext.class))
                .build();
    }

    protected static File getCheckoutDir(GitRepository gitRepository)
    {
        return new File(gitRepository.getWorkingDirectory(), "checkoutDir");
    }

    @AfterClass
    void cleanUpFiles()
    {
        for (File file : filesToCleanUp)
        {
            FileUtils.deleteQuietly(file);
        }
        for (Repository repository : repositoriesToCleanUp)
        {
            repository.close();
        }
    }

    static class GitRepositoryFixture extends GitRepository
    {
        private final AgentType agentType;

        GitRepositoryFixture(final AgentType agentType)
        {
            this.agentType = agentType;
        }

        @Override
        public String getGitCapability()
        {
            return "";
        }

        @Override
        public File getCacheDirectory()
        {
            return GitCacheDirectory.getCacheDirectory(getWorkingDirectory(), getSubstitutedAccessData());
        }

        @Override
        protected boolean isOnLocalAgent()
        {
            return agentType==AgentType.LOCAL;
        }
    }

    static class NativeGitRepositoryFixture extends GitRepositoryFixture
    {
        NativeGitRepositoryFixture()
        {
            super(AgentType.LOCAL);
        }

        @Override
        public String getGitCapability()
        {
            return "git";
        }
    }
}
