package com.atlassian.bamboo.plugins.git;

import com.atlassian.bamboo.plugins.ssh.ProxyRegistrationInfoImpl;
import com.google.common.base.Function;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;
import org.eclipse.jgit.transport.URIish;
import org.hamcrest.Matcher;
import org.jetbrains.annotations.NotNull;
import org.junit.Assert;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.URISyntaxException;

import static org.hamcrest.CoreMatchers.equalTo;
import static org.junit.Assert.assertThat;
import static org.junit.Assert.assertTrue;

public class UriUtilsTest
{
    static GitRepositoryAccessData proxyAccessData = new GitRepositoryAccessData();
    private final static ImmutableList<String> FILE_URLS = ImmutableList.of(
            "host", "host/path", "user@host/path", "user@host",
            "/host", "/host/path", "/user@host/path", "/user@host",
            "host/", "host/path/", "user@host/path/", "user@host/",
            "/host/", "/host/path/", "/user@host/path/", "/user@host/",
            "file://host", "file://host/path", "file://login@host");
    private final static ImmutableList<String> SSH_URLS = ImmutableList.of(
            "ssh://user:password@host/path",
            "ssh://user:password@host:22/path",
            "ssh://host", "ssh://host/path", "ssh://login@host");

    private final static ImmutableList<String> GIT_URLS = ImmutableList.copyOf(Iterables.transform(SSH_URLS, new Function<String, String>()
    {
        @Override
        public String apply(String input)
        {
            return input.replace("ssh://", "git://");
        }
    }));

    private final static ImmutableList<String> SCP_URLS = ImmutableList.of(
            "host:", "host:/", "host:22", "host:22/path", "host:/path", "user@host:22/path",
            "host:path", "host:path/path", "user@host:path", "user:password@host/path", "user:password@host:22/path"
    );

    private final static ImmutableList<String> UNKNOWN_URLS = ImmutableList.of(
            "/ssh://host", "/ssh://host/path", "/ssh://login@host"
    );

    @BeforeClass
    public static void setup()
    {
        proxyAccessData.setProxyRegistrationInfo(new ProxyRegistrationInfoImpl("proxyHost", 22, null, "proxyUserName"));
    }

    @Test
    public void uriCreation() throws URISyntaxException
    {
        String uri = "ssh://[user@]host.xz[:port]/path/to/repo.git/";
        createUris(uri, false);
        uri = "git://host.xz[:port]/path/to/repo.git/";
        createUris(uri, false);
        uri = "http[s]://host.xz[:port]/path/to/repo.git/";
        createUris(uri, false);
        uri = "ftp[s]://host.xz[:port]/path/to/repo.git/";
        createUris(uri, false);
        uri = "rsync://host.xz/path/to/repo.git/";
        createUris(uri, false);
        uri = "ssh://[user@]host.xz[:port]/~[user]/path/to/repo.git/";
        createUris(uri, false);
        uri = "git://host.xz[:port]/~[user]/path/to/repo.git/";
        createUris(uri, false);
        uri = "/path/to/repo.git/";
        createUris(uri, false);
        uri = "file:///path/to/repo.git/";
        createUris(uri, false);


        uri = "[user@]host.xz:path/to/repo.git/";
        createUris(uri, true);

        uri = "[user@]host.xz:/~[user]/path/to/repo.git/";
        createUris(uri, false);
    }

    private void createUris(String uriStr, boolean isRelative) throws URISyntaxException
    {
        String minimalUriStr = uriStr.replaceAll("\\[[:\\w@]*\\]", "");
        testUri(minimalUriStr, isRelative);

        String fullUriStr = uriStr.replaceAll("[\\[\\]]", "");
        testUri(fullUriStr, isRelative);
    }

    private void testUri(String minimalUriStr, boolean isRelative) throws URISyntaxException
    {
        ScpAwareUri uri = ScpAwareUri.create(minimalUriStr);
        Assert.assertTrue(uri.getRawPath() +  " should contain path", uri.getRawPath().contains("path"));
        Assert.assertEquals(uri.isRelativePath(), isRelative);
        URI minimalUriViaProxy = UriUtils.getUriViaProxy(proxyAccessData, uri);
    }

    @Test
    public void recognisesScpLikeUris()
    {
        assertTrue(none(FILE_URLS, hasScpSyntax()));

        assertTrue(none(SSH_URLS, hasScpSyntax()));

        assertTrue(Iterables.all(SCP_URLS, hasScpSyntax()));
    }

    private <T> boolean none(@NotNull Iterable<T> iterable, @NotNull Predicate<? super T> predicate)
    {
        return !Iterables.any(iterable, predicate);
    }

