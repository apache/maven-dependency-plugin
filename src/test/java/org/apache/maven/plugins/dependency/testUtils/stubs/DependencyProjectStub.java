package org.apache.maven.plugins.dependency.testUtils.stubs;

/* 
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.    
 */

import java.io.File;
import java.io.IOException;
import java.io.Writer;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Build;
import org.apache.maven.model.CiManagement;
import org.apache.maven.model.Contributor;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.model.Developer;
import org.apache.maven.model.DistributionManagement;
import org.apache.maven.model.Extension;
import org.apache.maven.model.IssueManagement;
import org.apache.maven.model.License;
import org.apache.maven.model.MailingList;
import org.apache.maven.model.Model;
import org.apache.maven.model.Organization;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginManagement;
import org.apache.maven.model.Prerequisites;
import org.apache.maven.model.Profile;
import org.apache.maven.model.ReportPlugin;
import org.apache.maven.model.Reporting;
import org.apache.maven.model.Repository;
import org.apache.maven.model.Resource;
import org.apache.maven.model.Scm;
import org.apache.maven.plugin.testing.stubs.DefaultArtifactHandlerStub;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.PlexusTestCase;
import org.codehaus.plexus.util.xml.Xpp3Dom;

/**
 * very simple stub of maven project, going to take a lot of work to make it useful as a stub though
 */
