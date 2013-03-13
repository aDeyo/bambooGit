package com.atlassian.bamboo.plugins.git;


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
    static final String SSH_SCHEME = "ssh";
    static final String SCHEME_DELIMITER = "://";

    public static final String SSH_PREFIX = UriUtils.SSH_SCHEME + UriUtils.SCHEME_DELIMITER;

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

    public static URIish normaliseRepositoryLocation(@Nullable String userName, @Nullable String password, @NotNull URIish normalised)
    {
        if (StringUtils.isNotBlank(userName))
        {
            normalised = normalised.setUser(userName);
        }
        else
        {
            userName = normalised.getUser();
            if (StringUtils.isEmpty(userName))
            {
                return normalised;
            }
        }

        final String scheme = normalised.getScheme();
        final boolean isHttpBased = scheme.equals(UriUtils.HTTP_SCHEME) || scheme.equals(UriUtils.HTTPS_SCHEME);

        if (isHttpBased)
        {
            if (StringUtils.isBlank(password))
            {
                password = normalised.getPass();
            }
            return normalised.setPass(StringUtils.defaultIfBlank(password, "none")); //otherwise we'd get a password prompt
        }

        return normalised.setPass(null);
    }
}