    private Predicate<? super String> hasScpSyntax()
    {
        return new Predicate<String>()
        {
            @Override
            public boolean apply(String url)
            {
                return UriUtils.hasScpSyntax(url);
            }
        };
    }

    @Test
    public void recognisesSshTransport()
    {
        assertTrue(none(FILE_URLS, requiresSshTransport()));
        assertTrue(none(UNKNOWN_URLS, requiresSshTransport()));

        assertTrue(Iterables.all(SSH_URLS, requiresSshTransport()));
        assertTrue(Iterables.all(SCP_URLS, requiresSshTransport()));
        assertTrue(none(GIT_URLS, requiresSshTransport()));
    }

    @Test
    public void normalisesUrlProperly() throws URISyntaxException
    {
        URIish repo = new URIish("http://wrong1:wrong2@host/path");
        URIish normalised = UriUtils.normaliseRepositoryLocation("user", "password", repo);
        assertThatUrl(normalised, equalTo("http://user:password@host/path"));

        repo = new URIish("http://wrong1@host/path");
        normalised = UriUtils.normaliseRepositoryLocation("user", null, repo);
        assertThatUrl(normalised, equalTo("http://user:" + UriUtils.FAKE_PASSWORD + "@host/path"));

        repo = new URIish("http://host/path");
        normalised = UriUtils.normaliseRepositoryLocation("user", null, repo);
        assertThatUrl(normalised, equalTo("http://user:" + UriUtils.FAKE_PASSWORD + "@host/path"));

        repo = new URIish("ssh://okish@host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, "password", repo);
        assertThatUrl(normalised, equalTo("ssh://okish@host/path"));

        repo = new URIish("ssh://host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, "password", repo);
        assertThatUrl(normalised, equalTo("ssh://host/path"));
    }

    @Test
    public void properlyHandlesSshWithPasssword() throws URISyntaxException
    {
        final String password = "this_password_would_be_ignored_by_ssh_transport";
        URIish repo = new URIish("ssh://wrong1:" + password + "@host/path");
        URIish normalised = UriUtils.normaliseRepositoryLocation("user", "password", repo);
        assertThatUrl(normalised, equalTo("ssh://user@host/path"));

        repo = new URIish("ssh://okish:"  + password + "@host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, "password", repo);
        assertThatUrl(normalised, equalTo("ssh://okish@host/path"));
    }

    @Test
    public void properlyNormalisesUserName() throws URISyntaxException
    {
        URIish repo = new URIish("ssh://okish@host/path");
        URIish normalised = UriUtils.normaliseRepositoryLocation(null, "password", repo);
        assertThatUrl(normalised, equalTo("ssh://okish@host/path"));

        repo = new URIish("ssh://okish@host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, null, repo);
        assertThatUrl(normalised, equalTo("ssh://okish@host/path"));

        repo = new URIish("http://okish@host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, null, repo);
        assertThatUrl(normalised, equalTo("http://okish:" + UriUtils.FAKE_PASSWORD + "@host/path"));

        repo = new URIish("http://host/path");
        normalised = UriUtils.normaliseRepositoryLocation(null, null, repo);
        assertThatUrl(normalised, equalTo("http://" + UriUtils.FAKE_USER + ":" + UriUtils.FAKE_PASSWORD + "@host/path"));
    }

    @Test
    public void properlyUrlEncodesUsernameAndPassword() throws URISyntaxException
    {
        URIish repo = new URIish("ssh://host/path");
        URIish normalised = UriUtils.normaliseRepositoryLocation("@", "@", repo);
        assertThatUrl(normalised, equalTo("ssh://%40@host/path"));

        repo = new URIish("http://host/path");
        normalised = UriUtils.normaliseRepositoryLocation("@", "@", repo);
        assertThatUrl(normalised, equalTo("http://%40:%40@host/path"));
    }

    @Test
    public void handlesLocalUrls() throws URISyntaxException
    {
        final String localRepo1 = "/okish@host/path";
        URIish repo = new URIish(localRepo1);
        URIish normalised = UriUtils.normaliseRepositoryLocation("user", "password", repo);
        assertThatUrl(normalised, equalTo(localRepo1));

        final String localRepo2 = "file://okish@host/path";
        repo = new URIish(localRepo2);
        normalised = UriUtils.normaliseRepositoryLocation("user", "password", repo);
        assertThatUrl(normalised, equalTo(localRepo2));
    }

    private void assertThatUrl(final URIish normalised, final Matcher<String> matcher)
    {
        assertThat(normalised.toPrivateString(), matcher);
    }

    private Predicate<? super String> requiresSshTransport()
    {
        return new Predicate<String>()
        {
            @Override
            public boolean apply(String url)
            {
                return UriUtils.requiresSshTransport(ScpAwareUri.create(url));
            }
        };
    }
}
