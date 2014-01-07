package com.atlassian.bamboo.plugins.git.rest.entity;

import com.atlassian.bamboo.plugins.git.rest.commons.RestConstants;
import org.apache.log4j.Logger;

import javax.xml.bind.annotation.XmlAccessorType;
import javax.xml.bind.annotation.XmlAttribute;
import javax.xml.bind.annotation.XmlRootElement;

import static javax.xml.bind.annotation.XmlAccessType.FIELD;

@XmlRootElement(name = RestConstants.REQUEST)
@XmlAccessorType(FIELD)
public class RestRequest
{
    @SuppressWarnings("UnusedDeclaration")
    private static final Logger log = Logger.getLogger(RestRequest.class);
    // ------------------------------------------------------------------------------------------------------- Constants
    // ------------------------------------------------------------------------------------------------- Type Properties
    @XmlAttribute
    private String username;

    @XmlAttribute
    private String password;

    @XmlAttribute
    private String repository;

    @XmlAttribute
    private long repositoryId;

    @XmlAttribute
    private String query;

    // ---------------------------------------------------------------------------------------------------- Dependencies
    // ---------------------------------------------------------------------------------------------------- Constructors
    public RestRequest()
    {
    }
    // ----------------------------------------------------------------------------------------------- Interface Methods
    // -------------------------------------------------------------------------------------------------- Action Methods
    // -------------------------------------------------------------------------------------------------- Public Methods
    // -------------------------------------------------------------------------------------- Basic Accessors / Mutators
    public String getUsername()
    {
        return username;
    }

    public void setUsername(final String username)
    {
        this.username = username;
    }

    public String getPassword()
    {
        return password;
    }

    public void setPassword(final String password)
    {
        this.password = password;
    }

    public String getRepository()
    {
        return repository;
    }

    public void setRepository(final String repository)
    {
        this.repository = repository;
    }

    public long getRepositoryId()
    {
        return repositoryId;
    }

    public void setRepositoryId(final long repositoryId)
    {
        this.repositoryId = repositoryId;
    }

    public String getQuery()
    {
        return query;
    }

    public void setQuery(final String query)
    {
        this.query = query;
    }
}
