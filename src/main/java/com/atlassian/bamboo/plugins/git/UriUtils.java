package com.atlassian.bamboo.plugins.git;


import com.atlassian.bamboo.util.HtmlUtils;
import com.google.common.annotations.VisibleForTesting;
import org.apache.commons.lang.StringUtils;
import org.eclipse.jgit.transport.URIish;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.net.URI;
import java.net.URISyntaxException;

public class UriUtils
{
    public static final String HTTP_SCHEME = "http";
    public static final String HTTPS_SCHEME = "https";
    public static final String FTP_SCHEME = "ftp";
    public static final String FTPS_SCHEME = "ftps";
    public static final String FILE_SCHEME = "file";
    static final String SSH_SCHEME = "ssh";
    static final String SCHEME_DELIMITER = "://";

    public static final String SSH_PREFIX = UriUtils.SSH_SCHEME + UriUtils.SCHEME_DELIMITER;

    @VisibleForTesting
    static final String FAKE_USER = "nouser";

    @VisibleForTesting
    static final String FAKE_PASSWORD = "nopassword";

    private UriUtils()
    {
    }

    @Nullable
    public static String extractUsername(final String repositoryUrl) throws URISyntaxException
    {
        URIish uri = new URIish(repositoryUrl);

        final String auth = uri.getUser();
        if (auth == null)
        {
            return null;
        }
        return auth;
    }

    public static boolean requiresSshTransport(@NotNull ScpAwareUri repositoryUri)
    {
        String scheme = repositoryUri.getScheme();

        return scheme!=null && scheme.equals(SSH_SCHEME);
    }

    public static boolean requiresSshTransport(@NotNull final String repositoryUrl)
    {
        return repositoryUrl.startsWith(SSH_PREFIX) || hasScpSyntax(repositoryUrl);
    }

    public static boolean hasScpSyntax(@NotNull String url)
    {
        if (hasScheme(url))
        {
            return false; //cannot use SCP syntax when a scheme is defined
        }

        int pathDefinitelyStartsHere = url.indexOf("/");
        if (pathDefinitelyStartsHere!=-1)
        {
            url = url.substring(0, pathDefinitelyStartsHere); //don't care about anything after the first /
        }

        return url.contains(":");
    }

    private static boolean hasScheme(@NotNull String url)
    {
        int scheme = url.indexOf(SCHEME_DELIMITER);
        return scheme != -1;
    }

    public static URI getUriViaProxy(GitRepositoryAccessData proxyAccessData, ScpAwareUri repositoryUri) throws URISyntaxException
    {
        return new URI(repositoryUri.getScheme(),
                proxyAccessData.getProxyRegistrationInfo().getProxyUserName(),
                proxyAccessData.getProxyRegistrationInfo().getProxyHost(),
                proxyAccessData.getProxyRegistrationInfo().getProxyPort(),
                repositoryUri.getAbsolutePath(),
                repositoryUri.getRawQuery(),
                repositoryUri.getRawFragment());
    }

    /**
     * This method adds/removes username and password from URL to avoid interactive password prompts from the git command line client
     */
    public static URIish normaliseRepositoryLocation(@Nullable final String userName, @Nullable final String password, @NotNull URIish normalised)
    {
        final String scheme = normalised.getScheme();

        if (isLocalUri(scheme))
        {
            return normalised;
        }

        final boolean isHttpBased = scheme.equals(UriUtils.HTTP_SCHEME) || scheme.equals(UriUtils.HTTPS_SCHEME);
        final boolean isFtpBased = scheme.equals(UriUtils.FTP_SCHEME) || scheme.equals(UriUtils.FTPS_SCHEME);

        final boolean acceptsPasswordInUri = isHttpBased || isFtpBased;

        if (StringUtils.isNotBlank(userName))
        {
            normalised = setUser(normalised, userName);
        }
        else
        {
            final String urlUserName = normalised.getUser();
            if (StringUtils.isEmpty(urlUserName))
            {
                if (!acceptsPasswordInUri)
                {
                    return normalised;
                }
                else
                {
                    normalised = setUser(normalised, FAKE_USER);
                }
            }
        }

        if (!acceptsPasswordInUri)
        {
            //no further normalisation needs to be performed
            return setPassword(normalised, null);
        }

        // we need to have a password too
        if (StringUtils.isNotBlank(password))
        {
            return setPassword(normalised, password);
        }
        else
        {
            final String urlPassword = normalised.getPass();

            return StringUtils.isNotEmpty(urlPassword) ? normalised : setPassword(normalised, FAKE_PASSWORD);
        }
    }

    private static URIish setPassword(@NotNull final URIish normalised, @Nullable final String password)
    {
        return password == null ? normalised.setPass(null) : normalised.setPass(HtmlUtils.encodeUrl(password));
    }

    private static URIish setUser(@NotNull final URIish normalised, @Nullable final String userName)
    {
        return userName == null ? normalised.setUser(null) : normalised.setUser(HtmlUtils.encodeUrl(userName));
    }

    private static boolean isLocalUri(@Nullable final String scheme)
    {
        return scheme==null || scheme.equals(FILE_SCHEME);
    }
}