public class DependencyProjectStub
    extends MavenProject
{
    private String groupId;

    private String artifactId;

    private String name;

    private Model model;

    private MavenProject parent;

    private List<Dependency> dependencies;

    private File file;

    private List<MavenProject> collectedProjects;

    private List<Artifact> attachedArtifacts;

    private List<String> compileSourceRoots;

    private List<String> testCompileSourceRoots;

    private List<String> scriptSourceRoots;

    private List<ArtifactRepository> pluginArtifactRepositories;

    private List<Profile> activeProfiles;

    private Set<Artifact> dependencyArtifacts;

    private DependencyManagement dependencyManagement;

    private Artifact artifact;

    private Model originalModel;

    private boolean executionRoot;

    private List<Artifact> compileArtifacts;

    private List<Dependency> compileDependencies;

    private List<Dependency> systemDependencies;

    private List<String> testClasspathElements;

    private List<Dependency> testDependencies;

    private List<String> systemClasspathElements;

    private List<Artifact> systemArtifacts;

    private List<Artifact> testArtifacts;

    private List<Artifact> runtimeArtifacts;

    private List<Dependency> runtimeDependencies;

    private List<String> runtimeClasspathElements;

    private String modelVersion;

    private String packaging;

    private String inceptionYear;

    private String url;

    private String description;

    private String version;

    private String defaultGoal;

    private Set<Artifact> artifacts;

    private Properties properties;

    public DependencyProjectStub()
    {
        super( (Model) null );
    }

    // kinda dangerous...
    public DependencyProjectStub( Model model )
    {
        // super(model);
        super( (Model) null );
    }

    // kinda dangerous...
    public DependencyProjectStub( MavenProject project )
    {
        // super(project);
        super( (Model) null );
    }

    public String getModulePathAdjustment( MavenProject mavenProject )
        throws IOException
    {
        return "";
    }

    public Artifact getArtifact()
    {
        if ( artifact == null )
        {
            ArtifactHandler ah = new DefaultArtifactHandlerStub( "jar", null );

            VersionRange vr = VersionRange.createFromVersion( "1.0" );
            Artifact art =
                new DefaultArtifact( "group", "artifact", vr, Artifact.SCOPE_COMPILE, "jar", null, ah, false );
            setArtifact( art );
        }
        return artifact;
    }

    public void setArtifact( Artifact artifact )
    {
        this.artifact = artifact;
    }

    public Model getModel()
    {
        return model;
    }

    public MavenProject getParent()
    {
        return parent;
    }

    public void setParent( MavenProject mavenProject )
    {
        this.parent = mavenProject;
    }

    public void setRemoteArtifactRepositories( List<ArtifactRepository> list )
    {

    }

    public List<ArtifactRepository> getRemoteArtifactRepositories()
    {
        return Collections.emptyList();
    }

    public boolean hasParent()
    {
        return ( parent != null );
    }

    public File getFile()
    {
        return file;
    }

    public void setFile( File file )
    {
        this.file = file;
    }

    public File getBasedir()
    {
        return new File( PlexusTestCase.getBasedir() );
    }

    public void setDependencies( List<Dependency> list )
    {
        dependencies = list;
    }

    public List<Dependency> getDependencies()
    {
        if ( dependencies == null )
        {
            dependencies = Collections.emptyList();
        }
        return dependencies;
    }

    public void setDependencyManagement( DependencyManagement depMgt )
    {
        this.dependencyManagement = depMgt;
    }

    public DependencyManagement getDependencyManagement()
    {
        if ( dependencyManagement == null )
        {
            dependencyManagement = new DependencyManagement();
        }

        return dependencyManagement;
    }

    public void addCompileSourceRoot( String string )
    {
        if ( compileSourceRoots == null )
        {
            compileSourceRoots = Collections.singletonList( string );
        }
        else
        {
            compileSourceRoots.add( string );
        }
    }

    public void addScriptSourceRoot( String string )
    {
        if ( scriptSourceRoots == null )
        {
            scriptSourceRoots = Collections.singletonList( string );
        }
        else
        {
            scriptSourceRoots.add( string );
        }
    }

    public void addTestCompileSourceRoot( String string )
    {
        if ( testCompileSourceRoots == null )
        {
            testCompileSourceRoots = Collections.singletonList( string );
        }
        else
        {
            testCompileSourceRoots.add( string );
        }
    }

    public List<String> getCompileSourceRoots()
    {
        return compileSourceRoots;
    }

    public List<String> getScriptSourceRoots()
    {
        return scriptSourceRoots;
    }

    public List<String> getTestCompileSourceRoots()
    {
        return testCompileSourceRoots;
    }

    public List<String> getCompileClasspathElements()
        throws DependencyResolutionRequiredException
    {
        return compileSourceRoots;
    }

    public void setCompileArtifacts( List<Artifact> compileArtifacts )
    {
        this.compileArtifacts = compileArtifacts;
    }

    public List<Artifact> getCompileArtifacts()
    {
        return compileArtifacts;
    }

    public List<Dependency> getCompileDependencies()
    {
        return compileDependencies;
    }

    public List<String> getTestClasspathElements()
        throws DependencyResolutionRequiredException
    {
        return testClasspathElements;
    }

    public List<Artifact> getTestArtifacts()
    {
        return testArtifacts;
    }

    public List<Dependency> getTestDependencies()
    {
        return testDependencies;
    }

    public List<String> getRuntimeClasspathElements()
        throws DependencyResolutionRequiredException
    {
        return runtimeClasspathElements;
    }

    public List<Artifact> getRuntimeArtifacts()
    {
        return runtimeArtifacts;
    }

    public List<Dependency> getRuntimeDependencies()
    {
        return runtimeDependencies;
    }

    public List<String> getSystemClasspathElements()
        throws DependencyResolutionRequiredException
    {
        return systemClasspathElements;
    }

    public List<Artifact> getSystemArtifacts()
    {
        return systemArtifacts;
    }

    public void setRuntimeClasspathElements( List<String> runtimeClasspathElements )
    {
        this.runtimeClasspathElements = runtimeClasspathElements;
    }

    public void setAttachedArtifacts( List<Artifact> attachedArtifacts )
    {
        this.attachedArtifacts = attachedArtifacts;
    }

    public void setCompileSourceRoots( List<String> compileSourceRoots )
    {
        this.compileSourceRoots = compileSourceRoots;
    }

    public void setTestCompileSourceRoots( List<String> testCompileSourceRoots )
    {
        this.testCompileSourceRoots = testCompileSourceRoots;
    }

    public void setScriptSourceRoots( List<String> scriptSourceRoots )
    {
        this.scriptSourceRoots = scriptSourceRoots;
    }

    public void setCompileDependencies( List<Dependency> compileDependencies )
    {
        this.compileDependencies = compileDependencies;
    }

    public void setSystemDependencies( List<Dependency> systemDependencies )
    {
        this.systemDependencies = systemDependencies;
    }

    public void setTestClasspathElements( List<String> testClasspathElements )
    {
        this.testClasspathElements = testClasspathElements;
    }

    public void setTestDependencies( List<Dependency> testDependencies )
    {
        this.testDependencies = testDependencies;
    }

    public void setSystemClasspathElements( List<String> systemClasspathElements )
    {
        this.systemClasspathElements = systemClasspathElements;
    }

    public void setSystemArtifacts( List<Artifact> systemArtifacts )
    {
        this.systemArtifacts = systemArtifacts;
    }

    public void setTestArtifacts( List<Artifact> testArtifacts )
    {
        this.testArtifacts = testArtifacts;
    }

    public void setRuntimeArtifacts( List<Artifact> runtimeArtifacts )
    {
        this.runtimeArtifacts = runtimeArtifacts;
    }

    public void setRuntimeDependencies( List<Dependency> runtimeDependencies )
    {
        this.runtimeDependencies = runtimeDependencies;
    }

    public void setModel( Model model )
    {
        this.model = model;
    }

    public List<Dependency> getSystemDependencies()
    {
        return systemDependencies;
    }

    public void setModelVersion( String string )
    {
        this.modelVersion = string;
    }

    public String getModelVersion()
    {
        return modelVersion;
    }

    public String getId()
    {
        return "";
    }

    public void setGroupId( String string )
    {
        this.groupId = string;
    }

    public String getGroupId()
    {
        return groupId;
    }

    public void setArtifactId( String string )
    {
        this.artifactId = string;
    }

    public String getArtifactId()
    {
        return artifactId;
    }

    public void setName( String string )
    {
        this.name = string;
    }

    public String getName()
    {
        return name;
    }

    public void setVersion( String string )
    {
        this.version = string;
    }

    public String getVersion()
    {
        return version;
    }

    public String getPackaging()
    {
        return packaging;
    }

    public void setPackaging( String string )
    {
        this.packaging = string;
    }

    public void setInceptionYear( String string )
    {
        this.inceptionYear = string;
    }

    public String getInceptionYear()
    {
        return inceptionYear;
    }

    public void setUrl( String string )
    {
        this.url = string;
    }

    public String getUrl()
    {
        return url;
    }

    public Prerequisites getPrerequisites()
    {
        return null;
    }

    public void setIssueManagement( IssueManagement issueManagement )
    {

    }

    public CiManagement getCiManagement()
    {
        return null;
    }

    public void setCiManagement( CiManagement ciManagement )
    {

    }

    public IssueManagement getIssueManagement()
    {
        return null;
    }

    public void setDistributionManagement( DistributionManagement distributionManagement )
    {

    }

    public DistributionManagement getDistributionManagement()
    {
        return null;
    }

    public void setDescription( String string )
    {
        this.description = string;
    }

    public String getDescription()
    {
        return description;
    }

    public void setOrganization( Organization organization )
    {

    }

    public Organization getOrganization()
    {
        return null;
    }

    public void setScm( Scm scm )
    {

    }

    public Scm getScm()
    {
        return null;
    }

    @Override
    public void setMailingLists( List<MailingList> list )
    {

    }

    public List<MailingList> getMailingLists()
    {
        return Collections.emptyList();
    }

    public void addMailingList( MailingList mailingList )
    {

    }

    @Override
    public void setDevelopers( List<Developer> list )
    {

    }

    public List<Developer> getDevelopers()
    {
        return Collections.emptyList();
    }

    public void addDeveloper( Developer developer )
    {

    }

    public void setContributors( List<Contributor> list )
    {

    }

    public List<Contributor> getContributors()
    {
        return Collections.emptyList();
    }

    public void addContributor( Contributor contributor )
    {

    }

    public void setBuild( Build build )
    {

    }

    public Build getBuild()
    {
        return null;
    }

    public List<Resource> getResources()
    {
        return Collections.emptyList();
    }

    public List<Resource> getTestResources()
    {
        return Collections.emptyList();
    }

    public void addResource( Resource resource )
    {

    }

    public void addTestResource( Resource resource )
    {

    }

    public void setReporting( Reporting reporting )
    {

    }

    public Reporting getReporting()
    {
        return null;
    }

    public void setLicenses( List<License> list )
    {

    }

    public List<License> getLicenses()
    {
        return Collections.emptyList();
    }

    public void addLicense( License license )
    {

    }

    public void setArtifacts( Set<Artifact> set )
    {
        this.artifacts = set;
    }

    public Set<Artifact> getArtifacts()
    {
        if ( artifacts == null )
        {
            return Collections.emptySet();
        }
        else
        {
            return artifacts;
        }
    }

    public Map<String, Artifact> getArtifactMap()
    {
        return Collections.emptyMap();
    }

    public void setPluginArtifacts( Set<Artifact> set )
    {

    }

    public Set<Artifact> getPluginArtifacts()
    {
        return Collections.emptySet();
    }

    public Map<String, Artifact> getPluginArtifactMap()
    {
        return Collections.emptyMap();
    }

    public void setReportArtifacts( Set<Artifact> set )
    {

    }

    public Set<Artifact> getReportArtifacts()
    {
        return Collections.emptySet();
    }

    public Map<String, Artifact> getReportArtifactMap()
    {
        return Collections.emptyMap();
    }

    public void setExtensionArtifacts( Set<Artifact> set )
    {

    }

    public Set<Artifact> getExtensionArtifacts()
    {
        return Collections.emptySet();
    }

    public Map<String, Artifact> getExtensionArtifactMap()
    {
        return Collections.emptyMap();
    }

    public void setParentArtifact( Artifact artifact )
    {

    }

    public Artifact getParentArtifact()
    {
        return null;
    }

    public List<Repository> getRepositories()
    {
        return Collections.emptyList();
    }

    public List<ReportPlugin> getReportPlugins()
    {
        return Collections.emptyList();
    }

    public List<Plugin> getBuildPlugins()
    {
        return Collections.emptyList();
    }

    public List<String> getModules()
    {
        return Collections.singletonList( "" );
    }

    public PluginManagement getPluginManagement()
    {
        return null;
    }

    public void addPlugin( Plugin plugin )
    {

    }

    public void injectPluginManagementInfo( Plugin plugin )
    {

    }

    public List<MavenProject> getCollectedProjects()
    {
        return collectedProjects;
    }

    public void setCollectedProjects( List<MavenProject> list )
    {
        this.collectedProjects = list;
    }

    public void setPluginArtifactRepositories( List<ArtifactRepository> list )
    {
        this.pluginArtifactRepositories = list;
    }

    public List<ArtifactRepository> getPluginArtifactRepositories()
    {
        return pluginArtifactRepositories;
    }

    public ArtifactRepository getDistributionManagementArtifactRepository()
    {
        return null;
    }

    public List<Repository> getPluginRepositories()
    {
        return Collections.emptyList();
    }

    public void setActiveProfiles( List<Profile> list )
    {
        activeProfiles = list;
    }

    public List<Profile> getActiveProfiles()
    {
        return activeProfiles;
    }

    public void addAttachedArtifact( Artifact theArtifact )
    {
        if ( attachedArtifacts == null )
        {
            this.attachedArtifacts = Collections.singletonList( theArtifact );
        }
        else
        {
            attachedArtifacts.add( theArtifact );
        }
    }

    public List<Artifact> getAttachedArtifacts()
    {
        return attachedArtifacts;
    }

    public Xpp3Dom getGoalConfiguration( String string, String string1, String string2, String string3 )
    {
        return null;
    }

    public Xpp3Dom getReportConfiguration( String string, String string1, String string2 )
    {
        return null;
    }

    public MavenProject getExecutionProject()
    {
        return null;
    }

    public void setExecutionProject( MavenProject mavenProject )
    {

    }

    public void writeModel( Writer writer )
        throws IOException
    {

    }

    public void writeOriginalModel( Writer writer )
        throws IOException
    {

    }

    public Set<Artifact> getDependencyArtifacts()
    {
        return dependencyArtifacts;
    }

    public void setDependencyArtifacts( Set<Artifact> set )
    {
        this.dependencyArtifacts = set;
    }

    public void setReleaseArtifactRepository( ArtifactRepository artifactRepository )
    {
        // this.releaseArtifactRepository = artifactRepository;
    }

    public void setSnapshotArtifactRepository( ArtifactRepository artifactRepository )
    {
        // this.snapshotArtifactRepository = artifactRepository;
    }

    public void setOriginalModel( Model model )
    {
        this.originalModel = model;
    }

    public Model getOriginalModel()
    {
        return originalModel;
    }

    public List<Extension> getBuildExtensions()
    {
        return Collections.emptyList();
    }

    @Override
    public Set<Artifact> createArtifacts( ArtifactFactory artifactFactory, String string, ArtifactFilter artifactFilter )
        throws InvalidDependencyVersionException
    {
        return Collections.emptySet();
    }

    public void addProjectReference( MavenProject mavenProject )
    {

    }

    public void attachArtifact( String string, String string1, File theFile )
    {

    }

    public Properties getProperties()
    {
        if ( properties == null )
        {
            properties = new Properties();
        }
        return properties;
    }

    public List<String> getFilters()
    {
        return Collections.singletonList( "" );
    }

    public Map<String, MavenProject> getProjectReferences()
    {
        return Collections.emptyMap();
    }

    public boolean isExecutionRoot()
    {
        return executionRoot;
    }

    public void setExecutionRoot( boolean b )
    {
        this.executionRoot = b;
    }

    public String getDefaultGoal()
    {
        return defaultGoal;
    }

    public Artifact replaceWithActiveArtifact( Artifact theArtifact )
    {
        return null;
    }
}
