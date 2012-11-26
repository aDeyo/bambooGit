package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.utils.Pair;
import com.google.common.collect.Lists;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.apache.sshd.ClientChannel;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.SshServer;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.auth.UserAuthNone;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ChangeDetectionPerformanceTest extends GitAbstractTest
{
    private static final Logger log = Logger.getLogger(ChangeDetectionPerformanceTest.class);
    private static final int NUM_CD_RUNS = 100;
    private static final String GIT_COMMAND = "git-upload-pack '/pbruski/bamboo-git.git'";
    private SshServer apacheSshdServer;
    private Callable<String> CD_JGIT;
    private Callable<String> CD_NATIVE_GIT;
    private Callable<String> CD_NATIVE_SSH;
    private Callable<String> CD_JAVA_SSH;
    private SshClient apacheSshdClient;

    @BeforeClass
    public void setUp() throws IOException, RepositoryException
    {
        Logger.getRootLogger().setLevel(Level.WARN);
        apacheSshdServer = createServer();
        apacheSshdClient = createApacheSshdClient();

        CD_JGIT = new Callable<String>()
        {
            final JGitOperationHelper gitOperationHelper = createJGitOperationHelper(createAccessData(getServerUrl(), "master", "user", "password", null, null));

            @Override
            public String call() throws Exception
            {
                return gitOperationHelper.obtainLatestRevision();
            }
        };

        CD_NATIVE_GIT = new Callable<String>()
        {
            final NativeGitOperationHelper gitOperationHelper = createNativeGitOperationHelper(createAccessData(getServerUrl(), "master", null, null, null, null));

            @Override
            public String call() throws Exception
            {
                return gitOperationHelper.obtainLatestRevision();
            }
        };

        CD_NATIVE_SSH = new Callable<String>()
        {
            final ProcessBuilder pb = new ProcessBuilder(
                    "ssh",
                    "-p" + apacheSshdServer.getPort(),
                    apacheSshdServer.getHost(),
                    "-o StrictHostKeyChecking=no",
                    "-o BatchMode=yes",
                    "-o UserKnownHostsFile=/dev/null",
                    GIT_COMMAND);

            @Override
            public String call() throws Exception
            {
                Process process = pb.start();
                InputStream inputStream = process.getInputStream();

                String response = getUntilEom(inputStream);

                writeEom(process.getOutputStream());

                return getRevision(response);
            }
        };

        CD_JAVA_SSH = new Callable<String>()
        {
            @Override
            public String call() throws Exception
            {
                ClientSession session = apacheSshdClient.connect(apacheSshdServer.getHost(), apacheSshdServer.getPort()).await().getSession();
                session.authPassword("", "").await().isSuccess();
                ChannelExec ls = session.createExecChannel(GIT_COMMAND);
                ls.setErr(new NullOutputStream());
                PipedInputStream fakeGitOutput = new PipedInputStream();
                ls.setOut(new PipedOutputStream(fakeGitOutput));
                ls.setIn(new ByteArrayInputStream(MockGitCommand.GIT_EOM_STRING.getBytes() ));

                ls.open().awaitUninterruptibly();
                String response = getUntilEom(fakeGitOutput);
                String revision = getRevision( response );

                ls.waitFor(ClientChannel.CLOSED, 0);
                ls.close(false);
                session.close(true);
                return revision;
            }
        };
    }

    private SshClient createApacheSshdClient()
    {
        SshClient client = SshClient.setUpDefaultClient();
        client.start();
        return client;
    }

    @AfterClass
    public void tearDown() throws InterruptedException
    {
        apacheSshdServer.stop(true);
    }

    @Test
    public void testJgitCdPerformance() throws Exception
    {
        testPerformance("JGit based", CD_JGIT);
    }

    @Test
    public void testNativeGitCdPerformance() throws Exception
    {
        testPerformance("Native Git based", CD_NATIVE_GIT);
    }

    @Test
    public void testNativeSshCdPerformance() throws Exception
    {
        testPerformance("Native SSH based", CD_NATIVE_SSH);
    }

    @Test
    public void testJavaSshPerformance() throws Exception
    {
        testPerformance("Apache SSHD based", CD_JAVA_SSH);
    }

    private void testPerformance(String testType, Callable<String> cdRoutine) throws Exception
    {
        Pair<String, Long> result = time(NUM_CD_RUNS, cdRoutine);

        Long time = result.second;
        log.warn(testType + ": " + TimeUnit.NANOSECONDS.toMillis(time) + "ms");
    }

    private String getServerUrl()
    {
        return "ssh://user@" + apacheSshdServer.getHost() + ":" + apacheSshdServer.getPort() + "/fakepath.git";
    }

    private Pair<String, Long> timeLoad(int times, final Callable<String> callable) throws Exception
    {
        String result = null;

        ExecutorService executorService = Executors.newFixedThreadPool(100);
        long start = System.nanoTime();
        final CountDownLatch latch = new CountDownLatch(times);
        for (int i=0; i<times; ++i)
        {
            executorService.execute(new Runnable()
            {
                @Override
                public void run()
                {
                    try
                    {
                        callable.call();
                        latch.countDown();
                    } catch (Exception e)
                    {
                        log.warn("", e);
                    }
                }
            });
        }
        latch.await();

        return Pair.make(result, System.nanoTime() - start);
    }

    private Pair<String, Long> time(int times, Callable<String> callable) throws Exception
    {
        long start = System.nanoTime();
        String result = null;

        for (int i=0; i<times; ++i)
        {
            result = callable.call();
        }

        return Pair.make(result, System.nanoTime() - start);
    }

    private SshServer createServer() throws IOException
    {
        SshServer sshServer = SshServer.setUpDefaultServer();
        sshServer.setPort(0);
        sshServer.setHost("127.0.0.1");

        SimpleGeneratorHostKeyProvider keyPairProvider = new SimpleGeneratorHostKeyProvider();
        keyPairProvider.loadKeys();
        sshServer.setKeyPairProvider(keyPairProvider);
        sshServer.setUserAuthFactories(Lists.newArrayList(new UserAuthPassword.Factory(), new UserAuthPublicKey.Factory(), new UserAuthNone.Factory()));
        sshServer.setPublickeyAuthenticator(new PublickeyAuthenticator()
        {
            @Override
            public boolean authenticate(String username, PublicKey key, ServerSession session)
            {
                return true;
            }
        });
        sshServer.setPasswordAuthenticator( new PasswordAuthenticator()
        {
            @Override
            public boolean authenticate(String username, String password, ServerSession session)
            {
                return true;
            }
        });

        sshServer.setCommandFactory(new CommandFactory()
        {
            @Override
            public Command createCommand(String command)
            {
                return new MockGitCommand(command);
            }
        });

        sshServer.setChannelFactories(Arrays.<NamedFactory<Channel>>asList(new ChannelSession.Factory()));
        sshServer.start();

        return sshServer;
    }


    private static String getUntilEom(InputStream inputStream) throws IOException
    {
        final StringBuilder sb = new StringBuilder();
        byte[] bytes = new byte[4096];
        while (true)
        {
            int read = inputStream.read(bytes);
            if (read==-1)
            {
                throw new IllegalArgumentException("End of input reached without EOM marker");
            }
            sb.append( new String(bytes, 0, read));
            if (sb.toString().endsWith("\n" + MockGitCommand.GIT_EOM_STRING))
            {
                break;
            }
        }
        return sb.toString();
    }

    private void writeEom(OutputStream outputStream) throws IOException
    {
        outputStream.write(MockGitCommand.GIT_EOM_STRING.getBytes());
    }

    private static String getRevision(String response)
    {
        return response.substring(0, response.indexOf(' '));
    }
}
