package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.repository.RepositoryException;
import com.atlassian.bamboo.util.PasswordMaskingUtils;
import org.jetbrains.annotations.Nullable;

/**
 * This class types git repository errors in running commands.
 *
 */
class GitCommandException extends RepositoryException {

    private final String stdout;
    private final String stderr;
    private final String passwordToObfuscate;

    /**
     * Create a command exception containing the message and root cause and the stderr
     *
     * @param message The error message
     * @param cause   The root cause
     * @param stderr  Command standard error output
     * @param passwordToObfuscate an user password that might be visible in the exception message - it will be replaced with asterisk when rendering exception in UI
     */
    public GitCommandException(String message, @Nullable Throwable cause, String stdout, String stderr, @Nullable String passwordToObfuscate) {
        super(message, cause);
        this.stdout = stdout;
        this.stderr = stderr;
        this.passwordToObfuscate = passwordToObfuscate;
    }

    @Override
    public String getStdout()
    {
        return stdout;
    }

    @Override
    public String getStderr()
    {
        return stderr;
    }

    @Override
    public String getMessage()
    {
        final StringBuilder sb = new StringBuilder(super.getMessage());
        sb
                .append(", stderr:\n")
                .append(stderr);
        if (!stdout.equals(stderr))
        {
            sb
                    .append("\nstdout:\n")
                    .append(stdout);
        }

        return PasswordMaskingUtils.mask(sb.toString(), passwordToObfuscate);
    }
}
