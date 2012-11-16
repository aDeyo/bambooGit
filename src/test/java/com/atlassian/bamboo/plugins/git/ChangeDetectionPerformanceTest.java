package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.utils.Pair;
import org.apache.log4j.Logger;
import org.apache.sshd.ClientSession;
import org.apache.sshd.SshClient;
import org.apache.sshd.SshServer;
import org.apache.sshd.client.channel.ChannelExec;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.common.Channel;
import org.apache.sshd.common.NamedFactory;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.CommandFactory;
import org.apache.sshd.server.PasswordAuthenticator;
import org.apache.sshd.server.PublickeyAuthenticator;
import org.apache.sshd.server.UserAuth;
import org.apache.sshd.server.auth.UserAuthPassword;
import org.apache.sshd.server.auth.UserAuthPublicKey;
import org.apache.sshd.server.channel.ChannelSession;
import org.apache.sshd.server.keyprovider.SimpleGeneratorHostKeyProvider;
import org.apache.sshd.server.session.ServerSession;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.StringBufferInputStream;
import java.security.PublicKey;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

public class ChangeDetectionPerformanceTest extends GitAbstractTest
{
    private static final Logger log = Logger.getLogger(ChangeDetectionPerformanceTest.class);
    private static final int NUM_CD_RUNS = 10;
    private static final String GIT_COMMAND = "git-upload-pack '/pbruski/bamboo-git.git'";
    private SshServer server;
    private Callable<String> CD_JGIT;
    private Callable<String> CD_NATIVE_GIT;
    private Callable<String> CD_NATIVE_SSH;
    private Callable<String> CD_JAVA_SSH;

    @BeforeClass
    public void setUp() throws IOException, RepositoryException
    {
        server = createServer();
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
                    "-p" + server.getPort(),
                    server.getHost(),
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
                SshClient sshClient = SshClient.setUpDefaultClient();
                sshClient.start();
                ConnectFuture connect = sshClient.connect(server.getHost(), server.getPort());
                ClientSession session = connect.awaitUninterruptibly().getSession();
                ChannelExec ls = session.createExecChannel(GIT_COMMAND);
                ByteArrayOutputStream err = new ByteArrayOutputStream(65535);
                ByteArrayOutputStream out = new ByteArrayOutputStream(65535);
                ls.setErr(err);
                ls.setOut(out);
                ls.setIn(new StringBufferInputStream("0000"));
                ls.open();


                return getRevision(out.toString());
            }
        };
    }

    @AfterClass
    public void tearDown() throws InterruptedException
    {
        server.stop(true);
    }

    @Test
    public void testJgitCdPerformance() throws Exception
    {
        testPerformance(CD_JGIT);
    }

    @Test
    public void testNativeGitCdPerformance() throws Exception
    {
        testPerformance(CD_NATIVE_GIT);
    }

    @Test
    public void testNativeSshCdPerformance() throws Exception
    {
        testPerformance(CD_NATIVE_SSH);
    }

//    @Test
//    public void testJavaSshPerformance() throws Exception
//    {
//        testPerformance(CD_JAVA_SSH);
//    }

    private void testPerformance(Callable<String> cdRoutine) throws Exception
    {
        Pair<String, Long> result = time(NUM_CD_RUNS, cdRoutine);

        Long time = result.second;
        String revision = result.first;
        log.info("revision " + revision + " " + TimeUnit.NANOSECONDS.toMillis(time) + "ms");
    }

    private String getServerUrl()
    {
        return "ssh://user@" + server.getHost() + ":" + server.getPort() + "/fakepath.git";
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
        sshServer.setUserAuthFactories(Arrays.<NamedFactory<UserAuth>>asList(new UserAuthPassword.Factory(), new UserAuthPublicKey.Factory()));
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
        return response.replace(" .*", "");
    }
}
