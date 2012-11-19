package com.atlassian.bamboo.plugins.git;

import org.apache.commons.io.IOUtils;
import org.apache.sshd.server.Command;
import org.apache.sshd.server.Environment;
import org.apache.sshd.server.ExitCallback;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class MockGitCommand implements Command
{
    static final String GIT_EOM_STRING = "0000";
    private OutputStream outputStream;
    private ExitCallback callback;
    private OutputStream err;
    private InputStream in;
    private final String command;

    public MockGitCommand(String command)
    {
        this.command = command;
    }

    @Override
    public void setInputStream(final InputStream in)
    {
        this.in = in;
        new Thread()
        {
            @Override
            public void run()
            {
                try
                {
                    byte [] bytes = new byte[4096];
                    int i;
                    while ((i=in.read(bytes))!=-1)
                    {
                        String s = new String(bytes, 0, i);
                        if (s.equals(GIT_EOM_STRING))
                        {
                            callback.onExit(0);
                            break;
                        }
                    }
                } catch (IOException ignored)
                {
                    callback.onExit(1);
                }
                IOUtils.closeQuietly(outputStream);
                IOUtils.closeQuietly(err);
            }
        }.start();
    }

    @Override
    public void setOutputStream(OutputStream out)
    {
        outputStream = out;
    }

    @Override
    public void setErrorStream(OutputStream err)
    {
        this.err = err;
    }

    @Override
    public void setExitCallback(ExitCallback callback)
    {
        this.callback = callback;
    }

    @Override
    public void start(Environment env) throws IOException
    {
        String str = "009b63170cebd46281904c800c12bded0e22c6b56770 HEAD\0" +
                "multi_ack thin-pack side-band side-band-64k ofs-delta shallow no-progress include-tag multi_ack_detailed\n" +
                "003f63170cebd46281904c800c12bded0e22c6b56770 refs/heads/master\n" +
                "0000";
        outputStream.write(str.getBytes());
        outputStream.flush();
    }

    @Override
    public void destroy()
    {
        callback.onExit(0);
    }
}
